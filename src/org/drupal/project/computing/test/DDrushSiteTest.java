package org.drupal.project.computing.test;

import com.google.gson.JsonElement;
import org.apache.commons.lang3.RandomStringUtils;
import org.drupal.project.computing.DDrushSite;
import org.drupal.project.computing.DRecord;
import org.drupal.project.computing.DSiteException;
import org.drupal.project.computing.DUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class DDrushSiteTest {

    private DDrushSite site;

    @Before
    public void setUp() {
        //site = new DDrushSite(new DUtils.Drush("drush", "d7"));
        DUtils.getInstance().getPackageLogger().setLevel(Level.ALL);
        site = DDrushSite.loadDefault();
    }

    @Test
    public void testBasic() throws DSiteException {
        assertTrue(site.checkConnection());

        String version = site.getDrupalVersion();
        System.out.println(version);
        assertTrue(version.startsWith("7"));

        Long timestamp1 = site.getTimestamp();
        System.out.println(timestamp1);
        Long timestamp2 = site.getTimestamp();
        assertTrue(timestamp1 <= timestamp2);
    }

    @Test
    public void testVariable() throws DSiteException {
        // test string variables
        site.variableSet("computing_test", "hello, world");
        assertEquals("hello, world", site.variableGet("computing_test", ""));
        assertEquals("N/A", site.variableGet("computing_test_1", "N/A"));

        // test integer variables
        site.variableSet("computing_test", 1);
        assertEquals(1, ((Number) site.variableGet("computing_test", 1)).intValue());
        assertEquals(2, ((Number) site.variableGet("computing_test_1", 2)).intValue());
        site.variableSet("computing_test", 0);
        assertEquals(0, ((Number) site.variableGet("computing_test", 1)).intValue());

        site.getDrush().execute(new String[]{"vdel", "computing_test", "--exact", "--yes"});
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

    //@Test
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

    //@Test
    public void testLoad() throws DSiteException {
        //DRecord r1 = site.loadRecord(6);
        //System.out.println(r1.toString());
        List<DRecord> records = site.queryReadyRecords("common");
        for (DRecord r : records) {
            //System.out.println(r.toString());
        }
    }

    //@Test
//    public void testUpdate() throws DSiteException {
//        DRecord r0 = createRecord();
//        assertTrue(!r0.isSaved());
//
//        long id = site.createRecord(r0);
//        DRecord r1 = site.loadRecord(id);
//        assertTrue(r1.isSaved());
//        assertTrue(r1.isActive());
//
//        r1.setStatus(DRecord.Status.RUNG);
//        r1.setControl(DRecord.Control.REDY);
//        r1.setMessage("Hello,world.");
//        r1.setId3(101L);
//        //r1.writeOutput("");   // output has problem for now.
//        site.updateRecord(r1);
//
//        DRecord r2 = site.loadRecord(id);
//        assertEquals(DRecord.Status.RUNG, r2.getStatus());
//        assertEquals(DRecord.Control.REDY, r2.getControl());
//        //assertEquals("", new String(r2.getOutput()));
//        assertEquals(101L, (long)r2.getId3());
//
//        r2.setString1("Hello, world");
//        r2.setStatus(DRecord.Status.OKOK);
//        site.updateRecordField(r2, "string1");
//        DRecord r3 = site.loadRecord(id);
//        assertEquals("Hello, world", r3.getString1());
//        // since we only update the field, status is still RUNG rather than OKOK.
//        assertEquals(DRecord.Status.RUNG, r3.getStatus());
//    }

}