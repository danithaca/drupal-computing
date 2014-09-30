package org.drupal.project.computing;

/**
 * Throws when there's configuration exception.
 */
public class DConfigException extends Exception {
    public DConfigException() {
    }

    public DConfigException(String s) {
        super(s);
    }

    public DConfigException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public DConfigException(Throwable throwable) {
        super(throwable);
    }
}
