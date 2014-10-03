package org.drupal.project.computing;

import org.drupal.project.computing.exception.DCommandExecutionException;
import org.drupal.project.computing.exception.DRuntimeException;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * Individual command to be executed.
 * Most often developers will write subclass of this class. So the interface has to be very simple and easy to understand.
 *
 * You need to overrides 2 methods:
 * 1. prepare(): initialize input from a Bindings object.
 * 2. execute(): execute the command.
 */
abstract public class DCommand implements Runnable, Callable<Void> {


    /**
     * Prepare the command by taking data from "input" and set internal states.
     *
     * Design decisions:
     * 1. There's no "abstract static method" in Java, so we can't use factory method pattern (unless we have a separate Factory class, which is not needed)
     * 2. There's no "abstract constructor" to force a constructor that takes Bindings as input.
     * 3. Therefore, we can only pass in Input Bindings in a regular abstract function, which means the object has to be initialized first, which means we can't have other types of constructors (except for the the default constructor).
     * 4. Therefore, this method "prepare" is the only entry to initialize an DCommand object.
     *
     * @param input data from DRecord.input.
     */
    abstract public void prepare(Bindings input) throws IllegalArgumentException;


    /**
     * this is the core of DCommand. Execute the command after fully initialized from Input data.
     * We expect execute() to run successfully. If an error occurs, throw an exception.
     *
     * Execution should also write to "message" and "result" to pass results back to DApplication.
     */
    abstract public void execute() throws DCommandExecutionException;



    /////////////////////////////////// default implementation or Runnable and Callable  ////////////////////////


    @Override
    public void run() {
        try {
            execute();
        } catch (DCommandExecutionException e) {
            // the caller should catch this exception, and then get "e", which is the DCommandExecutionException.
            throw new DRuntimeException(e);
        }
    }

    @Override
    public Void call() throws DCommandExecutionException {
        execute();
        return null;
    }


    /////////////////////////////////////// other useful stuff /////////////////////////////////////////////////




    protected Logger logger = DUtils.getInstance().getPackageLogger();

    /**
     * Stores execution message to show to Drupal users.
     */
    protected StringBuffer message = new StringBuffer();

    /**
     * Write results back to this so it can be retrieved later.
     */
    protected Bindings result = new SimpleBindings();

    /**
     * This is how the caller function can get the execution mesesge.
     * @return
     */
    public String getMessage() {
        return message.toString();
    }

    /**
     * This is how the caller function can retrieve the execution results.
     * @return
     */
    public Bindings getResult() {
        return result;
    }


    // contextual data, usually you don't want to use them because all the required data to run the command should be passed in with Input.
    // however, we still provide the data just in case.

    /**
     * The site from which the command is created.
     */
    protected DSite site;

    /**
     * The DApplication object that execute the command.
     */
    protected DApplication application;

    /**
     * The record that issues the command
     */
    protected DRecord record;


    /**
     * Set the contextual data.
     *
     * @param record
     * @param site
     * @param application
     */
    public void setContext(DRecord record, DSite site, DApplication application) {
        this.record = record;
        this.site = site;
        this.application = application;
    }

}
