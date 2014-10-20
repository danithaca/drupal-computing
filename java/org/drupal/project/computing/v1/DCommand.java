package org.drupal.project.computing.v1;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * Individual command to be executed. Each command is also registered with a DApplication.
 * A command doesn't necessarily know a DSite. If needed, it can get from DApplication.
 * The Record class needs to know a DSite in order to do database operations.
 *
 * DCommand has no constructor. To initialize a DCommand, use the "create()" factory method,
 * which also initialize things for all DCommand sub-classes. Sub-classes should implement the map* methods for
 * further initialization.
 */
abstract public class DCommand implements Runnable, Callable<DRecord> {

    protected Logger logger = DUtils.getInstance().getPackageLogger();

    protected DApplication application;

    protected DRecord record;


    /**
     * Create a command sub-class, and then initialize it.
     * @param commandClass
     * @param application
     * @param record
     * @return
     */
    public static DCommand create(Class<? extends DCommand> commandClass, DApplication application, DRecord record) {
        assert commandClass != null && application != null && record != null;
        DCommand command = null;
        try {
            command = (DCommand) commandClass.newInstance();
            command.application = application;
            command.record = record;
        } catch (InstantiationException e) {
            e.printStackTrace();
            assert false;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            assert false;
        }
        return command;
    }


    public static DCommand create(Class<? extends DCommand> commandClass, DApplication application) {
        assert commandClass != null && application != null;
        DRecord record = DRecord.create(application.getIdentifier());
        DCommand command = create(commandClass, application, record);
        record.setCommand(command.getIdentifier());
        record.setDescription("Forged command: " + command.getIdentifier());
        record.setControl(DRecord.Control.CODE);   // mark that the record is created from code.
        try {
            record.setCreated(application.getDrupalSite().getTimestamp());
        } catch (DConnectionException e) {
            DUtils.getInstance().getPackageLogger().warning("Cannot retrieve timestamp from Drupal site.");
        }
        return command;
    }

    protected DApplication getApplication() {
        assert application != null;
        return this.application;
    }

    public DRecord getRecord() {
        assert record != null;
        return this.record;
    }

    /**
    * Each DCommand is associated with one DApplication, which means it's associated with one Drupal site.
    * @return
    */
    protected DSite getDrupalSite() {
        assert application != null : "Application can't be null.";
        return application.getDrupalSite();
    }

    public String getIdentifier() {
        return DUtils.getInstance().getIdentifier(this.getClass());
    }

    protected void markStatus(DRecord.Status status) throws DConnectionException {
        assert record != null;
        record.setStatus(status);
        if (record.isSaved()) {
            application.getDrupalSite().updateRecordField(record, "status");
        }
    }

    protected void markProgress(float progress) throws DConnectionException {
        assert record != null;
        assert progress >= 0.0 && progress <= 1.0 : "Progress is in the range of [0, 1].";
        record.setProgress(progress);
        if (record.isSaved()) {
            application.getDrupalSite().updateRecordField(record, "progress");
        }
    }

    protected void printMessage(String message) {
        // no matter what, let's print it to the logger first.
        logger.info(message);

        assert record != null;
        StringBuffer buffer = new StringBuffer();
        if (record.getMessage() != null) {
            buffer.append(record.getMessage());
        }
        buffer.append(message).append("\n");
        record.setMessage(buffer.toString());
    }

    /**
     * Defines how to map a record to the parameters in the command.
     * All sub-class needs to define this, because a command will be persisted in a record.
     * If there's no need to persist a record, there's no need to derive from DCommand.
     * @param record
     */
    abstract public void enterRecord(DRecord record);


    /**
     * Defines how to map the CLI args to the given record.
     * DApplication will then run enterRecord() so there's no need to initialize the command here.
     * @param record
     * @param args
     */
    public void mapArgs(DRecord record, String[] args) {
        //record.setControl(DRecord.Control.CMLN);
        // remove the unsupported exception? NO. because we want to prompt errors early.
        throw new UnsupportedOperationException("Map parameters from CLI to a record is not supported.");
    }

    /**
     * Defines how to map parameters from code-call to the given record.
     * DApplication will then run enterRecord() so there's no need to initialize the command here.
     * @param params
     * @param record
     */
    public void mapParams(DRecord record, Object... params) {
        //record.setControl(DRecord.Control.CODE);
        // remove the unsupported exception? NO. because we want to prompt errors early.
        throw new UnsupportedOperationException("Map parameters from code to a record is not supported.");
    }

    /**
     * Saves command results back to the record.
     * If you don't want to save results to the record, you don't need to write code here.
     */
    abstract public void keepRecord(DRecord record);

    /**
     * Overrides Runnable.run().
     * Sub-class can either override this method, or override execute() instead. The latter is recommended.
     * If override this method, the sub-class needs to handle "record". If only overrides execute(), no need to handle "record".
     */
    @Override
    public void run() {
        // map record into command parameters.
        enterRecord(record);
        beforeExecute();  // run code before execution
        try {
            execute();
        } catch (DCommandExecutionException e) {
            try {
                // expected failure, mark status as failure
                markStatus(DRecord.Status.FAIL);
                printMessage(e.getMessage());
            } catch (DConnectionException e1) {
                throw new DRuntimeException(e1);
            }
        } catch (DConnectionException e) {
            // since it's already DConnectionException, it makes no sense to markStatus again.
            // throw RuntimeExcetpion directly.
            throw new DRuntimeException(e);
        } catch (DRuntimeException e) {
            try {
                // unexpected error, mark status as "interrupted"
                markStatus(DRecord.Status.INTR);
                printMessage(e.getMessage());
            } catch (DConnectionException e1) {
                throw new DRuntimeException(e1);
            }
        }
        afterExecute(); // run code after execution
        // save results back to record.
        keepRecord(record);
        // leave record persistence to the application.
    }


    protected void beforeExecute() {
        assert application != null && record != null;
        try {
            markStatus(DRecord.Status.RUNG);
            record.setStart(application.getDrupalSite().getTimestamp());
            record.setAgent(DUtils.getInstance().getMachineId()); // use Machine ID to set agent name.
        } catch (DConnectionException e) {
            logger.warning("Drupal connection problem. Cannot set status/start.");
            throw new DRuntimeException(e);
        }
    }


    protected void afterExecute() {
        assert application != null && record != null;
        try {
            long timestamp = application.getDrupalSite().getTimestamp();
            record.setEnd(timestamp);
            record.setUpdated(timestamp);
            if (record.getStatus() == null || record.getStatus() == DRecord.Status.RUNG) {
                markStatus(DRecord.Status.OKOK);
            }
        } catch (DConnectionException e) {
            logger.warning("Drupal connection problem. Cannot set status/updated/end.");
            throw new DRuntimeException(e);
        }
    }

    /**
     * The execution of DCommand sub-class doesn't have to care about DRecord.
     */
    abstract protected void execute() throws DCommandExecutionException, DConnectionException;


    /**
     * Impelments Callable::call(). Usually you don't need to override this.
     * @return
     */
    @Override
    public DRecord call() {
        run();
        return record;
    }
}
