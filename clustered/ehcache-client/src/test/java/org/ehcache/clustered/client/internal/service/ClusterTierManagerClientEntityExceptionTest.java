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

package org.ehcache.clustered.client.internal.service;

import org.ehcache.CachePersistenceException;
import org.ehcache.clustered.client.config.ClusteringServiceConfiguration;
import org.ehcache.clustered.client.config.builders.ClusteredResourcePoolBuilder;
import org.ehcache.clustered.client.config.builders.ClusteringServiceConfigurationBuilder;
import org.ehcache.clustered.client.internal.ClusterTierManagerValidationException;
import org.ehcache.clustered.client.internal.SimpleClusterTierManagerClientEntity;
import org.ehcache.clustered.client.internal.UnitTestConnectionService;
import org.ehcache.clustered.client.service.ClusteringService;
import org.ehcache.clustered.common.internal.exceptions.ClusterException;
import org.ehcache.clustered.common.internal.exceptions.InvalidServerSideConfigurationException;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.core.config.store.StoreEventSourceConfiguration;
import org.ehcache.core.spi.store.Store;
import org.ehcache.core.store.StoreConfigurationImpl;
import org.ehcache.impl.internal.spi.serialization.DefaultSerializationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

/**
 * This class includes tests to ensure server-side exceptions returned as responses to
 * {@link SimpleClusterTierManagerClientEntity} messages are wrapped before being re-thrown.  This class
 * relies on {@link DefaultClusteringService} to set up conditions for the test and
 * is placed accordingly.
 */
public class ClusterTierManagerClientEntityExceptionTest {
  private static final String CLUSTER_URI_BASE = "terracotta://example.com:9540/";

  @Before
  public void definePassThroughServer() throws Exception {
    UnitTestConnectionService.add(CLUSTER_URI_BASE,
        new UnitTestConnectionService.PassthroughServerBuilder()
            .resource("defaultResource", 128, MemoryUnit.MB)
            .resource("serverResource1", 32, MemoryUnit.MB)
            .resource("serverResource2", 32, MemoryUnit.MB)
            .build());
  }

  @After
  public void removePassThroughServer() throws Exception {
    UnitTestConnectionService.remove(CLUSTER_URI_BASE);
  }

  /**
   * Tests to ensure that a {@link ClusterException ClusterException}
   * originating in the server is properly wrapped on the client before being re-thrown.
   */
  @Test
  public void testServerExceptionPassThrough() throws Exception {
    ClusteringServiceConfiguration creationConfig =
        ClusteringServiceConfigurationBuilder.cluster(URI.create(CLUSTER_URI_BASE + "my-application"))
          .autoCreate(server -> server
            .defaultServerResource("defaultResource")
            .resourcePool("sharedPrimary", 2, MemoryUnit.MB, "serverResource1")
            .resourcePool("sharedSecondary", 2, MemoryUnit.MB, "serverResource2")
            .resourcePool("sharedTertiary", 4, MemoryUnit.MB))
          .build();
    DefaultClusteringService creationService = new DefaultClusteringService(creationConfig);
    creationService.start(null);
    creationService.stop();

    ClusteringServiceConfiguration accessConfig =
        ClusteringServiceConfigurationBuilder.cluster(URI.create(CLUSTER_URI_BASE + "my-application"))
          .expecting(server -> server
            .defaultServerResource("different"))
          .build();
    DefaultClusteringService accessService = new DefaultClusteringService(accessConfig);
    /*
     * Induce an "InvalidStoreException: cluster tier 'cacheAlias' does not exist" on the server.
     */
    try {
      accessService.start(null);

      fail("Expecting ClusterTierManagerValidationException");
    } catch (RuntimeException e) {
      assertThat(e.getCause(), is(instanceOf(ClusterTierManagerValidationException.class)));

      Throwable clientSide = e.getCause().getCause();
      assertThat(clientSide, is(instanceOf(InvalidServerSideConfigurationException.class)));

      Throwable serverSide = clientSide.getCause();
      assertThat(serverSide, is(instanceOf(InvalidServerSideConfigurationException.class)));
    } finally {
      accessService.stop();
    }
  }

  private <K, V> Store.Configuration<K, V> getDedicatedStoreConfig(
      String targetResource, DefaultSerializationProvider serializationProvider, Class<K> keyType, Class<V> valueType)
      throws org.ehcache.spi.serialization.UnsupportedTypeException {
    return new StoreConfigurationImpl<>(
      CacheConfigurationBuilder.newCacheConfigurationBuilder(keyType, valueType,
        ResourcePoolsBuilder.newResourcePoolsBuilder()
          .with(ClusteredResourcePoolBuilder.clusteredDedicated(targetResource, 8, MemoryUnit.MB)))
        .build(),
      StoreEventSourceConfiguration.DEFAULT_DISPATCHER_CONCURRENCY,
      serializationProvider.createKeySerializer(keyType, getClass().getClassLoader()),
      serializationProvider.createValueSerializer(valueType, getClass().getClassLoader()));
  }

  private ClusteringService.ClusteredCacheIdentifier getClusteredCacheIdentifier(
      DefaultClusteringService service, String cacheAlias)
      throws CachePersistenceException {

    ClusteringService.ClusteredCacheIdentifier clusteredCacheIdentifier = (ClusteringService.ClusteredCacheIdentifier) service.getPersistenceSpaceIdentifier(cacheAlias, null);
    if (clusteredCacheIdentifier != null) {
      return clusteredCacheIdentifier;
    }
    throw new AssertionError("ClusteredCacheIdentifier not available for configuration");
  }
}
