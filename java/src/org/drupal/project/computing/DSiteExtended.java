package org.drupal.project.computing;

import org.drupal.project.computing.exception.DSiteException;

/**
 * An interface to declare additional features of accessing Drupal.
 */
public interface DSiteExtended {
    /**
     * Execute Drupal API "variable_get()".
     * Perhaps we should use generic method, but implementation is hard.
     * If defaultValue is a JsonElement, will output JsonElement too.
     *
     * @param name Drupal variable name
     * @param defaultValue default value for the variable.
     * @return
     */
    public Object variableGet(String name, Object defaultValue) throws DSiteException, UnsupportedOperationException;

    /**
     * Execute Drupal API "variable_set()"
     *
     * @param name Drupal variable name
     * @param value value to save
     */
    public void variableSet(String name, Object value) throws DSiteException, UnsupportedOperationException;

}
