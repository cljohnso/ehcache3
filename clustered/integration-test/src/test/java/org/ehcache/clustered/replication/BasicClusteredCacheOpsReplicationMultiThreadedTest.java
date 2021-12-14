/*
 * Copyright Terracotta, Inc.
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

package org.ehcache.clustered.replication;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.PersistentCacheManager;
import org.ehcache.clustered.client.config.builders.ClusteredResourcePoolBuilder;
import org.ehcache.clustered.client.config.builders.ClusteredStoreConfigurationBuilder;
import org.ehcache.clustered.client.config.builders.ClusteringServiceConfigurationBuilder;
import org.ehcache.clustered.common.Consistency;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.terracotta.testing.rules.BasicExternalCluster;
import org.terracotta.testing.rules.Cluster;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * This test asserts Active-Passive fail-over with
 * multi-threaded/multi-client scenarios.
 * Note that fail-over is happening while client threads are still writing
 * Finally the same key set correctness is asserted.
 */
@RunWith(Parameterized.class)
public class BasicClusteredCacheOpsReplicationMultiThreadedTest {

  private static final int NUM_OF_THREADS = 10;
  private static final int JOB_SIZE = 100;
  private static final String RESOURCE_CONFIG =
      "<service xmlns:ohr='http://www.terracotta.org/config/offheap-resource' id=\"resources\">"
      + "<ohr:offheap-resources>"
      + "<ohr:resource name=\"primary-server-resource\" unit=\"MB\">16</ohr:resource>"
      + "</ohr:offheap-resources>" +
      "</service>\n";

  private static CacheManager CACHE_MANAGER1;
  private static CacheManager CACHE_MANAGER2;
  private static Cache<Long, BlobValue> CACHE1;
  private static Cache<Long, BlobValue> CACHE2;

  @Parameters(name = "consistency={0}")
  public static Consistency[] data() {
    return Consistency.values();
  }

  @Parameter
  public Consistency cacheConsistency;

  @ClassRule
  public static Cluster CLUSTER =
      new BasicExternalCluster(new File("build/cluster"), 2, Collections.emptyList(), "", RESOURCE_CONFIG, "");

  @Before
  public void startServers() throws Exception {
    CLUSTER.getClusterControl().waitForActive();
    CLUSTER.getClusterControl().waitForRunningPassivesInStandby();
    final CacheManagerBuilder<PersistentCacheManager> clusteredCacheManagerBuilder
        = CacheManagerBuilder.newCacheManagerBuilder()
        .with(ClusteringServiceConfigurationBuilder.cluster(CLUSTER.getConnectionURI().resolve("/crud-cm-replication"))
            .autoCreate()
            .defaultServerResource("primary-server-resource"));
    CACHE_MANAGER1 = clusteredCacheManagerBuilder.build(true);
    CACHE_MANAGER2 = clusteredCacheManagerBuilder.build(true);
    CacheConfiguration<Long, BlobValue> config = CacheConfigurationBuilder
        .newCacheConfigurationBuilder(Long.class, BlobValue.class,
            ResourcePoolsBuilder.newResourcePoolsBuilder().heap(500, EntryUnit.ENTRIES)
                .with(ClusteredResourcePoolBuilder.clusteredDedicated("primary-server-resource", 4, MemoryUnit.MB)))
        .add(ClusteredStoreConfigurationBuilder.withConsistency(cacheConsistency))
        .build();

    CACHE1 = CACHE_MANAGER1.createCache("clustered-cache", config);
    CACHE2 = CACHE_MANAGER2.createCache("clustered-cache", config);
  }

  @After
  public void tearDown() throws Exception {
    CACHE_MANAGER1.close();
    CACHE_MANAGER2.close();
    CLUSTER.getClusterControl().terminateActive();
    CLUSTER.getClusterControl().startAllServers();
  }

