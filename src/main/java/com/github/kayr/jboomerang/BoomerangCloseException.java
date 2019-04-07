package com.github.kayr.jboomerang;

/**
 * This exception will be thrown in case the resource factory fails to close a resource
 */
public class BoomerangCloseException extends RuntimeException {

    private final transient  JBoomerang.ResourceFactory factory;


    public BoomerangCloseException(String message) {
        super(message);
        this.factory = null;
    }

    public BoomerangCloseException(String message, Throwable x, JBoomerang.ResourceFactory resourceFactory) {
        super(message, x);
        this.factory = resourceFactory;
    }

    public JBoomerang.ResourceFactory getFactory() {
        return factory;
    }

}
