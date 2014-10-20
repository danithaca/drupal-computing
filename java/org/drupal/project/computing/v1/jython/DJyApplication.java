package org.drupal.project.computing.v1.jython;

import org.apache.commons.lang3.ArrayUtils;
import org.drupal.project.computing.v1.DApplication;

/**
 * This is to help with Jython applications.
 */
abstract public class DJyApplication extends DApplication {

    @Override
    public String getIdentifier() {
        throw new IllegalArgumentException("Please override getIdentifier()");
    }

    @Override
    public void launch() {
        if (cliOptions != null && cliOptions.length >= 3) {
            // in jython application, the first arg is the script name, and we should remove it.
            cliOptions = ArrayUtils.remove(cliOptions, 0);
        }
        super.launch();
    }

}
