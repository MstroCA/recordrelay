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
package io.recordrelay.engine.batch;

import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.port.out.RecordTransformer;
import java.util.Objects;
import org.springframework.batch.item.ItemProcessor;

/**
 * Spring Batch {@link ItemProcessor} adapter that delegates to a {@link RecordTransformer}.
 *
 * <p>Returning {@code null} from an {@code ItemProcessor} causes Spring Batch to filter the item;
 * this class never does so — filtering is the transformer's responsibility.
 */
public final class RecordRelayItemProcessor implements ItemProcessor<DataRecord, DataRecord> {

  private final RecordTransformer transformer;

  public RecordRelayItemProcessor(RecordTransformer transformer) {
    this.transformer = Objects.requireNonNull(transformer, "transformer");
  }

  @Override
  public DataRecord process(DataRecord item) throws Exception {
    return transformer.transform(item);
  }
}
