package org.drupal.project.computing.test;

import org.drupal.project.computing.DApplication;
import org.drupal.project.computing.DRecord;
import org.drupal.project.computing.DSite;
import org.drupal.project.computing.DUtils;
import org.drupal.project.computing.common.ComputingApplication;
import org.drupal.project.computing.exception.DSiteException;
import org.junit.Before;
import org.junit.Test;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.logging.Level;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * Test DApplication and DCommand
 */
public class DApplicationTest {
    private DApplication application;
    private DSite site;

    @Before
    public void setUp() {
        DUtils.getInstance().getPackageLogger().setLevel(Level.ALL);
        application = new ComputingApplication();
        site = application.getSite();
    }

    @Test
    public void testExecutionBasic() throws DSiteException {
        // create 2 command
        Bindings input1 = new SimpleBindings();
        input1.put("ping", "hello");
        Bindings input2 = new SimpleBindings();
        input2.put("ping", "ciao");

        DRecord d1 = new DRecord("computing", "Echo", "UnitTest Echo Command", input1);
        Long id1 = site.createRecord(d1);
        DRecord d2 = new DRecord("computing", "Echo", "UnitTest Echo Command", input2);
        Long id2 = site.createRecord(d2);

        // execute commands and make sure things work.
        application.launch();

        d1 = site.loadRecord(id1);
        assertEquals(DRecord.Status.SCF, d1.getStatus());
        assertEquals("hello", (String) d1.getOutput().get("pong"));

        d2 = site.loadRecord(id2);
        assertEquals(DRecord.Status.SCF, d2.getStatus());
        assertEquals("ciao", (String) d2.getOutput().get("pong"));
    }

    @Test
    public void testExecutionFail() throws DSiteException {
        Bindings input1 = new SimpleBindings();
        input1.put("ping", "hello");
        Bindings input2 = new SimpleBindings();
        input2.put("ping", "ciao");

        // create 2 command that's not registered by default, but manually register it.
        DRecord d3 = new DRecord("computing", "Echo2", "UnitTest Echo Command", input1);
        Long id3 = site.createRecord(d3);
        DRecord d4 = new DRecord("computing", "Echo3", "UnitTest Echo Command", input2);
        Long id4 = site.createRecord(d4);

        // make sure the command cannot work.
        application.setCommandMapping("Echo2", "org.drupal.project.computing.common.EchoCommand");
        application.launch();

        d3 = site.loadRecord(id3);
        assertEquals(DRecord.Status.SCF, d3.getStatus());
        assertEquals("hello", (String) d3.getOutput().get("pong"));

        d4 = site.loadRecord(id4);
        assertEquals(DRecord.Status.FLD, d4.getStatus());
    }

    @Test
    public void testRunOnce() throws DSiteException {
        Bindings input1 = new SimpleBindings();
        input1.put("ping", "hello");
        DRecord r = new DRecord("computing", "Echo", "JUnitTest", input1);
        DApplication app = new ComputingApplication();
        DRecord r2 = app.runOnce(r);
        assertTrue(!r2.isNew());
        assertEquals("hello", r2.getOutput().get("pong"));
    }
}
