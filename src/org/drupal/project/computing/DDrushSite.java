package org.drupal.project.computing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * The Drupal instance that can be accessed locally through drush.
 * Meaning that settings.php is accessible and Drush is executable to execute any drupal script.
 */
public class DDrushSite extends DSite {

    private DUtils.Drush drush;

    public DDrushSite(String drushExec) {
        this.drush = new DUtils.Drush(drushExec);
    }

    public DDrushSite() {
        this(null);
    }

    /**
     * each Drush site would be able to return a database connection.
     * although remote drupal site usually has "localhost" as host. If that's the case, remote site could use
     * $databases['computing']['default'] is settings.php to get a valid connection
     *
     * @return DDatabase connection. Caller is responsible to close it.
     */
    public DDatabase getDatabase() throws DSiteException {
        DConfig config = new DConfig();
        config.setProperty("drupal.drush", drush.getDrushExec());
        try {
            Properties dbProperties = config.getDbProperties();
            DDatabase db = new DDatabase(dbProperties);
            return db;
        } catch (DConfigException e) {
            throw new DSiteException("Cannot get database connection to Drupal with drush. Please read documentations.", e);
        }
    }

    public DUtils.Drush getDrush() {
        return drush;
    }

    @Override
    public String getDrupalVersion() throws DSiteException {
        Properties coreStatus = drush.getCoreStatus();
        if (!coreStatus.containsKey("drupal_version")) {
            throw new DRuntimeException("Cannot get drupal version.");
        }
        return coreStatus.getProperty("drupal_version");
    }

    @Override
    public Object variableGet(String name, Object defaultValue) throws DSiteException {
        // TODO: think about how to do it with "drush variable-get", and handle multiple variable cases
        // It might be impossible, for example: if we have "var", "var1", "var2", then using drush variable-get var
        // will give us all three values.
        String[] command = {
                "variable_get",
                DUtils.getInstance().toJson(name),
                DUtils.getInstance().toJson(defaultValue),
        };

        String result = drush.computingCall(command);

        // this logic is not tested yet.
        if (defaultValue != null) {
            return DUtils.getInstance().getDefaultGson().fromJson(result, defaultValue.getClass());
        } else {
            // defaultValue == null
            return DUtils.getInstance().getDefaultGson().fromJson(result, null);
        }
    }

    @Override
    public void variableSet(String name, Object value) throws DSiteException {
        String[] command = {
                "variable_set",
                DUtils.getInstance().toJson(name),
                DUtils.getInstance().toJson(value),
        };
        // there's no return value in drupal either.
        drush.computingCall(command);
    }

    @Override
    public long getTimestamp() throws DSiteException {
        String json = drush.computingCall(new String[]{"time"});
        return DUtils.getInstance().getDefaultGson().fromJson(json, Long.class);
    }

    @Override
    public DRecord claimRecord(String appName) throws DSiteException {
        return null;
    }

    @Override
    public List<DRecord> queryReadyRecords(String appName) throws DSiteException {
        String phpCode = String.format("return computing_query_active_records('%s');", appName);
        String json = drush.computingEval(phpCode);

        // see the JSON example code at Gson class.
        Gson gson = DUtils.getInstance().getDefaultGson();
        Type listType = new TypeToken<List<DRecord>>() {}.getType();

        List<DRecord> records;
        try {
            records = gson.fromJson(json, listType);
        } catch (JsonSyntaxException e) {
            logger.finer("JSON output: " + json);
            throw new DRuntimeException(e);
        }
        return records;
    }

