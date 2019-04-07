package com.github.kayr.jboomerang;


@FunctionalInterface
public interface JBoomerangFunction<T, R>{
    R apply(T t) throws Exception;//NOSONAR

}
