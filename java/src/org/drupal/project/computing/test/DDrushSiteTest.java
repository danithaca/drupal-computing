package org.drupal.project.computing.test;

import org.drupal.project.computing.DDrushSite;
import org.drupal.project.computing.DRecord;
import org.drupal.project.computing.DUtils;
import org.drupal.project.computing.exception.DNotFoundException;
import org.drupal.project.computing.exception.DSiteException;
import org.junit.Before;
import org.junit.Test;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.logging.Level;

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


//    private DRecord createRecord() {
//        Map map = new HashMap();
//        map.put("app", "common");
//        map.put("command", "drush");
//        map.put("description", "test from drush " + RandomStringUtils.randomAlphanumeric(10));
//        map.put("id1", new Random().nextInt(10000));
//        map.put("string1", "hello,world");
//        return new DRecord(map);
//    }

    //@Test
//    public void testSave() throws DSiteException {
//        Logger logger = DUtils.getInstance().getPackageLogger();
//        logger.setLevel(Level.FINEST);
//
//        DRecord r0 = createRecord();
//        //System.out.println(r0.toJson());
//        long id = site.createRecord(r0);
//
//        DRecord r1 = site.loadRecord(id);
//        //System.out.println(r1.toString());
//        assertEquals(r0.getId1(), r1.getId1());
//    }


    @Test
    public void testRecordSimple() throws DSiteException {
        Bindings input = new SimpleBindings();
        input.put("message", "hello, world");
        input.put("test", 1);
        DRecord record = new DRecord("default", "Echo", "Test Echo", input);
        System.out.println("Created record: " + record.toJson());

        long recordId = site.createRecord(record);
        assertTrue(recordId > 0);

        DRecord rebuild = site.loadRecord(recordId);
        System.out.println("Reloaded record: " + rebuild.toJson());
        assertEquals(record.getCommand(), rebuild.getCommand());
        assertEquals((Long) recordId, rebuild.getId());
        assertEquals(input.get("message"), rebuild.getInput().get("message"));
        assertTrue(rebuild.getCreated() > 0);

        DRecord r2 = new DRecord("default", "Echo", "Test Echo", null);
        r2.setMessage("test message");
        long r2Id = site.createRecord(r2);
        assertTrue (r2Id > 0);
        DRecord r2r = site.loadRecord(r2Id);
        assertEquals(r2.getMessage(), r2r.getMessage());
    }

    @Test
    public void testRecordUpdate() throws DSiteException {
        // prepare command
        Bindings input = new SimpleBindings();
        input.put("message", "hello, world");
        input.put("test", 1);
        DRecord record = new DRecord("default", "Echo", "Test Echo", input);

        // make sure this will get claimed first.
        record.setWeight(-100L);

        long recordId = site.createRecord(record);
        assertTrue(recordId > 0);

        // test claim
        DRecord cl = null;
        try {
            cl = site.claimRecord("default");
        } catch (DNotFoundException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        assertEquals((Long) (-100L), cl.getWeight());
        assertEquals("hello, world", cl.getInput().get("message"));

        // test update
        cl.setMessage("updated");
        site.updateRecord(cl);
        DRecord cl_a = site.loadRecord(cl.getId());
        assertEquals("updated", cl_a.getMessage());

        // test update field
        cl_a.setOutput(input);
        site.updateRecordField(cl_a, "output");
        DRecord cl_b = site.loadRecord(cl_a.getId());
        assertEquals(1, ((Number) cl_b.getOutput().get("test")).intValue());

        // test finish
        cl_b.setStatus(DRecord.Status.SCF);
        site.finishRecord(cl_b);
        DRecord cl_c = site.loadRecord(cl_b.getId());
        assertEquals(DRecord.Status.SCF, cl_c.getStatus());

        // expected exception. can't update status to a record with status != 'RUN'.
        try {
            site.finishRecord(cl_c);
            assertTrue(false);
        } catch (DSiteException e) {
            assertTrue(true);
        }
    }

    //@Test
    public void testAdHoc() throws DSiteException {
        DRecord re = site.loadRecord(10);
        //System.out.println(re.toJson());
//        DRecord re = null;
//        try {
//            re = site.claimRecord("default");
//            System.out.println(re.toJson());
//        } catch (DNotFoundException e) {
//            System.out.println("Not Found");
//        }
        //System.out.println(re.toJson());
        re.setStatus(DRecord.Status.SCF);
        re.setMessage("Updated record");
        //site.updateRecord(re);

        Bindings output = new SimpleBindings();
        output.put("message", "hello, world");
        output.put("test", 1);
        re.setOutput(output);
        //site.updateRecordField(re, "output");
        site.finishRecord(re);
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
