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
package org.ehcache.docs;

import org.ehcache.config.Configuration;
import org.ehcache.config.FluentConfigurationBuilder;
import org.ehcache.config.ResourceType;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.core.internal.resilience.ThrowingResilienceStrategy;
import org.ehcache.core.spi.service.ServiceUtils;
import org.ehcache.core.util.ClassLoading;
import org.ehcache.impl.config.executor.PooledExecutionServiceConfiguration;
import org.ehcache.impl.config.persistence.DefaultPersistenceConfiguration;
import org.ehcache.impl.config.resilience.DefaultResilienceStrategyConfiguration;
import org.ehcache.spi.serialization.Serializer;
import org.ehcache.spi.serialization.SerializerException;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

public class ConfigurationDerivation {

  @Test
  public void identityTransform() {
    Configuration configuration = ConfigurationBuilder.newConfigurationBuilder()
      .withCache("cache", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10))
        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(10))))
      .build();

    // tag::deriveContract[]
    FluentConfigurationBuilder<?> derivedBuilder = configuration.derive(); // <1>
    Configuration configurationCopy = derivedBuilder.build(); // <2>
    // end::deriveContract[]
  }

  @Test
  public void withCustomClassLoader() {
    Configuration configuration = ConfigurationBuilder.newConfigurationBuilder()
      .withCache("cache", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10)))
      .build();

    ClassLoader classLoader = Mockito.mock(ClassLoader.class);

    // tag::customClassLoader[]
    Configuration withClassLoader = configuration.derive()
      .withClassLoader(classLoader)
      .build();
    // end::customClassLoader[]

    assertThat(configuration.getClassLoader(), is(sameInstance(ClassLoading.getDefaultClassLoader())));
    assertThat(withClassLoader.getClassLoader(), is(sameInstance(classLoader)));
  }

  @Test
  public void withCache() {
    Configuration configuration = ConfigurationBuilder.newConfigurationBuilder().build();

    //tag::withCache[]
    Configuration withCache = configuration.derive()
      .withCache("cache", CacheConfigurationBuilder.newCacheConfigurationBuilder(
        Long.class, String.class, ResourcePoolsBuilder.heap(10)))
      .build();
    //end::withCache[]

    assertThat(configuration.getCacheConfigurations().keySet(), is(empty()));
    assertThat(withCache.getCacheConfigurations().keySet(), containsInAnyOrder("cache"));
  }

  @Test
  public void withoutCache() {
    Configuration configuration = ConfigurationBuilder.newConfigurationBuilder()
      .withCache("cache", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10)))
      .build();

    //tag::withoutCache[]
    Configuration withoutCache = configuration.derive()
      .withoutCache("cache")
      .build();
    //end::withoutCache[]

    assertThat(configuration.getCacheConfigurations().keySet(), containsInAnyOrder("cache"));
    assertThat(withoutCache.getCacheConfigurations().keySet(), is(empty()));
  }

  @Test
  public void updateCache() {
    Configuration configuration = ConfigurationBuilder.newConfigurationBuilder()
      .withCache("cache", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10)))
      .build();

    //tag::updateCache[]
    Configuration withOffHeap = configuration.derive()
      .updateCache("cache", cache -> cache.updateResourcePools(
        resources -> ResourcePoolsBuilder.newResourcePoolsBuilder(resources)
          .offheap(100, MemoryUnit.MB)
          .build()))
      .build();
    //end::updateCache[]

    assertThat(configuration.getCacheConfigurations().get("cache").getResourcePools().getResourceTypeSet(), containsInAnyOrder(ResourceType.Core.HEAP));
    assertThat(withOffHeap.getCacheConfigurations().get("cache").getResourcePools().getResourceTypeSet(), containsInAnyOrder(ResourceType.Core.HEAP, ResourceType.Core.OFFHEAP));
  }

  @Test
  public void withServiceCreation() {
    Configuration configuration = ConfigurationBuilder.newConfigurationBuilder()
      .withCache("cache", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10)))
      .build();

    //tag::withServiceCreation[]
    Configuration withBoundedThreads = configuration.derive()
      .withService(new PooledExecutionServiceConfiguration()
        .addDefaultPool("default", 1, 16))
      .build();
    //end::withServiceCreation[]

    assertThat(configuration.getServiceCreationConfigurations(), not(hasItem(instanceOf(PooledExecutionServiceConfiguration.class))));
    PooledExecutionServiceConfiguration serviceCreationConfiguration = ServiceUtils.findSingletonAmongst(PooledExecutionServiceConfiguration.class, withBoundedThreads.getServiceCreationConfigurations());
    assertThat(serviceCreationConfiguration.getDefaultPoolAlias(), is("default"));
    assertThat(serviceCreationConfiguration.getPoolConfigurations().keySet(), containsInAnyOrder("default"));
    PooledExecutionServiceConfiguration.PoolConfiguration pool = serviceCreationConfiguration.getPoolConfigurations().get("default");
    assertThat(pool.minSize(), is(1));
    assertThat(pool.maxSize(), is(16));
  }

  @Test
  public void updateServiceCreation() {
    @SuppressWarnings("unchecked")
    Configuration configuration = ConfigurationBuilder.newConfigurationBuilder()
      .withService(new DefaultPersistenceConfiguration(new File("temp")))
      .withCache("cache", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10)))
      .build();

    //tag::updateServiceCreation[]
    Configuration withUpdatedPersistence = configuration.derive()
      .updateServices(DefaultPersistenceConfiguration.class,
        existing -> new File("/var/persistence/path"))
      .build();
    //end::updateServiceCreation[]

    DefaultPersistenceConfiguration initialPersistenceConfiguration = ServiceUtils.findSingletonAmongst(DefaultPersistenceConfiguration.class, configuration.getServiceCreationConfigurations());
    assertThat(initialPersistenceConfiguration.getRootDirectory(), is(new File("temp")));

    DefaultPersistenceConfiguration revisedPersistenceConfiguration = ServiceUtils.findSingletonAmongst(DefaultPersistenceConfiguration.class, withUpdatedPersistence.getServiceCreationConfigurations());
    assertThat(revisedPersistenceConfiguration.getRootDirectory(), is(new File("/var/persistence/path")));
  }

  @Test
  public void withService() {
    Configuration configuration = ConfigurationBuilder.newConfigurationBuilder()
      .withCache("cache", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10)))
      .build();

    //tag::withService[]
    Configuration withThrowingStrategy = configuration.derive()
      .updateCache("cache", existing -> existing.withService(
        new DefaultResilienceStrategyConfiguration(new ThrowingResilienceStrategy<>())
      ))
      .build();
    //end::withService[]


    assertThat(configuration.getServiceCreationConfigurations(), not(hasItem(
      instanceOf(DefaultResilienceStrategyConfiguration.class))));

    DefaultResilienceStrategyConfiguration resilienceStrategyConfiguration =
      ServiceUtils.findSingletonAmongst(DefaultResilienceStrategyConfiguration.class,
        withThrowingStrategy.getCacheConfigurations().get("cache").getServiceConfigurations());
    assertThat(resilienceStrategyConfiguration.getInstance(), instanceOf(ThrowingResilienceStrategy.class));
  }

  public static final class OptimizedDateSerializer implements Serializer<Date> {

    public OptimizedDateSerializer(ClassLoader classLoader) {}

    @Override
    public ByteBuffer serialize(Date object) throws SerializerException {
      ByteBuffer buffer = ByteBuffer.allocate(8);
      return (ByteBuffer) buffer.putLong(object.getTime()).flip();
    }

    @Override
    public Date read(ByteBuffer binary) throws ClassNotFoundException, SerializerException {
      return new Date(binary.getLong());
    }

    @Override
    public boolean equals(Date object, ByteBuffer binary) throws ClassNotFoundException, SerializerException {
      return binary.getLong() == object.getTime();
    }
  }
}