    @Override
    public void updateRecord(DRecord record) throws DSiteException {
        assert record.isSaved();
        String[] command = {
                "computing_update_record",
                DUtils.getInstance().toJson(record.getId()),
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
        assert record.isSaved();
        String fieldValue = record.toProperties().getProperty(fieldName, "");
        String[] command = {
                "computing_update_record_field",
                DUtils.getInstance().getDefaultGson().toJson(record.getId()),
                DUtils.getInstance().getDefaultGson().toJson(fieldName),
                DUtils.getInstance().getDefaultGson().toJson(fieldValue),
        };
        String json = drush.computingCall(command);
        int updated = DUtils.getInstance().getDefaultGson().fromJson(json, Integer.class);
        if (updated != 1) {
            // here we don't throw exception because if no field is changed, record is not updated either.
            logger.warning("Update record failure. Please check code.");
        }
    }

    @Override
    public long createRecord(DRecord record) throws DSiteException {
        assert !record.isSaved();
        assert record.getApp() != null && record.getCommand() != null
                && record.getApp().length() > 0 && record.getCommand().length() > 0;

        String[] command = {
                "computing-create",
                record.getApp(),
                record.getCommand(),
                record.getDescription() == null ? "N/A" : record.getDescription(),
                "-", // read from STDIN to handle long input/output json.
                "--json",
                "--pipe"
        };

        String result = drush.execute(command, record.toJson());
        return DUtils.getInstance().getLong(result.trim());
    }

    @Override
    public DRecord loadRecord(long id) throws DSiteException {
        String phpCode = String.format("return computing_load_record(%d);", id);
        String json = drush.computingEval(phpCode);

        Gson gson = new GsonBuilder().create();
        // attention: this could have precision errors: eg. float number wouldn't be very accurate!
        // FIXME: if the record with the id does not exist, there would be errors.
        DRecord record = gson.fromJson(json, DRecord.class);
        return record;
    }


    @Override
    public boolean checkConnection() {
        try {
            String drushVersion = drush.getVersion();
            if (drushVersion.substring(0, 1).compareTo("5") < 0) {
                logger.severe("You need drush version 5+. Your version of drush: " + drushVersion);
                return false;
            }
            // after we check here, then let the super function check again.
            return super.checkConnection();
        } catch (DSiteException e) {
            logger.severe("Check connection error: " + e.getLocalizedMessage());
            return false;
        }
    }


    //////////////////////////// unit test ///////////////////////////////////

    public static class UnitTest {

        private DDrushSite site;

        @Before
        public void setUp() {
            site = new DDrushSite("drush @mturk");
        }

        private DRecord createRecord() {
            Map map = new HashMap();
            map.put("app", "common");
            map.put("command", "drush");
            map.put("description", "test from drush " + RandomStringUtils.randomAlphanumeric(10));
            map.put("id1", new Random().nextInt(10000));
            map.put("string1", "hello,world");
            return new DRecord(map);
        }

        @Test
        public void testSave() throws DSiteException {
            Logger logger = DUtils.getInstance().getPackageLogger();
            logger.setLevel(Level.FINEST);

            DRecord r0 = createRecord();
            //System.out.println(r0.toJson());
            long id = site.createRecord(r0);

            DRecord r1 = site.loadRecord(id);
            //System.out.println(r1.toString());
            assertEquals(r0.getId1(), r1.getId1());
        }

        @Test
        public void testLoad() throws DSiteException {
            //DRecord r1 = site.loadRecord(6);
            //System.out.println(r1.toString());
            List<DRecord> records = site.queryReadyRecords("common");
            for (DRecord r : records) {
                //System.out.println(r.toString());
            }
        }

        @Test
        public void testUpdate() throws DSiteException {
            DRecord r0 = createRecord();
            assertTrue(!r0.isSaved());

            long id = site.createRecord(r0);
            DRecord r1 = site.loadRecord(id);
            assertTrue(r1.isSaved());
            assertTrue(r1.isActive());

            r1.setStatus(DRecord.Status.RUNG);
            r1.setControl(DRecord.Control.REDY);
            r1.setMessage("Hello,world.");
            r1.setId3(101L);
            //r1.writeOutput("");   // output has problem for now.
            site.updateRecord(r1);

            DRecord r2 = site.loadRecord(id);
            assertEquals(DRecord.Status.RUNG, r2.getStatus());
            assertEquals(DRecord.Control.REDY, r2.getControl());
            //assertEquals("", new String(r2.getOutput()));
            assertEquals(101L, (long)r2.getId3());

            r2.setString1("Hello, world");
            r2.setStatus(DRecord.Status.OKOK);
            site.updateRecordField(r2, "string1");
            DRecord r3 = site.loadRecord(id);
            assertEquals("Hello, world", r3.getString1());
            // since we only update the field, status is still RUNG rather than OKOK.
            assertEquals(DRecord.Status.RUNG, r3.getStatus());
        }

        @Test
        public void testVariable() throws DSiteException {
            // test string variables
            site.variableSet("computing_test", "hello, world");
            assertEquals("hello, world", site.variableGet("computing_test", ""));
            assertEquals("N/A", site.variableGet("computing_test_1", "N/A"));

            // test integer variables
            site.variableSet("computing_test", 1);
            assertEquals(1, site.variableGet("computing_test", 1));
            assertEquals(2, site.variableGet("computing_test_1", 2));
            site.variableSet("computing_test", 0);
            assertEquals(0, site.variableGet("computing_test", 1));

            site.getDrush().execute(new String[]{"vdel", "computing_test", "--exact", "--yes"});
        }

    }

}
