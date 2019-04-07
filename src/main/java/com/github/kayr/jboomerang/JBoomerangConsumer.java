package com.github.kayr.jboomerang;

@FunctionalInterface
public interface JBoomerangConsumer<T> {
    void accept(T t) throws Exception;//NOSONAR
}