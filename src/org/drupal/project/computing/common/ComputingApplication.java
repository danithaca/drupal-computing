package org.drupal.project.computing.common;

import org.drupal.project.computing.DApplication;

import java.util.Properties;


public class ComputingApplication extends DApplication {

    public ComputingApplication(String applicationName) {
        super(applicationName);
    }

    @Override
    protected Properties registerDefaultCommandMapping() {
        Properties defaultCommandMapping = new Properties();
        defaultCommandMapping.put("Echo", "org.drupal.project.computing.common.EchoCommand");
        return defaultCommandMapping;
    }


    /**
     * Currently we don't take command line input.
     * All configurations should be handled in dc.config.file.
     */
    public static void main(String[] args) {
        DApplication application = new ComputingApplication("computing");
        application.launch();
    }
}
