package org.drupal.project.computing.exception;

/**
 * Throws when there's a problem executing shell command.
 */
public class DSystemExecutionException extends Exception {
    public DSystemExecutionException() {
    }

    public DSystemExecutionException(String s) {
        super(s);
    }

    public DSystemExecutionException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public DSystemExecutionException(Throwable throwable) {
        super(throwable);
    }
}
