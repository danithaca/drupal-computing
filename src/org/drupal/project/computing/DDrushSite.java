package org.drupal.project.computing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang.StringUtils;
import org.drupal.project.computing.exception.DRuntimeException;
import org.drupal.project.computing.exception.DSiteException;

import javax.script.Bindings;
import java.lang.reflect.Type;
import java.util.*;


/**
 * The Drupal instance that can be accessed locally through drush.
 * Meaning that settings.php is accessible and Drush is executable to execute any drupal script.
 */
public class DDrushSite extends DSite {

    private DUtils.Drush drush;

    public DDrushSite(DUtils.Drush drush) {
        this.drush = drush;
    }

    /**
     * Factory method. Create DDrushSite using default config.properties file.
     * @return Default DDrushSite object.
     */
    public static DDrushSite loadDefault() {
        return new DDrushSite(DUtils.Drush.loadDefault());
    }

    /**
     * each Drush site would be able to return a database connection.
     * although remote drupal site usually has "localhost" as host. If that's the case, remote site could use
     * $databases['computing']['default'] is settings.php to get a valid connection
     *
     * @return DDatabase connection. Caller is responsible to close it.
     */
//    @Deprecated
//    public DDatabase getDatabase() throws DSiteException {
//        DConfig config = new DConfig();
//        config.setProperty("drupal.drush", drush.getDrushExec());
//        try {
//            Properties dbProperties = config.getDbProperties();
//            DDatabase db = new DDatabase(dbProperties);
//            return db;
//        } catch (DConfigException e) {
//            throw new DSiteException("Cannot get database connection to Drupal with drush. Please read documentations.", e);
//        }
//    }


    public DUtils.Drush getDrush() {
        return drush;
    }


    @Override
    public String getDrupalVersion() throws DSiteException {
        Bindings coreStatus = drush.getCoreStatus();
        if (!coreStatus.containsKey("drupal-version")) {
            throw new DSiteException("Cannot get drupal version.");
        }
        try {
            return (String) coreStatus.get("drupal-version");
        } catch (ClassCastException e) {
            throw new DSiteException(e);
        }
    }


    @Override
    public Object variableGet(String name, Object defaultValue) throws DSiteException {
        // TODO: think about how to do it with "drush variable-get", and handle multiple variable cases
        // It might be impossible, for example: if we have "var", "var1", "var2", then using drush variable-get var
        // will give us all three values.
        String result = drush.computingCall("variable_get", name, defaultValue);
        return DUtils.Json.getInstance().fromJson(result);
    }

    @Override
    public void variableSet(String name, Object value) throws DSiteException {
        // there's no return value in drupal either.
        drush.computingCall("variable_set", name, value);
    }


    @Override
    public long getTimestamp() throws DSiteException {
        String json = drush.computingCall("time");
        return DUtils.getInstance().getLong(DUtils.Json.getInstance().fromJson(json));
    }


    @Override
    public long createRecord(DRecord record) throws DSiteException {
        // this is not compile-time check, so don't use assert.
        if (!record.isNew() || StringUtils.isBlank(record.getApplication()) || StringUtils.isBlank(record.getCommand())) {
            throw new IllegalArgumentException("DRecord object is not valid.");
        }

        Bindings extraOptions = record.toBindings();
        extraOptions.remove("id");
        extraOptions.remove("application");
        extraOptions.remove("command");
        extraOptions.remove("label");
        extraOptions.remove("input");

        String jsonResult = drush.computingCall("computing_create",
                record.getApplication(),
                record.getCommand(),
                StringUtils.isBlank(record.getLabel()) ? "Process " + record.getCommand() : record.getLabel(),
                record.getInput(), // this will get encoded in JSON regardless of whether it's null or not.
                // handle more data here.
                extraOptions
        );

        try {
            return DUtils.Json.getInstance().fromJson(jsonResult, Long.class);
        } catch (JsonSyntaxException e) {
            throw new DSiteException("Cannot parse JSON result: " + jsonResult, e);
        }
    }

    @Override
    public DRecord loadRecord(long id) throws DSiteException {
        String jsonResult = drush.computingCall("computing_load", id);
        try {
            return DRecord.fromJson(jsonResult);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DSiteException("Cannot parse JSON result: " + jsonResult, e);
        }
    }


    @Override
    public DRecord claimRecord(String appName) throws DSiteException {
        String jsonResult = drush.computingCall("computing_claim",appName);
        try {
            return DRecord.fromJson(jsonResult);
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new DSiteException("Cannot parse JSON result.", e);
        }
    }

    @Override
    public void finishRecord(DRecord record) throws DSiteException {

    }

//    @Override @Deprecated
//    public List<DRecord> queryReadyRecords(String appName) throws DSiteException {
//        String phpCode = String.format("return computing_query_active_records('%s');", appName);
//        String json = drush.computingEval(phpCode);
//
//        // see the JSON example code at Gson class.
//        Gson gson = DUtils.getInstance().getDefaultGson();
//        Type listType = new TypeToken<List<DRecord>>() {}.getType();
//
//        List<DRecord> records;
//        try {
//            records = gson.fromJson(json, listType);
//        } catch (JsonSyntaxException e) {
//            logger.finer("JSON output: " + json);
//            throw new DRuntimeException(e);
//        }
//        return records;
//    }

    @Override
    public void updateRecord(DRecord record) throws DSiteException {
        assert !record.isNew();
        String[] command = {
                "computing_update_record",
                DUtils.Json.getInstance().toJson(record.getId()),
                record.toJson()
        };

        String json = drush.computingCall(command);

        Gson gson = new Gson();
        int updated = 0;
        try {
            updated = gson.fromJson(json, Integer.class);
        } catch (JsonSyntaxException e) {
            logger.finer("JSON output: " + json);
            throw new DRuntimeException(e);
        }
        if (updated != 1) {
            // here we don't throw exception because if no field is changed, record is not updated either.
            logger.warning("Update record failure. Please check code.");
        }
    }

    @Override
    public void updateRecordField(DRecord record, String fieldName) throws DSiteException {
//        assert !record.isNew();
//        String fieldValue = record.toBindings().get(fieldName);
//        String[] command = {
//                "computing_update_record_field",
//                DUtils.getInstance().getDefaultGson().toJson(record.getId()),
//                DUtils.getInstance().getDefaultGson().toJson(fieldName),
//                DUtils.getInstance().getDefaultGson().toJson(fieldValue),
//        };
//        String json = drush.computingCall(command);
//        int updated = DUtils.getInstance().getDefaultGson().fromJson(json, Integer.class);
//        if (updated != 1) {
//            // here we don't throw exception because if no field is changed, record is not updated either.
//            logger.warning("Update record failure. Please check code.");
//        }
    }


    @Override
    public boolean checkConnection() {
        try {
            String drushVersion = drush.getVersion();
            if (drushVersion.substring(0, 1).compareTo("6") < 0) {
                logger.severe("You need drush version 6 or higher. Your version of drush: " + drushVersion);
                return false;
            }
            // after we check here, then let the super function check again.
            return super.checkConnection();
        } catch (DSiteException e) {
            logger.severe("Check connection error: " + e.getLocalizedMessage());
            return false;
        }
    }

}
