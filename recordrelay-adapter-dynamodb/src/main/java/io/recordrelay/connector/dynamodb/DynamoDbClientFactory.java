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
package io.recordrelay.connector.dynamodb;

import io.recordrelay.core.domain.ConnectionProfile;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/** Builds a {@link DynamoDbClient} from a {@link ConnectionProfile}. */
final class DynamoDbClientFactory {

  private DynamoDbClientFactory() {}

  static DynamoDbClient create(ConnectionProfile profile) {
    DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(profile.host()));

    String keyId = profile.credentials().username();
    String secretKey = profile.credentials().password();
    if (!keyId.isBlank() && !secretKey.isBlank()) {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId, secretKey)));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }

    String endpoint = profile.properties().get("endpoint");
    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint));
    }

    return builder.build();
  }
}
