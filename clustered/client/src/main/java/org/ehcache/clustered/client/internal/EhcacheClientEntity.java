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

package org.ehcache.clustered.client.internal;

import org.ehcache.CachePersistenceException;
import org.ehcache.clustered.client.config.TimeoutDuration;
import org.ehcache.clustered.common.ClusteredEhcacheIdentity;
import org.ehcache.clustered.common.ClusteredStoreValidationException;
import org.ehcache.clustered.common.ServerSideConfiguration;
import org.ehcache.clustered.common.ServerStoreConfiguration;
import org.ehcache.clustered.common.messages.EhcacheEntityMessage;
import org.ehcache.clustered.common.messages.LifeCycleMessageFactory;
import org.ehcache.clustered.common.messages.EhcacheEntityResponse;
import org.ehcache.clustered.common.messages.EhcacheEntityResponse.Failure;
import org.ehcache.clustered.common.messages.EhcacheEntityResponse.Type;
import org.ehcache.clustered.common.messages.LifecycleMessage;
import org.ehcache.clustered.common.messages.ServerStoreOpMessage.ServerStoreOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.connection.entity.Entity;
import org.terracotta.entity.EndpointDelegate;
import org.terracotta.entity.EntityClientEndpoint;
import org.terracotta.entity.EntityResponse;
import org.terracotta.entity.InvokeFuture;
import org.terracotta.entity.MessageCodecException;
import org.terracotta.exception.EntityException;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.ehcache.clustered.common.messages.ServerStoreOpMessage.ServerStoreOp.GET;
import static org.ehcache.clustered.common.messages.ServerStoreOpMessage.ServerStoreOp.getServerStoreOp;

/**
 * The client-side {@link Entity} through which clustered cache operations are performed.
 * An instance of this class is created by the {@link EhcacheClientEntityService}.
 * The server-side partner is the {@code EhcacheActiveEntity}.
 */
public class EhcacheClientEntity implements Entity {

  private static final Logger LOGGER = LoggerFactory.getLogger(EhcacheClientEntity.class);

  public interface ResponseListener<T extends EhcacheEntityResponse> {
    void onResponse(T response);
  }

  public interface DisconnectionListener {
    void onDisconnection();
  }

  private final EntityClientEndpoint<EhcacheEntityMessage, EhcacheEntityResponse> endpoint;
  private final LifeCycleMessageFactory messageFactory;
  private final Map<Class<? extends EhcacheEntityResponse>, List<ResponseListener<? extends EhcacheEntityResponse>>> responseListeners = new ConcurrentHashMap<Class<? extends EhcacheEntityResponse>, List<ResponseListener<? extends EhcacheEntityResponse>>>();
  private final List<DisconnectionListener> disconnectionListeners = new CopyOnWriteArrayList<DisconnectionListener>();
  private volatile boolean connected = true;

  private Timeouts timeouts = Timeouts.builder().build();

  public EhcacheClientEntity(EntityClientEndpoint<EhcacheEntityMessage, EhcacheEntityResponse> endpoint) {
    this.endpoint = endpoint;
    this.messageFactory = new LifeCycleMessageFactory();
    endpoint.setDelegate(new EndpointDelegate() {
      @Override
      public void handleMessage(EntityResponse messageFromServer) {
        if (messageFromServer instanceof EhcacheEntityResponse) {
          fireResponseEvent((EhcacheEntityResponse) messageFromServer);
        }
      }

      @Override
      public byte[] createExtendedReconnectData() {
        return new byte[0];
      }

      @Override
      public void didDisconnectUnexpectedly() {
        fireDisconnectionEvent();
      }
    });
  }

  void fireDisconnectionEvent() {
    connected = false;
    for (DisconnectionListener listener : disconnectionListeners) {
      listener.onDisconnection();
    }
  }

  void setConnected(boolean connected) {
    this.connected = connected;
  }

  Timeouts getTimeouts() {
    return timeouts;
  }

  void setTimeouts(Timeouts timeouts) {
    this.timeouts = timeouts;
  }

