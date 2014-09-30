package org.drupal.project.computing.test;

import org.apache.commons.exec.CommandLine;
import org.drupal.project.computing.DUtils;
import org.junit.Test;

import java.io.File;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

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

}


