package org.drupal.project.computing;

import org.drupal.project.computing.exception.DNotFoundException;
import org.drupal.project.computing.exception.DRuntimeException;
import org.drupal.project.computing.exception.DSiteException;

import java.util.logging.Logger;

/**
 * <p>This is the super class to model a Drupal site. A Drupal Computing application will use a DSite class to connect
 * to Drupal sites. The suggested way to pass data between applications and Drupal site is through the DRecord objects
 * (maps to a Computing Entity). You would have your Drupal module write input data to a record, and then your
 * application read it and write results back to the output field in the record, and your Drupal module can read data
 * back to the database.</p>
 *
 * <p>Here we don't require DSite to offer nodeLoad(), nodeSave(), or userLoad(), which might be defined in
 * DSiteExtended. If your application needs to read nodes, the Drupal part of your application will write the node info
 * into the "input" field of computing_record, and then your Java application can read it. However, we don't prevent
 * subclasses to provide a nodeLoad() method. If you use DDrushSite in particular, you can call any Drupal API with
 * drush. Also, you can use DDatabase to access Drupal database directly, but it's not recommended.</p>
 *
 * <p>Some sub-class implementations: DServicesSite, DDrushSite</p>
 */
abstract public class DSite {

    protected Logger logger = DUtils.getInstance().getPackageLogger();


    /**
     * Get one available computing record from Drupal to process. Drupal will handle the logic of providing the record.
     * If there's no record to return, throw DNotFoundException.
     *
     * @param appName the Application name to claim a record.
     * @return A computing record to handle, or NULL is none is found.
     */
    abstract public DRecord claimRecord(String appName) throws DSiteException, DNotFoundException;


    /**
     * After agent finishes computation, return the results to Drupal.
     *
     * @param record the record to mark as finished and send back results.
     * @throws DSiteException
     */
    abstract public void finishRecord(DRecord record) throws DSiteException;


    /**
     * Save the updated record in the database.
     * @param record The computing record to be saved.
     */
    abstract public void updateRecord(DRecord record) throws DSiteException;


    /**
     * Update only the specified field of the record.
     *
     * @param record The computing record to be updated.
     * @param fieldName The field to be updated.
     */
    abstract public void updateRecordField(DRecord record, String fieldName) throws DSiteException;


    /**
     * Save the new record Drupal using the data in the parameter.
     *
     * @param record The newly created record. record.isSave() has too be true.
     * @return The database record ID of the newly created record.
     */
    abstract public long createRecord(DRecord record) throws DSiteException;

    /**
     * Load one record according to its ID. This is expected to return a valid DRecord.
     * If id is invalid, this function will throw an exception.
     *
     * @param id The id of computing record.
     * @return the loaded DRecord.
     */
    abstract public DRecord loadRecord(long id) throws DSiteException;


    /**
     * Check whether connection to Drupal site is established.
     *
     * @return true if the connection is valid. will not throw exceptions.
     */
    public boolean checkConnection() {
        try {
            String drupalVersion = getDrupalVersion();
            logger.info("Drupal version: " + drupalVersion);
            return true;
        } catch (DSiteException e) {
            logger.warning("Cannot connect to Drupal site");
            return false;
        } catch (DRuntimeException e) {
            logger.severe("Check connection unexpected error: " + e.getLocalizedMessage());
            return false;
        }
    }

    /**
     * @return The version of Drupal for this site.
     */
    abstract public String getDrupalVersion() throws DSiteException;


    /**
     * @return Drupal site's current timestamp. Equivalent to PHP time().
     */
    abstract public long getTimestamp() throws DSiteException;
}