  private void fireResponseEvent(EhcacheEntityResponse response) {
    List<ResponseListener<? extends EhcacheEntityResponse>> responseListeners = this.responseListeners.get(response.getClass());
    if (responseListeners == null) {
      return;
    }
    LOGGER.debug("{} registered response listener(s) for {}", responseListeners.size(), response.getClass());
    for (ResponseListener responseListener : responseListeners) {
      responseListener.onResponse(response);
    }
  }

  public boolean isConnected() {
    return connected;
  }

  public void addDisconnectionListener(DisconnectionListener listener) {
    disconnectionListeners.add(listener);
  }

  public <T extends EhcacheEntityResponse> void addResponseListener(Class<T> responseType, ResponseListener<T> responseListener) {
    List<ResponseListener<? extends EhcacheEntityResponse>> responseListeners = this.responseListeners.get(responseType);
    if (responseListeners == null) {
      responseListeners = new CopyOnWriteArrayList<ResponseListener<? extends EhcacheEntityResponse>>();
      this.responseListeners.put(responseType, responseListeners);
    }
    responseListeners.add(responseListener);
  }

  public UUID identity() {
    return ClusteredEhcacheIdentity.deserialize(endpoint.getEntityConfiguration());
  }

  @Override
  public void close() {
    endpoint.close();
  }

  public void validate(ServerSideConfiguration config) throws IllegalArgumentException, TimeoutException {
    try {
      invokeInternal(timeouts.getLifecycleOperationTimeout(), messageFactory.validateStoreManager(config), false);
    } catch (TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw convert(e, IllegalArgumentException.class, ILLEGAL_ARGUMENT_EXCEPTION_CTOR);
    }
  }

  public void configure(ServerSideConfiguration config) throws IllegalStateException, TimeoutException {
    try {
      invokeInternal(timeouts.getLifecycleOperationTimeout(), messageFactory.configureStoreManager(config), false);
    } catch (TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw convert(e, IllegalStateException.class, ILLEGAL_STATE_EXCEPTION_CTOR);
    }
  }

  public void createCache(String name, ServerStoreConfiguration serverStoreConfiguration)
      throws CachePersistenceException, TimeoutException {
    try {
      invokeInternal(timeouts.getLifecycleOperationTimeout(), messageFactory.createServerStore(name, serverStoreConfiguration), false);
    } catch (TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw convert(e, CachePersistenceException.class, CACHE_PERSISTENCE_EXCEPTION_CTOR);
    }
  }

  public void validateCache(String name, ServerStoreConfiguration serverStoreConfiguration)
      throws ClusteredStoreValidationException, TimeoutException {
    try {
      invokeInternal(timeouts.getLifecycleOperationTimeout(), messageFactory.validateServerStore(name , serverStoreConfiguration), false);
    } catch (TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw convert(e, ClusteredStoreValidationException.class, CLUSTERED_STORE_VALIDATION_EXCEPTION_CTOR);
    }
  }

  public void releaseCache(String name) throws CachePersistenceException, TimeoutException {
    try {
      invokeInternal(timeouts.getLifecycleOperationTimeout(), messageFactory.releaseServerStore(name), false);
    } catch (TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw convert(e, CachePersistenceException.class, CACHE_PERSISTENCE_EXCEPTION_CTOR);
    }
  }

  public void destroyCache(String name) throws CachePersistenceException, TimeoutException {
    try {
      invokeInternal(timeouts.getLifecycleOperationTimeout(), messageFactory.destroyServerStore(name), false);
    } catch (TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw convert(e, CachePersistenceException.class, CACHE_PERSISTENCE_EXCEPTION_CTOR);
    }
  }

