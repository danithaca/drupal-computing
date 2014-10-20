package org.drupal.project.computing.v1;

import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Uses direct JDBC database access to connect to Drupal site database.
 * Unimplemented yet.
 */
public class DSqlSite extends DSite {

    protected DDatabase database;

    // we want to have caller create db and close it, so we don't create DDatabase here.

    /*public DSqlSite() throws DConfigException, DConnectionException {
        this(new DDatabase(new DConfig().getDbProperties()));
    }

    public DSqlSite(DConfig config) throws DConfigException, DConnectionException {
        this(new DDatabase(config.getDbProperties()));
    }*/

    /**
     * Create a DSite instance using direct database access.
     *
     * @param database
     */
    public DSqlSite(DDatabase database) {
        this.database = database;
    }

    public DDatabase getDatabase() {
        return database;
    }

    @Override
    public List<DRecord> queryActiveRecords(String appName) throws DConnectionException {
        List<DRecord> records = new ArrayList<DRecord>();
        List<Map<String, Object>> rows = database.query("SELECT * FROM {computing_record} WHERE status IS NULL and app = ?", appName);
        for (Map<String, Object> row : rows) {
            records.add(new DRecord(row));
        }
        return records;
    }

    @Override
    public void updateRecord(DRecord record) throws DConnectionException {
        assert record != null && record.isSaved();
        Map<String, Object> recordMap = record.toMap();
        Long id = (Long) recordMap.remove("id");

        List<String> keys = new ArrayList<String>(recordMap.size());
        List<Object> params = new ArrayList<Object>(recordMap.size());

        for (Map.Entry<String, Object> entry : recordMap.entrySet()) {
            keys.add(entry.getKey() + " = ?");
            params.add(entry.getValue());
        }
        params.add(id);  // append 'id' at the end for the WHERE sql clause.

        database.update("UPDATE {computing_record} SET " + StringUtils.join(keys, ", ") + " WHERE id = ?", params.toArray());
    }

    @Override
    public void updateRecordField(DRecord record, String fieldName) throws DConnectionException {
        assert record != null && record.isSaved() && fieldName != null;
        Map<String, Object> recordMap = record.toMap();
        assert recordMap.containsKey(fieldName);
        database.update("UPDATE {computing_record} SET " + fieldName + " = ? WHERE id = ?", recordMap.get(fieldName), record.getId());
    }

    @Override
    public long saveRecord(DRecord record) throws DConnectionException {
        assert record != null && !record.isSaved();
        Map<String, Object> recordMap = record.toMap();
        recordMap.remove("id");

        List<String> keys = new ArrayList<String>(recordMap.size());
        List<Object> params = new ArrayList<Object>(recordMap.size());

        for (Map.Entry<String, Object> entry : recordMap.entrySet()) {
            keys.add(entry.getKey());
            params.add(entry.getValue());
        }
        // generate a list of ?, ?, ?, ....
        String questionMarks = StringUtils.join(StringUtils.repeat('?', keys.size()).toCharArray(), ", ");

        return database.insert("INSERT INTO {computing_record}(" + StringUtils.join(keys.toArray(), ", ") + ") VALUE(" + questionMarks + ")", params);
    }

    @Override
    public DRecord loadRecord(long id) throws DConnectionException {
        List<Map<String, Object>> rows = database.query("SELECT * FROM {computing_record} WHERE id = ?", id);
        assert rows.size() <= 1;

        if (rows.size() == 1) {
            return new DRecord(rows.get(0));
        } else {
            return null;
        }
    }

    @Override
    public String getDrupalVersion() throws DConnectionException {
        String version = (String) variableGet("computing_drupal_version", null);
        if (version != null) {
            return version;
        } else {
            throw new DConnectionException("Cannot get Drupal version. Possible reason: computing module not installed.");
        }
    }

