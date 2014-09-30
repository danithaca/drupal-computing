package org.drupal.project.computing;

import junit.framework.Assert;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.ArrayListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * Connect to Drupal through direct database access. It uses JDBC and Apache Commons DBUtils, so it's independent of
 * DBMS implementations. Currently we support MySQL and PostgreSQL.
 *
 * Due to security reasons, it is not recommended to use this class directly to access Drupal database unless necessary.
 * Typical reasons to use this class: 1) performance, 2) prototyping. Other than those 2 cases, you should consider
 * to use {computing_record} to pass data in/out of Drupal.
 *
 * This can be used as standalone class.
 */

public class DDatabase {

    protected Logger logger = DUtils.getInstance().getPackageLogger();

    protected final Properties dbProperties;

    // Save dbPrefix, which is quite costly to retrieve
    private final String dbPrefix;
    private final int maxBatchSize;


    /**
     * Basic info about Drupal database driver's type.
     * The name follows Drupal's convention: mysql, pgsql
     */
    public static enum DatabaseDriver {

        MYSQL("mysql", "com.mysql.jdbc.Driver"),
        PGSQL("postgresql", "org.postgresql.Driver"),
        UNKNOWN("", "");

        private final String jdbcName;
        private final String jdbcDriver;

        DatabaseDriver(String jdbcName,String jdbcDriver) {
            this.jdbcName = jdbcName;
            this.jdbcDriver = jdbcDriver;
        }

        public String getJdbcName() {
            return jdbcName;
        }

        public String getJdbcDriver() {
            return jdbcDriver;
        }
    }

    /**
     * JDBC datasource for this Drupal connection.
     * we use BasicDataSource rather than the DataSource interface to check the closed property
     */
    protected BasicDataSource dataSource;


    /**
     * Initialize connection pooling and connect to the Drupal database.
     * In the default database settings, we don't set AutoCommit/TransactionLevel etc pragmatically.
     * Such settings should be set in the database connection string.
     */
    public DDatabase(Properties dbProperties) throws DSiteException {
        this.dbProperties = dbProperties;
        dbPrefix = dbProperties.getProperty("prefix", null);
        maxBatchSize = Integer.parseInt(dbProperties.getProperty("max_batch_size", "0"));
        this.connect();
    }

    private void connect() throws DSiteException {
        assert dataSource == null;

        try {
            // create data source.
            dataSource = (BasicDataSource) BasicDataSourceFactory.createDataSource(dbProperties);
        } catch (Exception e) {
            logger.severe("Error initializing DataSource for Drupal database connection.");
            throw new DSiteException(e);
        }
    }

    /**
     * Don't forget to close the connections after using it.
     * @throws DSiteException
     */
    public void close() throws DSiteException {
        assert dataSource != null;
        try {
            dataSource.close();
        } catch (SQLException e) {
            logger.severe("Cannot close Drupal database connection.");
            throw new DSiteException(e);
        }
    }

