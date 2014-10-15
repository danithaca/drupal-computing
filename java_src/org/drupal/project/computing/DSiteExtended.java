package org.drupal.project.computing;

import org.drupal.project.computing.exception.DSiteException;

/**
 * An interface to declare additional features.
 */
public interface DSiteExtended {
    /**
     * Execute Drupal API "variable_get()".
     * Perhaps we should use generic method, but implementation is hard.
     * If defaultValue is a JsonElement, will output JsonElement too.
     *
     * @param name
     * @param defaultValue
     * @return
     */
    public Object variableGet(String name, Object defaultValue) throws DSiteException, UnsupportedOperationException;

    /**
     * Execute Drupal API "variable_set()"
     *
     * @param name
     * @param value
     */
    public void variableSet(String name, Object value) throws DSiteException, UnsupportedOperationException;

}
