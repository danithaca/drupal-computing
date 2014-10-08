package org.drupal.project.computing;

import org.apache.commons.lang.StringUtils;
import org.drupal.project.computing.exception.DConfigException;
import org.drupal.project.computing.exception.DNotFoundException;
import org.drupal.project.computing.exception.DSiteException;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.ArrayList;
import java.util.List;

/**
 * Uses Drupal Services REST Server to access Drupal.
 */
public class DServicesSite extends DSite implements DSiteExtended {

    protected DRestfulJsonServices services;

    public DServicesSite(DRestfulJsonServices services) {
        this.services = services;
    }

    public static DServicesSite loadDefault() throws DConfigException {
        return new DServicesSite(DRestfulJsonServices.loadDefault());
    }

    public DRestfulJsonServices getServices() {
        return services;
    }

    /**
     * Connect to Drupal site with services.
     * This will get called automatically if it's not getting called yet before doing any operations.
     * @throws DSiteException
     */
    public void connect() throws DSiteException {
        if (!services.isAuthenticated()) {
            services.userLogin();
        }
    }

    /**
     * Caller should explicitly call this function to close connection (basically logoff the drupal user).
     *
     * @throws DSiteException
     */
    public void close() throws DSiteException {
        if (services.isAuthenticated()) {
            services.userLogout();
        }
    }

    @Override
    public DRecord claimRecord(String appName) throws DSiteException, DNotFoundException {
        connect();
        Bindings params = new SimpleBindings();
        params.put("application", appName);

        Object response = services.request("computing/claim.json", params, "POST");
        if (response instanceof ArrayList) {
            boolean found = getSingleElementFromList(response, Boolean.class);
            if (!found) {
                throw new DNotFoundException("No more READY record for application: " + appName);
            } else {
                throw new DSiteException("Illegal response from Drupal.");
            }
        } else {
            Bindings recordBindings = (Bindings) response;
            return DRecord.fromBindings(recordBindings);
        }
    }

    @Override
    public void finishRecord(DRecord record) throws DSiteException {
        connect();
        Bindings params = new SimpleBindings();
        params.put("status", record.getStatus());
        params.put("message", record.getMessage());
        if (record.getOutput() != null) {
            params.put("output", record.getOutput());
        }
        // we don't want to throw in extra "options"

        try {
            String requestString = String.format("computing/%d/finish.json", record.getId());
            boolean success = getSingleElementFromList(services.request(requestString, params, "POST"), Boolean.class);
            if (!success) {
                throw new DSiteException("Cannot mark record as done: " + record.getId());
            }
        } catch (IllegalArgumentException e) {
            throw new DSiteException("Cannot retrieve results.", e);
        }
    }

    @Override
    public void updateRecord(DRecord record) throws DSiteException {
        connect();
        try {
            String requestString = String.format("computing/%d.json", record.getId());
            boolean success = getSingleElementFromList(services.request(requestString, record.toBindings(), "PUT"), Boolean.class);
            if (!success) {
                throw new DSiteException("Cannot update record: " + record.getId());
            }
        } catch (IllegalArgumentException e) {
            throw new DSiteException("Cannot retrieve results.", e);
        }
    }

    @Override
    public void updateRecordField(DRecord record, String fieldName) throws DSiteException {
        connect();
        Bindings recordBindings = record.toBindings();
        assert recordBindings.containsKey(fieldName);

        try {
            String requestString = String.format("computing/%d/field.json", record.getId());
            Bindings params = new SimpleBindings();
            params.put("name", fieldName);
            params.put("value", recordBindings.get(fieldName));

            boolean success = getSingleElementFromList(services.request(requestString, params, "POST"), Boolean.class);
            if (!success) {
                throw new DSiteException("Cannot update record on field: " + record.getId() + ", " + fieldName);
            }

        } catch (IllegalArgumentException e) {
            throw new DSiteException("Cannot retrieve results.", e);
        }
    }

    @Override
    public long createRecord(DRecord record) throws DSiteException {
        if (!record.isNew() || StringUtils.isBlank(record.getApplication()) || StringUtils.isBlank(record.getCommand())) {
            throw new IllegalArgumentException("DRecord object is not valid.");
        }

        try {
            // execute request. for some reason this will return a List instead of just the number.
            Long id = getSingleElementFromList(services.request("computing.json", record.toBindings(), "POST"), Long.class);
            if (id > 0) {
                return id;
            } else {
                throw new DSiteException("Cannot create computing record with a valid ID.");
            }
        } catch (IllegalArgumentException e) {
            throw new DSiteException("Cannot retrieve results.", e);
        }
    }

    @Override
    public DRecord loadRecord(long id) throws DSiteException {
        connect();
        String requestString = String.format("computing/%d.json", id);
        Bindings data = services.request(requestString, null, "GET", Bindings.class);
        return DRecord.fromBindings(data);
    }

    @Override
    public String getDrupalVersion() throws DSiteException {
        Bindings data = getSiteInfo();
        try {
            return (String) data.get("drupal_version");
        } catch (ClassCastException e) {
            throw new DSiteException("Services results unexpected.", e);
        }
    }

    @Override
    public long getTimestamp() throws DSiteException {
        Bindings data = getSiteInfo();
        try {
            return DUtils.getInstance().getLong(data.get("drupal_time"));
        } catch (ClassCastException e) {
            throw new DSiteException("Services results unexpected.", e);
        }
    }

    private Bindings getSiteInfo() throws DSiteException {
        connect();
        return services.request("computing/info.json", null, "POST", Bindings.class);
    }

    @Override
    public Object variableGet(String name, Object defaultValue) throws DSiteException, UnsupportedOperationException {
        return null;
    }

    @Override
    public void variableSet(String name, Object value) throws DSiteException, UnsupportedOperationException {

    }

    private <T> T getSingleElementFromList(Object aList, Class<T> classOfT) throws IllegalArgumentException {
        try {
            Object element = ((List<Object>) aList).get(0);
            if (classOfT == Long.class) {
                return (T) DUtils.getInstance().getLong(element);
            } else {
                return (T) element;
            }
        } catch (ClassCastException | IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Cannot get single element from the list.");
        }
    }
}