  @Test(timeout=180000)
  public void testCRUD() throws Exception {
    List<Cache<Long, BlobValue>> caches = new ArrayList<>();
    caches.add(CACHE1);
    caches.add(CACHE2);
    Random random = new Random();
    Set<Long> universalSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    ExecutorService executorService = Executors.newWorkStealingPool(NUM_OF_THREADS);

    List<Future> futures = new ArrayList<>();

    caches.forEach(cache -> {
      for (int i = 0; i < NUM_OF_THREADS; i++) {
        futures.add(executorService.submit(() -> random.longs().limit(JOB_SIZE).forEach(x -> {
          cache.put(x, new BlobValue());
          universalSet.add(x);
        })));
      }
    });

    //This step is to add values in local tier randomly to test invalidations happen correctly
    futures.add(executorService.submit(() -> universalSet.forEach(x -> {
      CACHE1.get(x);
      CACHE2.get(x);
    })));

    CLUSTER.getClusterControl().terminateActive();

    for (Future f : futures ) {
      f.get();
    }

    Set<Long> readKeysByCache1AfterFailOver = new HashSet<>();
    Set<Long> readKeysByCache2AfterFailOver = new HashSet<>();
    universalSet.forEach(x -> {
      if (CACHE1.get(x) != null) {
        readKeysByCache1AfterFailOver.add(x);
      }
      if (CACHE2.get(x) != null) {
        readKeysByCache2AfterFailOver.add(x);
      }
    });

    assertThat(readKeysByCache2AfterFailOver.size(), equalTo(readKeysByCache1AfterFailOver.size()));

    readKeysByCache2AfterFailOver.stream().forEach(y -> assertThat(readKeysByCache1AfterFailOver.contains(y), is(true)));

  }

  @Test(timeout=180000)
  public void testBulkOps() throws Exception {
    List<Cache<Long, BlobValue>> caches = new ArrayList<>();
    caches.add(CACHE1);
    caches.add(CACHE2);
    Random random = new Random();
    Set<Long> universalSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    ExecutorService executorService = Executors.newWorkStealingPool(NUM_OF_THREADS);

    List<Future> futures = new ArrayList<>();

    caches.forEach(cache -> {
      for (int i = 0; i < NUM_OF_THREADS; i++) {
        Map<Long, BlobValue> map = random.longs().limit(JOB_SIZE).collect(HashMap::new, (hashMap, x) -> hashMap.put(x, new BlobValue()), HashMap::putAll);
        futures.add(executorService.submit(() -> {
          cache.putAll(map);
          universalSet.addAll(map.keySet());
        }));
      }
    });

    //This step is to add values in local tier randomly to test invalidations happen correctly
    futures.add(executorService.submit(() -> {
      universalSet.forEach(x -> {
        CACHE1.get(x);
        CACHE2.get(x);
      });
    }));

    CLUSTER.getClusterControl().terminateActive();

    for (Future f : futures ) {
      f.get();
    }

    Set<Long> readKeysByCache1AfterFailOver = new HashSet<>();
    Set<Long> readKeysByCache2AfterFailOver = new HashSet<>();
    universalSet.forEach(x -> {
      if (CACHE1.get(x) != null) {
        readKeysByCache1AfterFailOver.add(x);
      }
      if (CACHE2.get(x) != null) {
        readKeysByCache2AfterFailOver.add(x);
      }
    });

    assertThat(readKeysByCache2AfterFailOver.size(), equalTo(readKeysByCache1AfterFailOver.size()));

    readKeysByCache2AfterFailOver.stream().forEach(y -> assertThat(readKeysByCache1AfterFailOver.contains(y), is(true)));

  }

  @Test(timeout=180000)
  public void testClear() throws Exception {
    List<Cache<Long, BlobValue>> caches = new ArrayList<>();
    caches.add(CACHE1);
    caches.add(CACHE2);
    Random random = new Random();
    Set<Long> universalSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    ExecutorService executorService = Executors.newWorkStealingPool(NUM_OF_THREADS);

    List<Future> futures = new ArrayList<>();

    caches.forEach(cache -> {
      for (int i = 0; i < NUM_OF_THREADS; i++) {
        Map<Long, BlobValue> map = random.longs().limit(JOB_SIZE).collect(HashMap::new, (hashMap, x) -> hashMap.put(x, new BlobValue()), HashMap::putAll);
        futures.add(executorService.submit(() -> {
          cache.putAll(map);
          universalSet.addAll(map.keySet());
        }));
      }
    });

    for (Future f : futures ) {
      f.get();
    }

    universalSet.forEach(x -> {
      CACHE1.get(x);
      CACHE2.get(x);
    });

    Future clearFuture = executorService.submit(() -> CACHE1.clear());

    CLUSTER.getClusterControl().terminateActive();

    clearFuture.get();

    universalSet.forEach(x -> assertThat(CACHE2.get(x), nullValue()));

  }

  private static class BlobValue implements Serializable {
    private final byte[] data = new byte[10 * 1024];
  }

}