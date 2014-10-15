package org.drupal.project.computing.exception;

/**
 * This exception is thrown when there's something wrong communicating with the Drupal site (DSite)
 * Caller is expected to catch this exception and handles it properly.
 */
public class DSiteException extends Exception {
    public DSiteException() {
        super();
    }

    public DSiteException(String s) {
        super(s);
    }

    public DSiteException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public DSiteException(Throwable throwable) {
        super(throwable);
    }

    /**
     * The error code is arbitrary by the caller which sets it.
     */
    private int errorCode = 0;

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

}
