/*
 * Copyright 2026 the RecordRelay authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.recordrelay.cli.command;

import io.recordrelay.cli.ExitCode;
import io.recordrelay.cli.RecordRelayCli;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.cli.output.ProgressBar;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TransactionIsolation;
import io.recordrelay.core.domain.TransferJob;
import io.recordrelay.core.domain.TransferMode;
import io.recordrelay.core.domain.TransferResult;
import io.recordrelay.core.engine.DefaultTransferEngine;
import io.recordrelay.core.engine.SyncPipeline;
import io.recordrelay.core.exception.MappingParseException;
import io.recordrelay.core.port.in.TransferUseCase;
import io.recordrelay.core.port.out.TransferProgressListener;
import io.recordrelay.core.spi.MappingParserRegistry;
import io.recordrelay.engine.batch.BatchPipelineAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** Executes a data transfer driven by a mapping file. */
@Command(name = "transfer", description = "Transfer data between databases using a mapping file.")
public final class TransferCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Option(
      names = {"--source", "-s"},
      required = true,
      description = "Source connection profile name")
  String source;

  @Option(
      names = {"--target", "-t"},
      required = true,
      description = "Target connection profile name")
  String target;

  @Option(
      names = {"--mapping", "-m"},
      required = true,
      description = "Path to the mapping file")
  Path mappingFile;

  @Option(names = "--mode", description = "Transfer mode: sync (default) or batch")
  String mode = "sync";

  @Option(names = "--chunk-size", description = "Records per batch chunk (default: 1000)")
  int chunkSize = TransferJob.DEFAULT_BATCH_SIZE;

  @Option(names = "--isolation", description = "Transaction isolation (default: READ_COMMITTED)")
  String isolation = "READ_COMMITTED";

  @Override
  public Integer call() {
    try {
      var printer = parent.printer();
      var store = parent.configStore();
      var resolver = new ConnProfileResolver(store);
      var srcProfile = resolver.resolve(source);
      var tgtProfile = resolver.resolve(target);
      var mapping = loadMapping(mappingFile);
      var transferMode = TransferMode.valueOf(mode.toUpperCase());
      var isolationLevel = TransactionIsolation.valueOf(isolation.toUpperCase());
      var job =
          new TransferJob(
              UUID.randomUUID().toString(),
              mapping.id() != null ? mapping.id() : "cli-transfer",
              srcProfile,
              tgtProfile,
              mapping,
              transferMode,
              chunkSize,
              isolationLevel,
              null);
      printer.printLine("Starting transfer: " + source + " → " + target);
      var progress = new ProgressBar();
      var engine = buildEngine(transferMode);
      var result = engine.transfer(job, buildListener(job, progress));
      return reportResult(result);
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.TRANSFER_FAILED);
    }
  }

  private MappingDefinition loadMapping(Path file) throws Exception {
    String content = Files.readString(file);
    String formatId = ValidateCommand.detectFormat(file.toString());
    var parser =
        MappingParserRegistry.find(formatId)
            .orElseThrow(
                () -> new MappingParseException("No parser for format: " + formatId, -1, -1));
    return parser.parse(content);
  }

  private TransferUseCase buildEngine(TransferMode transferMode) throws Exception {
    var sync = new SyncPipeline();
    if (transferMode == TransferMode.BATCH) {
      return new DefaultTransferEngine(sync, new BatchPipelineAdapter());
    }
    return new DefaultTransferEngine(sync);
  }

  private TransferProgressListener buildListener(TransferJob job, ProgressBar progress) {
    return new TransferProgressListener() {
      @Override
      public void onStart(TransferJob j) {}

      @Override
      public void onProgress(TransferJob j, long transferred, long total) {
        progress.update(transferred, total);
      }

      @Override
      public void onComplete(TransferResult result) {
        progress.complete(result.transferredCount());
      }

      @Override
      public void onError(TransferJob j, Exception e) {
        progress.clear();
      }
    };
  }

  private int reportResult(TransferResult result) throws Exception {
    if (result.isSuccessful()) {
      parent
          .printer()
          .printSuccess("Transfer complete: " + result.transferredCount() + " records.");
      return ExitCode.SUCCESS;
    }
    parent.printer().printError("Transfer failed: " + result.errors().size() + " error(s).");
    for (var err : result.errors()) {
      parent.printer().printLine("  - " + err.message());
    }
    return ExitCode.TRANSFER_FAILED;
  }
}