    /**
     * Get one database JDBC connection. The caller is responsible to close the connection.
     * @return java.sql.Connection.
     * @throws DSiteException
     */
    public Connection getConnection() throws DSiteException {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new DSiteException(e);
        }
    }

    public DataSource getDataSource() {
        assert dataSource != null;
        return dataSource;
    }

    public DatabaseDriver getDatabaseDriver() {
        DatabaseDriver driver = DatabaseDriver.UNKNOWN;
        for (DatabaseDriver d : DatabaseDriver.class.getEnumConstants()) {
            if (StringUtils.equals(d.jdbcDriver, dbProperties.getProperty("driverClassName"))) {
                driver = d;
                break;
            }
        }
        return driver;
    }

    /**
     * "Decorates" the SQL statement for Drupal. replace {table} with prefix.
     * @param sql the original SQL statement to be decorated.
     * @return the "decorated" SQL statement
     */
    public String d(String sql) {
        String newSql;
        if (StringUtils.isNotBlank(dbPrefix)) {
            newSql = sql.replaceAll("\\{(.+?)\\}", dbPrefix+"_"+"$1");
        }
        else {
            newSql = sql.replaceAll("\\{(.+?)\\}", "$1");
        }
        logger.finest(newSql);
        return newSql;
    }

    //////////////////////////////// db operations /////////////////////////////


    // attention: later on should add the "connection" version of queries.

    /**
     * Simply run Drupal database queries e.g., query("SELECT nid, title FROM {node} WHERE type=?", "forum");
     *
     * @param sql SQL query to be executed, use {} for table names
     * @param params Parameters to complete the SQL query
     * @return A list of rows as Map (column => value)
     */
    public List<Map<String, Object>> query(String sql, Object... params) throws DSiteException {
        QueryRunner q = new QueryRunner(dataSource);
        // This is a hack in response to https://issues.apache.org/jira/browse/DBUTILS-24
        // Modify BasicRowProcessor to use the column label
        RowProcessor hackRowProcessor = new BasicRowProcessor() {
            @Override
            public Map<String, Object> toMap(ResultSet rs) throws SQLException {
                // can't use CaseInsensitiveHashMap directly because it is private.
                Map<String, Object> result = super.toMap(rs);
                // test if getColumnName() is the same as getColumnLabel()
                ResultSetMetaData rsmd = rs.getMetaData();
                int cols = rsmd.getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    if (!rsmd.getColumnName(i).equals(rsmd.getColumnLabel(i))) {
                        result.remove(rsmd.getColumnName(i));
                        result.put(rsmd.getColumnLabel(i), rs.getObject(i));
                    }
                }
                return result;
            }
        };

        try {
            return (List<Map<String, Object>>) q.query(d(sql), new MapListHandler(hackRowProcessor), params);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new DSiteException(e);
        }
    }

    /**
     * Simply run Drupal database queries, returning one value. e.g. query("SELECT title FROM {node} WHERE nid=?", 1);
     *
     *
     * @param sql SQL query to be executed, use {} for table names
     * @param params Parameters to complete the SQL query
     * @return One value object
     */
    public Object queryValue(String sql, Object... params) throws DSiteException {
        QueryRunner q = new QueryRunner(dataSource);
        try {
            return q.query(d(sql), new ScalarHandler(), params);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new DSiteException(e);
        }
    }

    /**
     * Simply run Drupal database queries, returning a list of arrays instead of maps.
     *
     *
     * @param sql SQL query to be executed, use {} for table names
     * @param params Parameters to complete the SQL query
     * @return A list of arrays
     */
    public List<Object[]> queryArray(String sql, Object... params) throws DSiteException {
        QueryRunner q = new QueryRunner(dataSource);
        try {
            return q.query(d(sql), new ArrayListHandler(), params);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new DSiteException(e);
        }
    }


    /**
     * Run Drupal database update queries (UPDATE, DELETE, INSERT) without using d(). e.g., query("UPDATE {node} SET sticky=1 WHERE type=?", "forum");
     *
     *
     * @param sql SQL update query to be executed, use {} for table names.
     * @param params parameters to complete the SQL query
     * @return number of rows affected
     */
    public int update(String sql, Object... params) throws DSiteException {
        logger.finest("SQL UPDATE: " + sql);
        Connection conn = null;
        // basically this is how DbUtils does the update() using DataSource.
        try {
            QueryRunner q = new QueryRunner();
            conn = dataSource.getConnection();
            int num = q.update(conn, d(sql), params);
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
            return num;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new DSiteException(e);
        } finally {
            DbUtils.closeQuietly(conn);
        }
    }

    /**
     * Insert one record and get the auto-increment ID.
     *
     *
     * @param sql       SQL INSERT statement.
     * @param params    Parameters to complete INSERT
     * @return          auto-increment ID.
     */
    public long insert(String sql, Object... params) throws DSiteException {
        assert(sql.toLowerCase().startsWith("insert"));
        String preparedSql = d(sql);
        logger.finest("SQL INSERT: " + sql);
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(preparedSql, Statement.RETURN_GENERATED_KEYS);
            QueryRunner q = new QueryRunner();

            q.fillStatement(stmt, params);
            stmt.executeUpdate();
            // get auto generated keys
            rs = stmt.getGeneratedKeys();
            rs.next();
            // commit
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
            return rs.getLong(1);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new DSiteException(e);
        } finally {
            DbUtils.closeQuietly(conn, stmt, rs);
        }
    }

    /**
     * Run batch database update.
     *
     *
     * @param sql       SQL update query to be executed, use {} for table names.
     * @param params    params parameters to complete the SQL query
     * @return          number of rows affected in each batch.
     */
    public int[] batch(String sql, Object[][] params) throws DSiteException {
        logger.finest("SQL BATCH: " + sql);
        logger.finest("Number of rows in batch: " + params.length);
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            int[] num = batch(conn, sql, params);
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
            return num;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new DSiteException(e);
        } finally {
            DbUtils.closeQuietly(conn);
        }
    }

    /**
     * Run batch with maxBatchSize at a time.
     *
     *
     * @param conn      Database connection. Usually given by another batch()
     * @param sql       SQL update query to be executed, use {} for table names.
     * @param params    params params parameters to complete the SQL query
     * @return          Number of rows affected in each batch.
     */
    private int[] batch(Connection conn, String sql, Object[][] params) throws DSiteException {
        QueryRunner q = new QueryRunner();
        String processedSql = d(sql);
        // fix slow problem [#1185100]
        try {
            if (maxBatchSize > 0) {
                int start = 0;
                int end = 0;
                int count;
                int[] num = new int[params.length];
                do {
                    end += maxBatchSize;
                    if (end > params.length) {
                        end = params.length;
                    }
                    // run batch query
                    logger.finest("Database batch processing: " + start + " to " + end);
                    int[] batchNum =  q.batch(conn, processedSql, Arrays.copyOfRange(params, start, end));
                    for (count=0; count < batchNum.length; count++) {
                        num[start+count] = batchNum[count];
                    }
                    start = end;
                } while (end < params.length);
                return num;
            } else {
                logger.finest("Batch processing all.");
                return q.batch(conn, processedSql, params);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new DSiteException(e);
        }
    }


    /**
     * This is thread that upload data through JDBC concurrently.
     * attention: could submit this back to commons.dbutils for reuse.
     */
    public static class AsyncBatchRunner extends Thread {

        private static Logger logger = DUtils.getInstance().getPackageLogger();
        private final BlockingQueue<Object[]> queue = new LinkedBlockingQueue<Object[]>();
        private final Connection connection;
        private final PreparedStatement preparedSql;
        private int batchSize;

        // check whether to use AtomicBoolean. perhaps don't need to as it's synchronized.
        private boolean accomplished = false;

        /**
         * Equivalent to AsyncBatchRunner(null, threadName, connection, sql, 0)
         *
         * @param connection
         * @param sql
         */
        public AsyncBatchRunner(Connection connection, String sql) throws DSiteException {
            this(connection, sql, 0);
        }

        /**
         * Initialize the AsyncBatchRunner.
         *
         * @param connection The connection to the Drupal database.
         * @param sql The full SQL for this AsyncBatchRunner. Use d() to parse any tables enclosed with {}.
         * @param batchSize
         */
        public AsyncBatchRunner(Connection connection, String sql, int batchSize) throws DSiteException {
            super();
            this.connection = connection;
            this.batchSize = batchSize;
            try {
                this.preparedSql = this.connection.prepareStatement(sql);
            } catch (SQLException e) {
                throw new DSiteException(e);
            }
        }

        synchronized public void put(Object... row) {
            try {
                queue.put(row);
            } catch (InterruptedException e) {
                throw new DRuntimeException(e);
            } finally {
                notifyAll();
            }
        }

        synchronized public void accomplish() {
            accomplished = true;
            notifyAll();
        }

        @Override
        synchronized public void run() {
            QueryRunner qr = new QueryRunner(); // this is a dummy so we can use fillStatement().
            List<Object[]> rows = new ArrayList<Object[]>();
            int num = 0;
            int index = 0;

            while (true) {
                // retrieve items and save them.
                num = queue.drainTo(rows);
                if (num > 0) {
                    logger.finest("Processing " + num + " rows in AsyncBatchRunner " + getName());
                    try {
                        for (index = 0; index < num; index++) {
                            qr.fillStatement(preparedSql, rows.get(index));
                            preparedSql.addBatch();
                            // periodically execute batch
                            if ((batchSize > 0) && (index > 0) && (index % batchSize == 0)) {
                                preparedSql.executeBatch();
                            }
                        }
                        // finally execute batch for the rest of the items.
                        preparedSql.executeBatch();
                        rows.clear();
                    } catch (SQLException e) {
                        throw new DRuntimeException("Unexpected SQL error.", e);
                    }
                }

                // test if accomplished or not.
                if (accomplished) {
                    break;
                } else {
                    //logger.finest("No rows in queue. Wait.");
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        throw new DRuntimeException("Unexpected thread error.", e);
                    }
                }
            }
            DbUtils.closeQuietly(preparedSql);
            logger.fine("Finished processing AsyncBatchRunner: " + getName());
        }
    }


    public static class UnitTest {

        private DDatabase db;

        @Before
        public void setUp() throws Exception {
            DConfig config = new DConfig();
            config.setProperty("drupal.drush", "drush @local");
            db = new DDatabase(config.getDbProperties());
            db.update("CREATE TABLE {computing_batch_runner} (id INT(10) NOT NULL)");
        }

        @After
        public void tearDown() throws Exception {
            db.update("DROP TABLE {computing_batch_runner}");
            db.close();
        }


        @Test
        public void testQuery() throws Exception {
            long uid = (Long) db.queryValue("SELECT uid FROM {users} WHERE uid=1");
            assertTrue(uid == 1L);
            Object result = db.queryValue("SELECT uid FROM {users} WHERE uid=-1");
            assertNull(result);
            Object o = db.queryValue("SELECT nid FROM {node} WHERE nid=0");
            assertNull(o);
            long i = (Long) db.queryValue("SELECT COUNT(*) FROM {variable}");
            assertTrue(i > 0);

            // test query array
            List<Object[]> lst = db.queryArray("SELECT uid, name FROM {users} WHERE uid < ?", 5);
            for (Object[] row : lst) {
                assertEquals(row.length, 2);
                System.out.println("" + row[0] + " : " + row[1]);
            }
        }

        @Test
        public void testAutoIncrement() throws Exception {
            long max = (Long) db.queryValue("SELECT max(rid) FROM {role}");
            long r1 = db.insert("INSERT INTO {role}(name, weight) VALUE(?, ?)", "test1", 0);
            assertTrue(r1 > max);
            long r2 = db.insert("INSERT INTO {role}(name, weight) VALUE(?, ?)", "test2", 0);
            assertTrue(r2 - r1 == 1);
            Object[][] params = {{r1}, {r2}};
            db.batch("DELETE FROM {role} WHERE rid=?", params);
            long r3 = (Long) db.queryValue("SELECT max(rid) FROM {role}");
            assertEquals(r3, max);
        }


        @Test
        public void testColumnLabel() throws Exception {
            List<Map<String, Object>> results = db.query("SELECT uid FROM {users} WHERE uid=1");
            assertTrue(results.size() == 1);
            long uid = (Long) results.get(0).get("uid");
            assertEquals(1, uid);

            results = db.query("SELECT uid AS id FROM {users} WHERE uid=1");
            assertTrue(results.size() == 1);
            uid = (Long) results.get(0).get("id");
            assertEquals(1, uid);
            assertTrue(!results.get(0).containsKey("uid"));
        }


        @Test
        public void testBatchRunner() throws Exception {
            Connection connection = this.db.getConnection();
            AsyncBatchRunner batch = new AsyncBatchRunner(connection,
                    this.db.d("INSERT INTO {computing_batch_runner} VALUES(?)"), 10);
            batch.start();
            for (int i = 0; i < 100; i++) {
                batch.put(i);
            }
            batch.accomplish();
            System.out.println("Finished adding data to database.");
            batch.join();
            System.out.println("Finished uploading.");
            long n1 = DUtils.getInstance().getLong(this.db.queryValue("SELECT count(*) FROM {computing_batch_runner}"));
            Assert.assertEquals(100L, n1);
            long n2 = DUtils.getInstance().getLong(this.db.queryValue("SELECT count(*) FROM {computing_batch_runner} WHERE id < 50"));
            Assert.assertEquals(50L, n2);
        }

    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }
}
