package org.drupal.project.computing;

/**
 * Expected failure from executing a DCommand.
 * This is different from any unexpected DRuntimeException.
 */
public class DCommandExecutionException extends Exception {
    public DCommandExecutionException() {
        super();
    }

    public DCommandExecutionException(String s) {
        super(s);
    }

    public DCommandExecutionException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public DCommandExecutionException(Throwable throwable) {
        super(throwable);
    }
}
