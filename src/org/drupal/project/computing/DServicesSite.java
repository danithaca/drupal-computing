package org.drupal.project.computing;

import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang.StringUtils;
import org.drupal.project.computing.exception.DConfigException;
import org.drupal.project.computing.exception.DNotFoundException;
import org.drupal.project.computing.exception.DSiteException;

import javax.script.Bindings;
import java.util.List;
import java.util.Properties;

/**
 * Uses Drupal Services REST Server to access Drupal.
 */
public class DServicesSite extends DSite implements DSiteExtended {

    protected DServices services;

    public DServicesSite(DServices services) {
        this.services = services;
    }

    public static DServicesSite loadDefault() throws DConfigException {
        return new DServicesSite(DServices.loadDefault());
    }

    public DServices getServices() {
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
        return null;
    }

    @Override
    public void finishRecord(DRecord record) throws DSiteException {

    }

    @Override
    public void updateRecord(DRecord record) throws DSiteException {

    }

    @Override
    public void updateRecordField(DRecord record, String fieldName) throws DSiteException {

    }

    @Override
    public long createRecord(DRecord record) throws DSiteException {
        if (!record.isNew() || StringUtils.isBlank(record.getApplication()) || StringUtils.isBlank(record.getCommand())) {
            throw new IllegalArgumentException("DRecord object is not valid.");
        }

        Bindings extraOptions = record.toBindings();
        extraOptions.remove("id");
        extraOptions.remove("application");
        extraOptions.remove("command");
        extraOptions.remove("label");
        extraOptions.remove("input");

        Properties params = new Properties();
        params.put("application", record.getApplication());
        params.put("command", record.getCommand());
        params.put("label", StringUtils.isBlank(record.getLabel()) ? "Process " + record.getCommand() : record.getLabel());
        if (record.getInput() != null) {
            params.put("input", DUtils.Json.getInstance().toJson(record.getInput()));
        }
        if (!extraOptions.isEmpty()) {
            params.put("options", DUtils.Json.getInstance().toJson(extraOptions));
        }

        try {
            // execute request. for some reason this will return a List instead of just the number.
            List idList = (List) services.request("computing.json", "POST", params);
            Long id = DUtils.getInstance().getLong(idList.get(0));

            if (id > 0) {
                return id;
            } else {
                throw new DSiteException("Cannot create computing record with a valid ID.");
            }
        } catch (ClassCastException | IndexOutOfBoundsException | IllegalArgumentException e) {
            throw new DSiteException("Services results unexpected.", e);
        }
    }

    @Override
    public DRecord loadRecord(long id) throws DSiteException {
        connect();
        String requestString = String.format("computing/%d.json", id);
        Bindings data = (Bindings) services.request(requestString, "GET", null);
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
        try {
            return (Bindings) services.request("computing/info.json", "POST", null);
        } catch (ClassCastException e) {
            throw new DSiteException("Services results unexpected.", e);
        }
    }

    @Override
    public Object variableGet(String name, Object defaultValue) throws DSiteException, UnsupportedOperationException {
        return null;
    }

    @Override
    public void variableSet(String name, Object value) throws DSiteException, UnsupportedOperationException {

    }
}
