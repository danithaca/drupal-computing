package org.drupal.project.computing;

import java.util.List;

/**
 * Connects to Drupal site via the "services" module.
 * @see http://drupal.org/project/services
 */
public class DServicesSite extends DSite {
    @Override
    public DRecord claimRecord(String appName) throws DSiteException {
        return null;
    }

    @Override
    public void finishRecord(DRecord record) throws DSiteException {

    }

    @Override
    public List<DRecord> queryReadyRecords(String appName) throws DSiteException {
        return null;
    }

    @Override
    public void updateRecord(DRecord record) throws DSiteException {
    }

    @Override
    public void updateRecordField(DRecord record, String fieldName) throws DSiteException {
    }

    @Override
    public long createRecord(DRecord record) throws DSiteException {
        return 0;
    }

    @Override
    public DRecord loadRecord(long id) throws DSiteException {
        return null;
    }

    @Override
    public String getDrupalVersion() throws DSiteException {
        return null;
    }

    @Override
    public Object variableGet(String name, Object defaultValue) throws DSiteException {
        return null;
    }

    @Override
    public void variableSet(String name, Object value) throws DSiteException {
    }

    @Override
    public long getTimestamp() throws DSiteException {
        return 0;
    }
}