  /**
   * Sends a message to the {@code EhcacheActiveEntity} associated with this {@code EhcacheClientEntity} and
   * awaits a response.
   *
   * @param message the {@code EhcacheEntityMessage} to send
   * @param replicate if {@code true}, indicates that the message should be replicated to
   *                            passive servers before the response for {@code message} is returned
   *
   * @return an {@code EhcacheEntityResponse} holding a successful response from the server for {@code message}
   *
   * @throws EhcacheEntityOperationException thrown to reflect a server-side operation fault except for
   *          {@code IllegalArgumentException} and {@code IllegalStateException}
   * @throws IllegalArgumentException thrown to reflect an {@code IllegalArgumentException} from a server-side
   *          operation or if thrown from a message system support method
   * @throws IllegalStateException thrown to reflect an {@code IllegalStateException} from a server-side
   *          operation or if thrown from a message system support method
   * @throws RuntimeException thrown when a message system support method throws a {@code RuntimeException}
   *          or a checked exception
   * @throws TimeoutException if the server interactions take longer than the timeout configured for the operation
   */
  public EhcacheEntityResponse invoke(EhcacheEntityMessage message, boolean replicate)
      throws EhcacheEntityOperationException, IllegalArgumentException, IllegalStateException, TimeoutException {
    TimeoutDuration timeLimit;
    if (message.getType() == EhcacheEntityMessage.Type.SERVER_STORE_OP
        && GET_STORE_OPS.contains(getServerStoreOp(message.getOpCode()))) {
      timeLimit = timeouts.getGetOperationTimeout();
    } else {
      timeLimit = timeouts.getMutativeOperationTimeout();
    }
    try {
      return invokeInternal(timeLimit, message, replicate);
    } catch (TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw convert(e, EhcacheEntityOperationException.class, EHCACHE_ENTITY_OPERATION_EXCEPTION_CTOR);
    }
  }

  private static final Set<ServerStoreOp> GET_STORE_OPS = EnumSet.of(GET);

