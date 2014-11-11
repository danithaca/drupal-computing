package org.drupal.project.computing.test;

public class DConfigTest {

//    @Test
//    public void testDbProperties() throws Exception {
//        DConfig config = new DConfig();
//        config.setProperty("drupal.db.username", "scott");
//        config.setProperty("drupal.db.password", "tiger");
//
//        // test extractDbProperties()
//        Properties p1 = config.extractDbProperties(config.properties);
//        assertEquals("scott", p1.getProperty("username"));
//        assertEquals("tiger", p1.getProperty("password"));
//
//        // test convertDbProperties()
//        p1.setProperty("driver", "pgsql");
//        p1.setProperty("database", "test");
//        p1.setProperty("host", "localhost");
//        config.convertDbProperties(p1);
//        assertEquals("jdbc:postgresql://localhost/test", p1.getProperty("url"));
//        assertEquals("org.postgresql.Driver", p1.getProperty("driverClassName"));
//
//        // test getDbPropertiesFromDrush()
//        config.setProperty("drupal.drush", "drush @dev");
//        Properties p2 = config.getDbPropertiesFromDrush(null);
//        assertEquals("com.mysql.jdbc.Driver", p2.getProperty("driverClassName"));
//        // make sure this exists.
//        assertEquals("jdbc:mysql://localhost/openturk2", p2.getProperty("url"));
//        assertTrue(StringUtils.isNotBlank(p2.getProperty("password")));
//
//        // test getDbPropertiesFromSettings()
//        //config.properties.remove("drupal.drush");
//        //config.setProperty("drupal.settings.file", "/tmp/settings.php");
//        config.setProperty("drupal.drush", "drush @local");   // should be able to find settings.php
//        Properties p3 = config.getDbPropertiesFromSettings(null);
//        assertEquals("jdbc:mysql://localhost/d7dev1", p3.getProperty("url"));
//
//        // test overall.
//        Properties dbProperties = config.getDbProperties();
//        assertEquals("scott", dbProperties.getProperty("username"));
//        assertEquals("com.mysql.jdbc.Driver", dbProperties.getProperty("driverClassName"));
//
//        // test db identifier
//        Properties extraDbProperties = config.getDbProperties("computing-extra");
//        assertEquals("scott", extraDbProperties.getProperty("username"));
//        assertEquals(DDatabase.DatabaseDriver.PGSQL.getJdbcDriver(), extraDbProperties.getProperty("driverClassName"));
//    }
//
//    @Test
//    public void testMisc() throws Exception {
//        DConfig config = new DConfig();
//
//        config.setProperty("drupal.php", "php-cgi");
//        assertEquals("php-cgi", config.getPhpExec());
//        config.properties.remove("drupal.php");
//        assertEquals("php", config.getPhpExec());
//
//        config.setProperty("drupal.drush", "drush @local");
//        assertEquals("drush @local", config.getDrushExec());
//    }
//
//    @Test
//    public void testDrupalRoot() throws Exception {
//        DConfig config = new DConfig();
//
//        config.setProperty("drupal.root", "/Users/danithaca/Development/drupal7");
//        assertEquals("/Users/danithaca/Development/drupal7", config.getDrupalRoot().getAbsolutePath());
//
//        // test using drush to get drupal root
//        config.properties.remove("drupal.root");
//        config.setProperty("drupal.drush", "drush @local");
//        try {
//            assertEquals("/Users/danithaca/Development/drupal7", config.getDrupalRoot().getAbsolutePath().trim());
//        } catch (DConfigException e) {
//            //e.printStackTrace();
//            fail("Drush execution problem.");
//        }
//
//        // using remote drush site would not get drupal root.
//        config.setProperty("drupal.drush", "drush @dev");
//        try {
//            config.getDrupalRoot();
//            fail("Remote drupal site returns no drupal root."); // we expect exception because of
//        } catch (DConfigException e) {
//            assertTrue(true);
//        }
//    }
//
//    @Test
//    public void testLocateFile() throws Exception {
//        DConfig config = new DConfig();
//        config.setProperty("drupal.drush", "drush @local");
//
//        assertEquals("/Users/danithaca/Development/drupal7/sites/d7dev1.localhost/settings.php", config.locateFile("settings.php").getAbsolutePath());
//        assertEquals("/Users/danithaca/Development/drupal7/sites/default/default.settings.php", config.locateFile("default.settings.php").getAbsolutePath());
//        assertEquals("/Users/danithaca/.profile", config.locateFile(".profile").getAbsolutePath());
//
//        try {
//            config.locateFile("abc");
//            fail("The file does not exists");
//        } catch (FileNotFoundException e) {
//            assertTrue(true);
//        }
//    }
//
//    @Test
//    public void testDbPropertiesExtra() throws Exception {
//        DConfig config = new DConfig();
//        config.setProperty("drupal.drush", "drush @local");
//        config.setProperty("drupal.db.prefix", "computing");
//        config.setProperty("drupal.db.max_batch_size", "500");
//
//        assertEquals("computing", config.getDbPrefix());
//        assertEquals(500, config.getMaxBatchSize());
//    }

}
