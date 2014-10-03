package org.drupal.project.computing.exception;

/**
 * Some item is not found.
 */
public class DNotFoundException extends Exception {
    public DNotFoundException() {}

    public DNotFoundException(String s) {
        super(s);
    }
}
