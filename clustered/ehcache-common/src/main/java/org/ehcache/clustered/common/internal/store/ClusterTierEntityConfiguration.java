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

package org.ehcache.clustered.common.internal.store;

import org.ehcache.clustered.common.internal.ServerStoreConfiguration;

/**
 * ClusterTierEntityConfiguration
 */
public class ClusterTierEntityConfiguration {

  private final String managerIdentifier;
  private final String storeIdentifier;
  private final ServerStoreConfiguration configuration;

  public ClusterTierEntityConfiguration(String managerIdentifier, String storeIdentifier, ServerStoreConfiguration configuration) {
    this.managerIdentifier = managerIdentifier;
    this.storeIdentifier = storeIdentifier;
    this.configuration = configuration;
  }

  public String getManagerIdentifier() {
    return managerIdentifier;
  }

  public String getStoreIdentifier() {
    return storeIdentifier;
  }

  public ServerStoreConfiguration getConfiguration() {
    return configuration;
  }
}
