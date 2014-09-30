package org.drupal.project.computing;

import java.lang.annotation.*;

/**
 * Provides an optional identifier to Druplet or AsyncCommand.
 * If not given, the default identifier is just the class name.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Identifier {
    String value();
}

