/*
 * Copyright Terracotta, Inc.
 * Copyright IBM Corp. 2024, 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehcache.clustered.common.internal;

import org.ehcache.clustered.common.ServerSideConfiguration;

/**
 * ClusterTierManagerConfiguration
 */
public class ClusterTierManagerConfiguration {

  private final String identifier;
  private final ServerSideConfiguration configuration;

  public ClusterTierManagerConfiguration(String identifier, ServerSideConfiguration configuration) {
    this.identifier = identifier;
    this.configuration = configuration;
  }

  public ServerSideConfiguration getConfiguration() {
    return configuration;
  }

  public String getIdentifier() {
    return identifier;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClusterTierManagerConfiguration that = (ClusterTierManagerConfiguration) o;

    if (!identifier.equals(that.identifier)) {
      return false;
    }
    return configuration.equals(that.configuration);
  }

  @Override
  public int hashCode() {
    int result = identifier.hashCode();
    result = 31 * result + configuration.hashCode();
    return result;
  }
}
