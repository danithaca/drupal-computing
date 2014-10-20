package org.drupal.project.computing.v1.jython;

import org.drupal.project.computing.v1.DCommand;
import org.drupal.project.computing.v1.DRecord;

/**
 * This is to help with Jython applications
 */
abstract public class DJyCommand extends DCommand {

    @Override
    public String getIdentifier() {
        throw new IllegalArgumentException("Please override getIdentifier()");
    }

    @Override
    public void keepRecord(DRecord record) {
        // do nothing. jython doesn't enforce overrides abstract functions.
        // if there's no implementation, there will be weird errors.
    }

    @Override
    public void enterRecord(DRecord record) {
        // do nothing. jython doesn't enforce overrides abstract functions.
        // if there's no implementation, there will be weird errors.
    }

}
