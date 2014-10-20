package org.drupal.project.computing.v1;

/**
 * This is the default runtime exception for Drupal Hybrid Computing
 */
public class DRuntimeException extends RuntimeException {

    public DRuntimeException() {
        super();
    }

    public DRuntimeException(String s) {
        super(s);
    }

    public DRuntimeException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public DRuntimeException(Throwable throwable) {
        super(throwable);
    }
}
