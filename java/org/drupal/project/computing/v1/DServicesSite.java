package org.drupal.project.computing.v1;

import java.util.List;

/**
 * Connects to Drupal site via the "services" module.
 * @see http://drupal.org/project/services
 */
public class DServicesSite extends DSite {
    @Override
    public List<DRecord> queryActiveRecords(String appName) throws DConnectionException {
        return null;
    }

    @Override
    public void updateRecord(DRecord record) throws DConnectionException {
    }

    @Override
    public void updateRecordField(DRecord record, String fieldName) throws DConnectionException {
    }

    @Override
    public long saveRecord(DRecord record) throws DConnectionException {
        return 0;
    }

    @Override
    public DRecord loadRecord(long id) throws DConnectionException {
        return null;
    }

    @Override
    public String getDrupalVersion() throws DConnectionException {
        return null;
    }

    @Override
    public Object variableGet(String name, Object defaultValue) throws DConnectionException {
        return null;
    }

    @Override
    public void variableSet(String name, Object value) throws DConnectionException {
    }

    @Override
    public long getTimestamp() throws DConnectionException {
        return 0;
    }
}
