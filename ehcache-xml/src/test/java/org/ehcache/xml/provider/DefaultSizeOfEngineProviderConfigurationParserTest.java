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

package org.ehcache.xml.provider;

import org.ehcache.config.Configuration;
import org.ehcache.config.builders.ConfigurationBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.spi.service.ServiceCreationConfiguration;
import org.ehcache.xml.XmlConfiguration;
import org.ehcache.xml.model.ConfigType;
import org.ehcache.xml.model.SizeofType;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Deprecated
public class DefaultSizeOfEngineProviderConfigurationParserTest {

  @Test
  public void parseServiceCreationConfiguration() {
    Configuration xmlConfig = new XmlConfiguration(getClass().getResource("/configs/sizeof-engine.xml"));

    assertThat(xmlConfig.getServiceCreationConfigurations()).hasSize(1);


    ServiceCreationConfiguration<?, ?> configuration = xmlConfig.getServiceCreationConfigurations().iterator().next();
    assertThat(configuration).isExactlyInstanceOf(org.ehcache.impl.config.store.heap.DefaultSizeOfEngineProviderConfiguration.class);

    org.ehcache.impl.config.store.heap.DefaultSizeOfEngineProviderConfiguration sizeOfEngineProviderConfig = (org.ehcache.impl.config.store.heap.DefaultSizeOfEngineProviderConfiguration) configuration;
    assertThat(sizeOfEngineProviderConfig.getMaxObjectGraphSize()).isEqualTo(200);
    assertThat(sizeOfEngineProviderConfig.getMaxObjectSize()).isEqualTo(100000);
  }

  @Test
  public void unparseServiceCreationConfiguration() {
    ConfigType configType = new ConfigType();
    Configuration config = ConfigurationBuilder.newConfigurationBuilder()
      .withService(new org.ehcache.impl.config.store.heap.DefaultSizeOfEngineProviderConfiguration(123, MemoryUnit.MB, 987)).build();
    configType = new DefaultSizeOfEngineProviderConfigurationParser().unparseServiceCreationConfiguration(config, configType);

    SizeofType heapStore = configType.getHeapStore();
    assertThat(heapStore.getMaxObjectGraphSize().getValue()).isEqualTo(987);
    assertThat(heapStore.getMaxObjectSize().getValue()).isEqualTo(123);
    assertThat(heapStore.getMaxObjectSize().getUnit().value()).isEqualTo("MB");
  }
}
