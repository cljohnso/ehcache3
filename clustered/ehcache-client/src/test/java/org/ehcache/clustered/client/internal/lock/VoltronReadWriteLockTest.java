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

package org.ehcache.clustered.client.internal.lock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.terracotta.connection.Connection;
import org.terracotta.connection.entity.EntityRef;

import static org.ehcache.clustered.common.internal.lock.LockMessaging.HoldType.READ;
import static org.ehcache.clustered.common.internal.lock.LockMessaging.HoldType.WRITE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class VoltronReadWriteLockTest {

  @Mock
  private VoltronReadWriteLockClient client;

  @Mock
  private EntityRef<VoltronReadWriteLockClient, Void, Void> entityRef;

  @Mock
  private Connection connection;

  @Test
  public void testCreateLockEntityWhenNotExisting() throws Exception {
    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.readLock();

    verify(entityRef).create(isNull());
  }

  @Test
  public void testFetchExistingLockEntityWhenExists() throws Exception {
    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.readLock();

    verify(entityRef, never()).create(any(Void.class));
  }

  @Test
  public void testWriteLockLocksWrite() throws Exception {
    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.writeLock();

    verify(client).lock(WRITE);
  }

  @Test
  public void testReadLockLocksRead() throws Exception {
    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.readLock();

    verify(client).lock(READ);
  }

  @Test
  public void testWriteUnlockUnlocksWrite() throws Exception {
    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.writeLock().unlock();

    verify(client).unlock(WRITE);
  }

  @Test
  public void testReadUnlockUnlocksRead() throws Exception {
    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.readLock().unlock();

    verify(client).unlock(READ);
  }

  @Test
  public void testWriteUnlockClosesEntity() throws Exception {
    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.writeLock().unlock();

    verify(client).close();
  }

  @Test
  public void testReadUnlockClosesEntity() throws Exception {
    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.readLock().unlock();

    verify(client).close();
  }

  @Test
  public void testWriteUnlockDestroysEntity() throws Exception {
    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.writeLock().unlock();

    verify(entityRef).destroy();
  }

  @Test
  public void testReadUnlockDestroysEntity() throws Exception {
    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.readLock().unlock();

    verify(entityRef).destroy();
  }

  @Test
  public void testTryWriteLockTryLocksWrite() throws Exception {
    when(client.tryLock(WRITE)).thenReturn(true);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    assertThat(lock.tryWriteLock(), notNullValue());
    verify(client).tryLock(WRITE);
  }

  @Test
  public void testTryReadLockTryLocksRead() throws Exception {
    when(client.tryLock(READ)).thenReturn(true);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    assertThat(lock.tryReadLock(), notNullValue());
    verify(client).tryLock(READ);
  }

  @Test
  public void testTryWriteUnlockUnlocksWrite() throws Exception {
    when(client.tryLock(WRITE)).thenReturn(true);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.tryWriteLock().unlock();

    verify(client).unlock(WRITE);
  }

  @Test
  public void testTryReadUnlockUnlocksRead() throws Exception {
    when(client.tryLock(READ)).thenReturn(true);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.tryReadLock().unlock();

    verify(client).unlock(READ);
  }

  @Test
  public void testTryWriteUnlockClosesEntity() throws Exception {
    when(client.tryLock(WRITE)).thenReturn(true);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.tryWriteLock().unlock();

    verify(client).close();
  }

  @Test
  public void testTryReadUnlockClosesEntity() throws Exception {
    when(client.tryLock(READ)).thenReturn(true);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.tryReadLock().unlock();

    verify(client).close();
  }

  @Test
  public void testTryWriteUnlockDestroysEntity() throws Exception {
    when(client.tryLock(WRITE)).thenReturn(true);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.tryWriteLock().unlock();

    verify(entityRef).destroy();
  }

  @Test
  public void testTryReadUnlockDestroysEntity() throws Exception {
    when(client.tryLock(READ)).thenReturn(true);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    lock.tryReadLock().unlock();

    verify(entityRef).destroy();
  }

  @Test
  public void testTryWriteLockFailingClosesEntity() throws Exception {
    when(client.tryLock(WRITE)).thenReturn(false);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    assertThat(lock.tryWriteLock(), nullValue());
    verify(client).close();
  }

  @Test
  public void testTryReadLockFailingClosesEntity() throws Exception {
    when(client.tryLock(READ)).thenReturn(false);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    assertThat(lock.tryReadLock(), nullValue());
    verify(client).close();
  }

  @Test
  public void testTryWriteLockFailingDestroysEntity() throws Exception {
    when(client.tryLock(WRITE)).thenReturn(false);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    assertThat(lock.tryWriteLock(), nullValue());
    verify(entityRef).destroy();
  }

  @Test
  public void testTryReadLockFailingDestroysEntity() throws Exception {
    when(client.tryLock(READ)).thenReturn(false);

    when(entityRef.fetchEntity(null)).thenReturn(client);

    when(connection.<VoltronReadWriteLockClient, Void, Void>getEntityRef(VoltronReadWriteLockClient.class, 1, "VoltronReadWriteLock-TestLock")).thenReturn(entityRef);
    VoltronReadWriteLock lock = new VoltronReadWriteLock(connection, "TestLock");

    assertThat(lock.tryReadLock(), nullValue());
    verify(entityRef).destroy();
  }
}
