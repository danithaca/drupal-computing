package org.drupal.project.computing.common;

import org.drupal.project.computing.DApplication;

import java.util.Properties;

/**
 * Default "Computing" application.
 */
public class ComputingApplication extends DApplication {

    public ComputingApplication() {
        super("computing");
    }

    @Override
    protected Properties declareCommandMapping() {
        Properties defaultCommandMapping = new Properties();
        defaultCommandMapping.put("Echo", "org.drupal.project.computing.common.EchoCommand");
        defaultCommandMapping.put("echo", "org.drupal.project.computing.common.EchoCommand");
        return defaultCommandMapping;
    }


    /**
     * Currently we don't take command line input.
     * All configurations should be handled in dcomp.config.file.
     */
    public static void main(String[] args) {
        DApplication application = new ComputingApplication();
        application.launch();
    }
}