    @Override
    public Object variableGet(String name, Object defaultValue) throws DConnectionException {
        assert name != null;
        byte[] serializedBytes = (byte[]) database.queryValue("SELECT value FROM {variable} WHERE name = ?", name);
        if (serializedBytes != null) {
            try {
                DUtils.Php php = new DUtils.Php();
                if (defaultValue == null) {
                    return php.unserialize(serializedBytes);
                } else {
                    return php.unserialize(serializedBytes, defaultValue.getClass());
                }
            } catch (DSystemExecutionException e) {
                throw new DRuntimeException("Cannot execute PHP in order to parse variable value.");
            }
        } else {
            return defaultValue;
        }
    }

    @Override
    public void variableSet(String name, Object value) throws DConnectionException {
        assert name != null;
        byte[] serializedBytes;
        try {
            DUtils.Php php = new DUtils.Php();
            serializedBytes = php.serialize(value);
        } catch (DSystemExecutionException e) {
            throw new DRuntimeException("Cannot execute PHP in order to serialize variable value.");
        }
        if (database.queryValue("SELECT name FROM {variable} WHERE name = ?", name) != null) {
            database.update("UPDATE {variable} SET value = ? WHERE name = ?", serializedBytes, name);
        } else {
            database.insert("INSERT INTO {variable}(name, value) VALUE(?, ?)", name, serializedBytes);
        }
    }

    @Override
    public long getTimestamp() throws DConnectionException {
        // current_timestamp() is SQL 92 standard. should get supported by all DBMS.
        // attention: tested on MySQL; don't know if it works for other DBMS.
        Timestamp timestamp = (Timestamp) database.queryValue("SELECT CURRENT_TIMESTAMP()");
        return timestamp.getTime() / 1000;
    }

    public static class UnitTest {

        //@Test
        public void testVariables() throws Exception {
            DConfig config = new DConfig();
            config.setProperty("drupal.db.max_batch_size", "2");
            DDatabase db = new DDatabase(config.getDbProperties());

//            String s1 = DrupletUtils.evalPhp("echo serialize(2);");
//            Object[][] params1 = {{"async_command_test1", "1".getBytes()}, {"async_command_test2", s1.getBytes()}, {"async_command_test3", "3".getBytes()}};
//            conn.batch("INSERT INTO {variable}(name, value) VALUES(?, ?)", params1);
//
//            long num1 = (Long) conn.queryValue("SELECT COUNT(*) FROM {variable} WHERE name LIKE 'async_command_test%'");
//            assertEquals(3, num1);
//
//            int v1 = (Integer) conn.variableGet("async_command_test2");
//            assertEquals(v1, 2);
//
//            // test variable set.
//            conn.variableSet("async_command_test2", 100);
//            v1 = (Integer) conn.variableGet("async_command_test2");
//            assertEquals(v1, 100);
//            conn.variableSet("async_command_test2", "Hello");
//            s1 = (String) conn.variableGet("async_command_test2");
//            assertEquals(s1, "Hello");
//
//
//            String s2 = DrupletUtils.evalPhp("echo serialize('abc');");
//            Object[][] params2 = {{s2.getBytes(), "async_command_test1"}};
//            conn.batch("UPDATE {variable} SET value=? WHERE name=?", params2);
//            String v2 = (String) conn.variableGet("async_command_test1");
//            assertEquals(v2, "abc");
//
//            // re-establish connection
//            conn.close();
//            prop = DrupletUtils.loadProperties(DrupletUtils.getConfigPropertiesFile());
//            prop.setProperty("db_max_batch_size", "0");
//            conn = new DrupalConnection(new DrupletConfig(prop));
//            conn.connect();
//
//            Object[][] params3 = {{"async_command_test1"}, {"async_command_test2"}, {"async_command_test3"}};
//            conn.batch("DELETE FROM {variable} WHERE name=?", params3);
//            long num2 = (Long) conn.queryValue("SELECT COUNT(*) FROM {variable} WHERE name LIKE 'async_command_test%'");
//            assertEquals(num2, 0);
//            conn.close();
        }
    }
}
