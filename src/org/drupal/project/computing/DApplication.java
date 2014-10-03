package org.drupal.project.computing;

import org.drupal.project.computing.exception.DCommandExecutionException;
import org.drupal.project.computing.exception.DNotFoundException;
import org.drupal.project.computing.exception.DSiteException;

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
 *
 * This is not an abstract class. This is the most basic class where other applications can build on it.
 */

public class DApplication {

    protected Logger logger = DUtils.getInstance().getPackageLogger();

    // most imported properties
    final String applicationName;
    DSite site;

    public DApplication(String applicationName) {
        this.applicationName = applicationName;
    }


    /**
     * This is the main execution
     * @param record
     */
    protected void processRecord(DRecord record) {
        assert record != null && !record.isNew() && record.getApplication().equals(applicationName);

        try {

            // prepare the command
            DCommand command = createCommand(record.getCommand());
            command.setContext(record, this.site, this);
            command.prepare(record.getInput());

            // execute it.
            command.execute();

            // retrieve results.
            record.setMessage(command.getMessage());
            record.setOutput(command.getResult());
            // if no error found, set status to be successful. error will cause exception and out of the loop.
            record.setStatus(DRecord.Status.SCF);

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            record.setMessage("Input error. " + e.getMessage());
            record.setStatus(DRecord.Status.FLD);

        } catch (DCommandExecutionException e) {
            e.printStackTrace();
            record.setMessage("Command execution error. " + e.getMessage());
            record.setStatus(DRecord.Status.FLD);
        }
    }


    protected DCommand createCommand(String commandName) {
        // first, check mapping from config.properties.

        // second, check coded command map

        // finally, check if we can create object directly from commandName

        return null;
    }


    protected void launch() {
        launchSingleThread();
    }


    private void launchSingleThread() {
        site = DDrushSite.loadDefault();
        DRecord record;
        for (int i = 0; i < 100; i++) {
            try {

                record = site.claimRecord(applicationName);
                processRecord(record);
                site.finishRecord(record);

            } catch (DSiteException e) {
                e.printStackTrace();
                logger.severe("Connect Drupal site error: " + e.getMessage());
                break;
            } catch (DNotFoundException e) {
                // this exception is expected.
                logger.info("Found no record with READY status for application '" + applicationName + "'.");
                break;
            }
        }
    }


    public static void main(String[] args) {
        DApplication application = new DApplication("computing");
        application.launch();
    }

}
