package org.drupal.project.computing.test;

import junit.framework.Assert;
import org.drupal.project.computing.DRecord;
import org.drupal.project.computing.DServicesSite;
import org.drupal.project.computing.exception.DConfigException;
import org.drupal.project.computing.exception.DNotFoundException;
import org.drupal.project.computing.exception.DSiteException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by daniel on 10/7/14.
 */
public class DServicesSiteTest {

    private DServicesSite site;

    @Before
    public void setUp() throws DConfigException {
        site = DServicesSite.loadDefault();
    }

    @Test
    public void testBasic() throws DSiteException {
        String version = site.getDrupalVersion();
        assertEquals('7', version.charAt(0));
        Long time = site.getTimestamp();
        assertTrue(time > 0);
    }

    @Test
    public void testExtended() throws DSiteException {
        assertEquals("standard", site.variableGet("install_profile", "abc"));
        assertEquals("abc", site.variableGet("install_profile1", "abc"));

        site.variableSet("install_profile", "abc");
        assertEquals("abc", site.variableGet("install_profile", "abc"));
        site.variableSet("install_profile", "standard");
        assertEquals("standard", site.variableGet("install_profile", "abc"));
    }

    //@Test
    public void testCRUD1() throws DSiteException {
        DRecord r1 = site.loadRecord(1);
        System.out.println(r1.toJson());
        assertEquals((Long) 1L, r1.getId());
    }

    @Test
    public void testCRUD2() throws DSiteException {
        // test create
        Bindings input = new SimpleBindings();
        input.put("message", "hello, world");
        input.put("test", 1);
        DRecord record = new DRecord("default", "Echo", "Test Echo", input);
        record.setWeight(2L);
        System.out.println("Created record: " + record.toJson());

        long recordId = site.createRecord(record);
        Assert.assertTrue(recordId > 0);
        DRecord r2 = site.loadRecord(recordId);
        assertEquals((Long) 2L, r2.getWeight());

        r2.setWeight(3L);
        site.updateRecord(r2);
        DRecord r3 = site.loadRecord(recordId);
        assertEquals((Long) 3L, r3.getWeight());

        r3.setWeight(4L);
        site.updateRecordField(r3, "weight");
        DRecord r4 = site.loadRecord(recordId);
        assertEquals((Long) 4L, r4.getWeight());

        r4.setOutput(input);
        site.updateRecordField(r4, "output");
        DRecord r5 = site.loadRecord(recordId);
        assertEquals("hello, world", r5.getOutput().get("message"));
    }

    @Test
    public void testCRUD3() throws DSiteException {
        Bindings input = new SimpleBindings();
        input.put("message", "hello, world");
        input.put("test", 1);
        DRecord record = new DRecord("unittest", "Echo", "Test Echo", input);
        record.setWeight(2L);
        System.out.println("Created record: " + record.toJson());

        long recordId = site.createRecord(record);
        Assert.assertTrue(recordId > 0);

        try {
            DRecord r2 = site.claimRecord("unittest");
            assertEquals((Long) 2L, r2.getWeight());
            r2.setMessage("Done");
            site.finishRecord(r2);
            DRecord r4 = site.loadRecord(recordId);
            assertEquals("Done", r4.getMessage());
        } catch (DNotFoundException e) {
            assertTrue(false);
        }

        try {
            DRecord r3 = site.claimRecord("unittest");
            assertTrue(false);
        } catch (DNotFoundException e) {
            assertTrue(true);
        }
    }

    @After
    public void tearDown() throws DSiteException {
        site.close();
    }

}
