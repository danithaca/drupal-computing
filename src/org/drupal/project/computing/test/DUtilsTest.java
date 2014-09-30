package org.drupal.project.computing.test;

import com.google.gson.Gson;
import org.apache.commons.exec.CommandLine;
import org.drupal.project.computing.DUtils;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import static junit.framework.Assert.*;
import static junit.framework.Assert.assertEquals;

public class DUtilsTest {

    @Test
    public void testExecuteShell() throws Exception {
        String result = DUtils.getInstance().executeShell("echo Hello").trim();
        // System.out.println(result);
        assertEquals(result, "Hello");

        DUtils.getInstance().executeShell("touch /tmp/computing.test");
        assertTrue((new File("/tmp/computing.test")).exists());
        DUtils.getInstance().executeShell("rm /tmp/computing.test");
        assertTrue(!(new File("/tmp/computing.test")).exists());

        DUtils.getInstance().executeShell(CommandLine.parse("touch computing.test"), new File("/tmp"), null);
        assertTrue((new File("/tmp/computing.test")).exists());
        DUtils.getInstance().executeShell("rm /tmp/computing.test");
        assertTrue(!(new File("/tmp/computing.test")).exists());

        String msg = DUtils.getInstance().executeShell(CommandLine.parse("cat"), null, "hello\u0004");    // Ctrl+D, end of stream.
        assertEquals("hello", msg.trim());

        // this shows input stream automatically sends Ctrl+D.
        msg = DUtils.getInstance().executeShell(CommandLine.parse("cat"), null, "hello, world");
        assertEquals("hello, world", msg.trim());

            /*CommandLine commandLine = CommandLine.parse("drush @dev1");
           commandLine.addArgument("php-eval");
           commandLine.addArgument("echo 'Hello';", true);
           System.out.println(commandLine.toString());
           commandLine.addArgument("echo drupal_json_encode(eval('return node_load(1);'));", true);
           System.out.println(DUtils.getInstance().executeShell(commandLine, null));*/
    }


    @Test
    public void testDrush() throws Exception {
        // test drush version
        DUtils.Drush drush = DUtils.Drush.loadDefault();
        assertEquals("Expected drush version", "6.2.0", drush.getVersion());

        // test drupal version
        Map<String, Object> coreStatus = drush.getCoreStatus();
        System.out.println(coreStatus.toString());
        assertNotNull(coreStatus);
        String drupalVersion = (String) coreStatus.get("drupal-version");
        assertNotNull(drupalVersion);
        assertTrue("Expected drupal version", drupalVersion.startsWith("7"));

        // test computing-eval
        String jsonStr = drush.computingEval("return node_load(1);").trim();
        System.out.println(jsonStr);
        assertTrue(jsonStr.startsWith("{") && jsonStr.endsWith("}"));
        Map<String, Object> jsonObj = (Map<String, Object>) DUtils.Json.getInstance().fromJson(jsonStr);
        String nidStr = (String) jsonObj.get("nid");
        assertEquals("1", nidStr);
        assertEquals(new Integer(1), new Integer(nidStr));

        jsonStr = drush.computingEval("node_load(1);").trim();
        System.out.println(jsonStr);
        assertEquals("null", jsonStr);

        // test computing call
        String s2 = drush.computingCall(new String[]{"variable_get", DUtils.Json.getInstance().toJson("install_profile")});
        System.out.println(s2);
        assertEquals("standard", (String) DUtils.Json.getInstance().fromJson(s2));
        String s3 = drush.computingCall("variable_get", "install_profile");
        System.out.println(s3);
        assertEquals("standard", DUtils.Json.getInstance().fromJson(s3));

        // test exception
        try {
            drush.computingCall("hello");
            assertTrue(false);
        } catch (Exception e) {
            assertTrue("Expected exception caught.", true);
        }
        try {
            DUtils.Drush badDrush = new DUtils.Drush("drush", "@xxx");
            badDrush.computingCall("variable_get", "install_profile");
            assertTrue(false);
        } catch (Exception e) {
            assertTrue("Expected exception caught.", true);
        }
    }

//    @Test
//    public void testLocalDrush() throws Exception {
//        // test local environment
//        Drush drush = new Drush("drush @local");
//        Properties coreStatus = drush.getCoreStatus();
//        assertTrue(coreStatus.getProperty("drupal_version").startsWith("7"));
//        assertEquals("/Users/danithaca/Development/drupal7", coreStatus.getProperty("drupal_root"));
//        assertEquals("/Users/danithaca/Development/drupal7", drush.execute(new String[]{"drupal-directory", "--local"}).trim());
//    }

    @Test
    public void testLogger() {
        Logger l = DUtils.getInstance().getPackageLogger();
        assertTrue(l != null);
        assertTrue(l.getUseParentHandlers());
        assertEquals(0, l.getHandlers().length);

        while (true) {
            if (l.getParent() != null) {
                l = l.getParent();
            } else {
                break;
            }
        }

        // now l is the top level handler
        assertEquals(1, l.getHandlers().length);
        assertTrue(l.getHandlers()[0] instanceof ConsoleHandler);
    }


    @Test
    public void testJson() {
        Map<String, Object> jsonObj = new HashMap<String, Object>();
        jsonObj.put("abc", 1);
        jsonObj.put("hello", "world");
        String jsonString = DUtils.Json.getInstance().toJson(jsonObj);
        Map<String, Object> json1 = (Map<String, Object>) DUtils.Json.getInstance().fromJson(jsonString);
        assertEquals(1, ((Number) json1.get("abc")).intValue());
        assertEquals("world", (String) json1.get("hello"));

        // produce error
        //Gson gson = new Gson();
        //Integer jsonObj = 1;
        //String jsonStr = gson.toJson(jsonObj);
        //System.out.println(jsonStr);
        // the library doesn't have fromJson(obj), so the library has no problem
        // we have a problem because we want to use fromJson(obj).
        //System.out.println(gson.toJson(gson.fromJson(jsonStr)));


            /*Map<String, Object> oldJson = new HashMap<String, Object>();
            oldJson.put("hello", 1);
            String oldJsonString = DUtils.getInstance().toJson(oldJson);
            Map<String, Object> newJson = new HashMap<String, Object>();
            newJson.put("hello", 1);
            newJson.put("abc", "def");
            String newJsonString = DUtils.getInstance().toJson(newJson);
            assertEquals(newJsonString, DUtils.getInstance().appendJsonString(oldJsonString, "abc", "def"));*/
    }

}