  private EhcacheEntityResponse invokeInternal(TimeoutDuration timeLimit, EhcacheEntityMessage message, boolean replicate)
      throws CachePersistenceException, MessageCodecException, EntityException, TimeoutException {

    InvokeFuture<EhcacheEntityResponse> result;
    if (replicate) {
      result = endpoint.beginInvoke().message(message).replicate(true).invoke();
    } else {
      result = endpoint.beginInvoke().message(message).invoke();
    }

    boolean interrupted = false;
    long deadlineTimeout = System.nanoTime() + timeLimit.toNanos();
    try {
      while (true) {
        try {
          long timeRemaining = deadlineTimeout - System.nanoTime();
          EhcacheEntityResponse response = result.getWithTimeout(timeRemaining, TimeUnit.NANOSECONDS);
          if (Type.FAILURE.equals(response.getType())) {
            /*
             * The FAILURE cause is a server-side exception lacking client-side stack trace
             * elements.  The server-side exception must be wrapped in a client-side exception
             * to provide proper stack trace for analysis.
             */
            Exception cause = ((Failure)response).getCause();
            throw new CachePersistenceException(message.getType() + " error: " + cause.toString(), cause);
          } else {
            return response;
          }
        } catch (InterruptedException e) {
          interrupted = true;
        } catch (TimeoutException e) {
          String msg = "Timeout exceeded for " + getMessageOp(message) + " message; " + timeLimit;
          TimeoutException timeoutException = new TimeoutException(msg);
          timeoutException.initCause(e);
          LOGGER.info(msg, timeoutException);
          throw timeoutException;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private String getMessageOp(EhcacheEntityMessage message) {
    switch (message.getType()) {
      case SERVER_STORE_OP:
        try {
          return message.getType() + "/" + getServerStoreOp(message.getOpCode());
        } catch (IllegalArgumentException e) {
          return message.getType() + "/" + message.getOpCode();
        }
      case LIFECYCLE_OP:
        try {
          return message.getType() + "/" + ((LifecycleMessage)message).operation();
        } catch (ArrayIndexOutOfBoundsException e) {
          return message.getType() + "/" + message.getOpCode();
         }
      default:
        return message.getType() + "/" + message.getOpCode();
    }
  }

  /**
   * Prepares an exception of the specified type from an exception thrown by the
   * {@link #invokeInternal(TimeoutDuration, EhcacheEntityMessage, boolean)} method.
   *
   * @param e the exception from {@code invoke}
   * @param targetException the desired exception type
   * @param targetExceptionBuilder a builder for {@code targetException}
   * @param <E> the desired exception type
   *
   * @return an exception of type {@code targetException}
   *
   * @throws IllegalArgumentException if {@code targetException} is not {@code IllegalArgumentException},
   *        {@code e} is a {@link CachePersistenceException}, and {@code e.getCause} is an
   *        {@code IllegalArgumentException}
   * @throws IllegalStateException if {@code targetException} is not {@code IllegalStateException},
   *        {@code e} is a {@link CachePersistenceException}, and {@code e.getCause} is an
   *        {@code IllegalStateException}
   * @throws RuntimeException if {@code e} is a {@code RuntimeException} and {@code e} is not of
   *        type {@code targetException}, or {@code e} is otherwise not a {@code RuntimeException}
   */
  private <E extends Exception> E convert(Exception e, Class<E> targetException, Ctor<E> targetExceptionBuilder) {
    if (targetException.isInstance(e)) {
      return targetException.cast(e);
    }

    if (e instanceof CachePersistenceException) {
      /*
       * An exception carrying a server-side exception returned from invoke (an EhcacheEntityResponse.Failure).
       * Since the CachePersistenceException isn't the tarrget exception, unwrap and re-throw/return as
       * befitting the wrapped exception.  If the CachePersistenceException cause is a recognized type,
       * the cause is wrapped in a client-side exception of the same type and either returned (if the target
       * exception type) or thrown.
       */
      Throwable cause = e.getCause();
      RuntimeException clientException;
      if (cause == null) {
        clientException = new RuntimeException(e);

      } else if (cause instanceof IllegalArgumentException) {
        clientException = new IllegalArgumentException(e.getMessage(), cause);

      } else if (cause instanceof IllegalStateException) {
        clientException = new IllegalStateException(e.getMessage(), cause);

      } else {
        /*
         * If not one of the recognized RuntimeExceptions above, wrap the cause in a new targetException and return.
         */
        return targetExceptionBuilder.create(e.getMessage(), cause);
      }

      if (targetException.isInstance(clientException)) {
        return targetException.cast(clientException);
      }
      throw clientException;

    } else if (e instanceof RuntimeException) {
      /*
       * An unchecked exception; re-throw.  This is an unexpected exception from invoke message handling.
       */
      throw (RuntimeException)e;

    } else {
      /*
       * A checked exception other than the targetException; re-throw as a RuntimeException.
       * This is an unexpected exception from invoke message handling.
       */
      throw new RuntimeException(e);
    }
  }

  private static final Ctor<ClusteredStoreValidationException> CLUSTERED_STORE_VALIDATION_EXCEPTION_CTOR =
      new Ctor<ClusteredStoreValidationException>() {
        @Override
        public ClusteredStoreValidationException create(String msg, Throwable cause) {
          return new ClusteredStoreValidationException(cause);
        }
      };
  private static final Ctor<CachePersistenceException> CACHE_PERSISTENCE_EXCEPTION_CTOR =
      new Ctor<CachePersistenceException>() {
        @Override
        public CachePersistenceException create(String msg, Throwable cause) {
          return new CachePersistenceException(msg, cause);
        }
      };
  private static final Ctor<EhcacheEntityOperationException> EHCACHE_ENTITY_OPERATION_EXCEPTION_CTOR =
      new Ctor<EhcacheEntityOperationException>() {
        @Override
        public EhcacheEntityOperationException create(String msg, Throwable cause) {
          return new EhcacheEntityOperationException(msg, cause);
        }
      };
  private static final Ctor<IllegalArgumentException> ILLEGAL_ARGUMENT_EXCEPTION_CTOR =
      new Ctor<IllegalArgumentException>() {
        @Override
        public IllegalArgumentException create(String msg, Throwable cause) {
          return new IllegalArgumentException(msg, cause);
        }
      };
  private static final Ctor<IllegalStateException> ILLEGAL_STATE_EXCEPTION_CTOR =
      new Ctor<IllegalStateException>() {
        @Override
        public IllegalStateException create(String msg, Throwable cause) {
          return new IllegalStateException(msg, cause);
        }
      };

  private interface Ctor<E extends Exception> {
    E create(String msg, Throwable cause);
  }

  /**
   * Describes the timeouts for {@link EhcacheClientEntity} operations.  Use
   * {@link #builder()} to construct an instance.
   */
  public static final class Timeouts {
    private final TimeoutDuration getOperationTimeout;
    private final TimeoutDuration mutativeOperationTimeout;
    private final TimeoutDuration lifecycleOperationTimeout;

    private Timeouts(TimeoutDuration getOperationTimeout, TimeoutDuration mutativeOperationTimeout, TimeoutDuration lifecycleOperationTimeout) {
      this.getOperationTimeout = getOperationTimeout;
      this.mutativeOperationTimeout = mutativeOperationTimeout;
      this.lifecycleOperationTimeout = lifecycleOperationTimeout;
    }

    public TimeoutDuration getGetOperationTimeout() {
      return getOperationTimeout;
    }

    public TimeoutDuration getMutativeOperationTimeout() {
      return mutativeOperationTimeout;
    }

    public TimeoutDuration getLifecycleOperationTimeout() {
      return lifecycleOperationTimeout;
    }

    public static Builder builder() {
      return new Builder();
    }

    @Override
    public String toString() {
      return "Timeouts{" +
          "getOperationTimeout=" + getOperationTimeout +
          ", mutativeOperationTimeout=" + mutativeOperationTimeout +
          ", lifecycleOperationTimeout=" + lifecycleOperationTimeout +
          '}';
    }

    /**
     * Constructs instances of {@link Timeouts}.  When obtained from
     * {@link Timeouts#builder()}, the default values are pre-set.
     */
    public static final class Builder {
      private TimeoutDuration getOperationalTimeout = TimeoutDuration.of(5, TimeUnit.SECONDS);
      private TimeoutDuration mutativeOperationTimeout = TimeoutDuration.of(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      private TimeoutDuration lifecycleOperationTimeout = TimeoutDuration.of(10, TimeUnit.SECONDS);

      /**
       * Sets the timeout for {@code get} operations.  The default value for this timeout is
       * 5 seconds.
       *
       * @param getOperationTimeout the {@code TimeoutDuration} to use for the {@code get} operation timeout
       *
       * @return this {@code Builder}
       */
      public Builder setGetOperationalTimeout(TimeoutDuration getOperationTimeout) {
        if (getOperationTimeout == null) {
          throw new NullPointerException("getOperationTimeout");
        }
        this.getOperationalTimeout = getOperationTimeout;
        return this;
      }

      /**
       * Sets the timeout for mutative operations like {@code put} and {@code remove}.  The default value
       * for this timeout is {@code Long.MAX_VALUE} nanoseconds.
       *
       * @param mutativeOperationTimeout the {@code TimeoutDuration} to use for a mutative operation timeout
       *
       * @return this {@code Builder}
       */
      public Builder setMutativeOperationTimeout(TimeoutDuration mutativeOperationTimeout) {
        if (mutativeOperationTimeout == null) {
          throw new NullPointerException("mutativeOperationTimeout");
        }
        this.mutativeOperationTimeout = mutativeOperationTimeout;
        return this;
      }

      /**
       * Sets the timeout for server store manager lifecycle operations like {@code validate} and {@code validateCache}.
       *
       * @param lifecycleOperationTimeout the {@code TimeoutDuration} to use for a store manager lifecycle operation timeout
       *
       * @return this {@code Builder}
       */
      public Builder setLifecycleOperationTimeout(TimeoutDuration lifecycleOperationTimeout) {
        if (lifecycleOperationTimeout == null) {
          throw new NullPointerException("lifecycleOperationTimeout");
        }
        this.lifecycleOperationTimeout = lifecycleOperationTimeout;
        return this;
      }

      /**
       * Gets a new {@link Timeouts} instance using the current timeout duration settings.
       *
       * @return a new {@code Timeouts} instance
       */
      public Timeouts build() {
        return new Timeouts(getOperationalTimeout, mutativeOperationTimeout, lifecycleOperationTimeout);
      }
    }
  }
}
