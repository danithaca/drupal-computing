package org.drupal.project.computing.v1.common;

import org.drupal.project.computing.v1.DApplication;
import org.drupal.project.computing.v1.Identifier;

/**
 * This is the common application that illustrated how to use DApplication.
 */
@Identifier("common")
public class CommonApplication extends DApplication {

    @Override
    protected void buildCommandRegistry() {
        registerCommand(PingMe.class);
    }

    public static void main(String[] args) {
        DApplication app = new CommonApplication();
        app.launchFromShell(args);
    }
}
