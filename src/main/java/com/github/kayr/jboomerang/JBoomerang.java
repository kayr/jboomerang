package com.github.kayr.jboomerang;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class JBoomerang<R> {

    public static final Object COMMON_DISCRIMINATOR = new Object();


    public enum Propagation {WITH_NEW, JOIN}

    private static final Logger LOG = LoggerFactory.getLogger(JBoomerang.class);
    private ThreadLocal<Map<Object, Deque<ResourceHolder<R>>>> resourceStack = ThreadLocal.withInitial(HashMap::new);
    private ResourceFactory<R> resourceFactory;

    public JBoomerang(ResourceFactory<R> resourceFactory) {
        this.resourceFactory = resourceFactory;
    }


    public void consume(JBoomerangConsumer<R> fx) {
        consume(Propagation.JOIN, fx);
    }

    public <V> V withResource(JBoomerangFunction<R, V> fx) {
        return withResource(Propagation.JOIN, fx);

    }

    public void consume(Propagation propagation, JBoomerangConsumer<R> fx) {
        withResource(propagation, r -> {
            fx.accept(r);
            return Void.TYPE;
        });
    }

    public <V> V withResource(Propagation propagation, JBoomerangFunction<R, V> fx) {
        return withResource(COMMON_DISCRIMINATOR, propagation, Args.none(), fx);
    }

    public <V> V withResource(Object discriminator, Propagation propagation, Args args, JBoomerangFunction<R, V> fx) {

        ResourceHolder<R> resource = null;
        boolean attemptedClose = false;

        Deque<ResourceHolder<R>> currentDeque = getCurrentDeque(discriminator);

        try {
            resource = getResource(discriminator, propagation, currentDeque, args);
            LOG.trace("-------!!! Providing resource..{} Calls:[{}]  !!!-------", resourceFactory, resource.count);

            V result = fx.apply(resource.incrementAndGet());
            attemptedClose = true;
            closeResourceExplosively(discriminator, resource, currentDeque);

            return result;
        } catch (Throwable x) {//NOSONAR

            if (!attemptedClose) {
                //if did not attempt close then this was an error of resource usage.
                //hence close the resource
                LOG.error("!!!! Error providing resource..{}", resourceFactory);
                LOG.trace("Error while providing/using resource: {}" + resourceFactory, x);

                Optional.ofNullable(resource)
                        .ifPresent(t -> resourceFactory.onException(discriminator, t.getResource()));

                closeResourceSilently(discriminator, resource, currentDeque);

            }
            ExceptionUtil.sneakyThrow(x);
        }
        return null;
    }




    private void closeResourceSilently(Object discriminator, ResourceHolder<R> resource, Deque<ResourceHolder<R>> currentDeque) {

        try {
            closeResource(discriminator, resource, currentDeque);
        } catch (BoomerangCloseException x) {
            LOG.warn("Error closing resource", x);
        }

    }

    private void closeResourceExplosively(Object discriminator, ResourceHolder<R> resource, Deque<ResourceHolder<R>> currentDeque) {
        closeResource(discriminator, resource, currentDeque);
    }

    private void closeResource(Object discriminator, ResourceHolder<R> resource, Deque<ResourceHolder<R>> currentDeque) {
        if (resource != null) {
            try {
                resource.decrement();
                if (resource.isComplete()) {
                    LOG.trace("--->Closing resource..{}: Calls[{}]", resourceFactory, resource.count);
                    resourceFactory.close(discriminator, resource.getResource());
                }
            } catch (BoomerangCloseException x) {
                throw x;
            } catch (Exception x) {
                LOG.warn("Error closing resource", x);
            } finally {
                if (resource.isComplete()) {
                    currentDeque.poll();
                }
            }
        }
    }

    private ResourceHolder<R> getResource(Object discriminator, Propagation propagation, Deque<ResourceHolder<R>> currentDeque, Args args) {
        if (currentDeque.isEmpty()) {
            return createResource(discriminator, currentDeque, args);
        }

        if (propagation == Propagation.WITH_NEW) {
            return createResource(discriminator, currentDeque, args);
        } else if (propagation == Propagation.JOIN) {
            return currentDeque.peek();
        }

        throw new UnsupportedOperationException("Propagation not supported: " + propagation);

    }


    private Deque<ResourceHolder<R>> getCurrentDeque(Object discriminator) {
        Map<Object, Deque<ResourceHolder<R>>> resourceHoldersMap = resourceStack.get();
        return resourceHoldersMap.computeIfAbsent(discriminator, k -> new ArrayDeque<>());
    }


    private ResourceHolder<R> createResource(Object discriminator, Deque<ResourceHolder<R>> currentDeque, Args args) {
        LOG.trace("-------->Creating resource: {}", resourceFactory);
        R resource = resourceFactory.create(discriminator, args);
        if (resource == null) {
            throw new NullPointerException("Unexpected null returned for resource");
        }
        ResourceHolder<R> e = new ResourceHolder<>(resource);

        currentDeque.push(e);
        return e;
    }

    public int getOpenResources() {
        return getOpenResources(COMMON_DISCRIMINATOR);
    }

    public int getOpenResources(Object discriminator) {
        return resourceStack.get().get(discriminator).size();
    }

    public Optional<R> getCurrentResource() {
        return getCurrentResource(COMMON_DISCRIMINATOR);
    }

    public Optional<R> getCurrentResource(Object discriminator) {
        ResourceHolder<R> peek = getCurrentDeque(discriminator).peek();
        if (peek != null)
            return Optional.ofNullable(peek.resource);
        return Optional.empty();
    }


    public interface ResourceFactory<R> {

        R create(Object discriminator, Args args);

        void close(Object discriminator, R resource);

        void onException(Object discriminator, R resource);
    }

    public static class Args {
        private static Args none = new Args(new Object[]{});
        private Object[] params;

        Args(Object[] params) {
            this.params = params;
        }

        static Args of(Object... args) {
            return new Args(args);
        }

        static Args none() {
            return none;
        }

        <T> T get(int i) {
            return (T) params[i];
        }
    }


    static class ResourceHolder<T> {

        private T resource;
        private int count = 0;


        ResourceHolder(T resource) {
            this.resource = resource;
        }


        private void increment() {
            count++;
        }

        T incrementAndGet() {
            increment();
            return resource;
        }

        public T getResource() {
            return resource;
        }

        void decrement() {
            count--;
        }

        private boolean isComplete() {
            return count <= 0;
        }

        int getCount() {
            return count;
        }
    }

}
