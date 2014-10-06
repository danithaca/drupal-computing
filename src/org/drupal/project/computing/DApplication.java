package org.drupal.project.computing;

import org.drupal.project.computing.exception.DCommandExecutionException;
import org.drupal.project.computing.exception.DNotFoundException;
import org.drupal.project.computing.exception.DSiteException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * <p>This is the application class. It integrates DSite, DConfig, DDatabase and a set of DCommand. Typically,
 * a DApplication has one DSite, one DConfig, zero or one DDatabase. The DApplication is initialized with DConfig,
 * then it creates a DSite (which is not aware of DConfig, because DConfig configs DApplication, where DSite is
 * independent from the Application/Command structure and only deals with Drupal), and from DSite it can retrieve a list
 * of DCommand and then execute them. DCommand can access DApplication in order to get DConfig etc. DSite doesn't have
 * access to either DApplication or DConfig. DSite can generate zero or one DDatabase that links to Drupal db, although
 * DApplication can have multiple DDatabase. </p>
 *
 * TODO: draw UML diagram
 *
 * <b>Additional thoughts:</b>
 *
 * It'll be nice to have "site" and "config" as "final". But that means they have to be set in constructors,
 * and launchFromShell() would have to be static factory method, and then it'll increase complexity.
 *
 * This design combines DApplication and DLauncher. Pro: simple. Con: Can't use other launcher easily.
 */

abstract public class DApplication {

    protected Logger logger = DUtils.getInstance().getPackageLogger();

    /**
     * The name of the Application
     */
    protected final String applicationName;

    /**
     * Access to Drupal site.
     */
    protected DSite site;

    /**
     * The mapping of command name to DCommand class name.
     */
    protected Properties commandMapping;

    /**
     * Default configurations for the Agent.
     */
    protected DConfig config;


    public DApplication(String applicationName) {
        logger.finest("Create DApplication: " + applicationName);
        this.applicationName = applicationName;

        logger.finest("Loading configuration file.");
        this.config = DConfig.loadDefault();

        logger.finest("Building command mapping.");
        this.commandMapping = this.registerCommandMapping();

        switch (config.getProperty("dc.drush.access", "drush")) {
            case "services":
                // set site to be services.
                break;
            case "drush":
            default:
                logger.finest("Initializing connection to Drupal via Drush.");
                site = DDrushSite.loadDefault();
                break;
        }
    }


    /**
     * This is the main execution point.
     * Input: DRecord. Output: DRecord.
     * @param record
     */
    protected void processRecord(DRecord record) {
        assert record != null && !record.isNew() && record.getApplication().equals(applicationName);

        try {

            // prepare the command
            logger.info("Preparing to executing command: " + record.getCommand() + ". ID: " + record.getId());
            DCommand command = createCommand(record.getCommand());
            command.setContext(record, this.site, this, this.config);
            command.prepare(record.getInput());

            // execute it.
            command.execute();

            // retrieve results.
            record.setMessage(command.getMessage());
            record.setOutput(command.getResult());
            // if no error found, set status to be successful. error will cause exception and out of the loop.
            record.setStatus(DRecord.Status.SCF);
            logger.info("Command execution accomplished.");

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            record.setMessage("Input error. " + e.getMessage());
            record.setStatus(DRecord.Status.FLD);

        } catch (DCommandExecutionException e) {
            e.printStackTrace();
            record.setMessage("Command execution error. " + e.getMessage());
            record.setStatus(DRecord.Status.FLD);

        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
            record.setMessage("Cannot identify or instantiate command. " + e.getMessage());
            record.setStatus(DRecord.Status.FLD);
        }
    }


    protected DCommand createCommand(String commandName) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        assert commandMapping != null;

        // the command class to be created.
        String className;
        if (commandMapping.containsKey(commandName)) {
            className = commandMapping.getProperty(commandName);
        } else {
            className = commandName;
        }

        Class<DCommand> commandClass = (Class<DCommand>) Class.forName(className);
        return commandClass.newInstance();
    }


    public void launch() {
        launchSingleThread();
    }


    protected void launchSingleThread() {
        assert site != null;

        DRecord record;
        // do 100 records at most.
        for (int i = 0; i < 100; i++) {
            try {

                record = site.claimRecord(applicationName);
                processRecord(record);
                site.finishRecord(record);

            } catch (DSiteException e) {
                e.printStackTrace();
                logger.severe("Connect Drupal site error: " + e.getMessage());
                // TODO: try to write error into record.

                break;
            } catch (DNotFoundException e) {
                // this exception is expected.
                logger.info("No more record with READY status for application '" + applicationName + "'.");
                break;
            }
        }
    }


    /**
     * Retrieve the mapping from DRecord "command" name to a DCommand class.
     * 1. Get all mappings from code.
     * 2. Get mappings from command.properties, which will override anything defined in #1.
     *
     * @return Command mapping with the key as DRecord "command" field, and value as DCommand class name.
     */
    protected Properties registerCommandMapping() {
        Properties commandMapping = new Properties();

        // first, get properties from code.
        commandMapping.putAll(registerDefaultCommandMapping());

        // second, check mapping from command.properties.
        String commandFileName = config.getProperty("dc.command.file", "command.properties");
        Properties commandMappingOverride = new Properties();

        // try to get file
        try {
            File commandFile = DUtils.getInstance().locateFile(commandFileName);
            commandMappingOverride.load(new FileReader(commandFile));
        } catch (FileNotFoundException e) {
            logger.warning("Cannot locate command mapping file: " + commandFileName);
        } catch (IOException e) {
            logger.warning("Cannot read command mapping file: " + commandFileName);
        }
        
        if (!commandMappingOverride.isEmpty()) {
            commandMapping.putAll(commandMappingOverride);
        }

        return commandMapping;
    }

    /**
     * Build the default command mapping from code.
     *
     * @return A Properties object with key as DRecord "command" field, and value as DCommand class name.
     */
    protected abstract Properties registerDefaultCommandMapping();

}
