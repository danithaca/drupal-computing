package org.drupal.project.computing;

import org.apache.commons.lang3.StringUtils;
import org.drupal.project.computing.exception.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * <p>This is the base class for Computing Applications. A computing application processes a queue of Computing Record
 * (DRecord) from a Drupal site (DSite) by instantiating and executing a command (DCommand), and saves results back to
 * Drupal. This class is the entry point to all Drupal Computing Java agent programs. It interacts with DConfig,
 * DCommand, DRecord, and DSite.</p>
 * <p>Sub-classes of DApplication will need to overrides declareCommandMapping() to associate a command string in the
 * Computing Record "command" field to its corresponding DCommand sub-class.</p>
 */
abstract public class DApplication {

    //////////////////////////////// abstract methods for overrides //////////////////////

    /**
     * Build the default command mapping from code.
     * @return A Properties object with key as DRecord "command" field, and value as DCommand class name.
     */
    protected abstract Properties declareCommandMapping();


    //////////////////////////////// public methods //////////////////////////////////////


    /**
     * Initialize connection to Drupal site too.
     *
     * @param applicationName The name of the application, which maps to Computing Record's "application" field.
     */
    public DApplication(String applicationName) {
        logger.finest("Create DApplication: " + applicationName);
        this.applicationName = applicationName;

        // no need to use other DConfig file, because users can specify dcomp.file.config.
        this.config = DConfig.loadDefault();
        logger.finest("Loaded (or tried to load) configuration file: " + config.getProperty("dcomp.config.file", "config.properties"));

        this.commandMapping = this.buildCommandMapping();
        logger.finest("Built command mapping, allowed commands: " + StringUtils.join(commandMapping.propertyNames(), ","));

        switch (config.getProperty("dcomp.site.access", "drush")) {
            case "services":
                logger.info("Using Services module for Drupal site access.");
                try {
                    site = DServicesSite.loadDefault();
                } catch (DConfigException e) {
                    logger.severe("Cannot get Services settings.");
                    throw new DRuntimeException(e);
                }
                break;
            case "drush":
            default:
                logger.info("Initializing connection to Drupal via Drush.");
                site = DDrushSite.loadDefault();
                break;
        }

        // check connection.
        if (!site.checkConnection()) {
            logger.severe("Drupal access is not validated.");
        }
    }


    /**
     * Launch the application, and execute commands. By default use launchSingleThread(). Subclasses could use other
     * ways to launch the application, perhaps using multi-thread.
     */
    public void launch() {
        launchSingleThread();
    }


    /**
     * Run a command based on the given record. This is usually used as a programmatic entry to create a record and run
     * it all at once.
     * TODO: Calling the function should not change the parameter "record", which has side-effect.
     *
     * @param record Could be a newly created record or an existing record from Drupal. If record is new, this method
     *               will create it in Drupal first.
     * @return the record after execution.
     */
    public DRecord runOnce(DRecord record) throws DSiteException {
        assert record != null && site != null;

        // check if record is new: then save it first.
        if (record.isNew()) {
            record.setStatus(DRecord.Status.RUN); // set record to be in "RUN" status.
            logger.info("Saving new record to Drupal: " + record.toJson());
            long id = site.createRecord(record);
            // this is to pass the ID to the calling function.
            record.setId(id);
            record = site.loadRecord(id);
        }

        // process record.
        processRecord(record);
        // save result
        site.finishRecord(record);

        return site.loadRecord(record.getId());
    }


    /**
     * Allow register a command mapping ad-hoc.
     *
     * @param commandName the command string in computing record's "command" field.
     * @param className   the DCommand class to be instantiated and executed.
     */
    public void setCommandMapping(String commandName, String className) {
        this.commandMapping.put(commandName, className);
    }


    /**
     * Getter for DSite field, which is initialized in constructor with settings from config.properties.
     * @return The Drupal site (DSite) which this application is associated with.
     */
    public DSite getSite() {
        assert site != null;
        return this.site;
    }


    /**
     * @return The application name that identifies this application.
     */
    public String toString() {
        return this.applicationName;
    }


    ////////////////////////////// methods available for overrides //////////////////////////////////



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


    /**
     * This is the main execution point for each Computing Record. The parameter "record" will change before and after
     * execution. This function will try to catch exceptions and set Record execution status as 'FLD'. This does not
     * catch "DSiteException" because command execution doesn't throws it (unless it access DSite). The caller function
     * (usually launch() or runOnce() will handle DRecord, which then might get DSiteException. If the DCommand class
     * does need to access DSite, it might throws DCommandExecutionException that wraps a DSiteException.
     *
     * @param record the Computing Record to be processed.
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
        } catch (NoClassDefFoundError e) {
            e.printStackTrace();
            record.setMessage("Cannot find required class. Check your CLASSPATH settings. " + e.getMessage());
            record.setStatus(DRecord.Status.FLD);
        }
    }

    /**
     * Create a DCommand based on commandName string.
     *
     * @param commandName the command string
     * @return the DCommand object that was mapped from the command string.
     */
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


    /**
     * Run DApplication in a single thread that process the queue of DRecord from DSite in a sequential manner. Process
     * at most 100 DRecord at a time.
     */
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
                // most exceptions are handled within "processRecord()".
                // we are not able to handle DSiteException here. just log a message and exit.
                e.printStackTrace();
                logger.severe("Drupal site error: " + e.getMessage());
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
     * <ol>
     *     <li>Get all mappings from code.</li>
     *     <li>Get mappings from command.properties, which will override anything defined in #1.</li>
     * </ol>
     *
     * @return Command mapping with the key as DRecord "command" field, and value as DCommand class name.
     */
    protected Properties buildCommandMapping() {
        Properties commandMapping = new Properties();

        // first, get properties from code.
        commandMapping.putAll(declareCommandMapping());

        // second, check mapping from command.properties.
        String commandFileName = config.getProperty("dcomp.command.file", "command.properties");
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

}
