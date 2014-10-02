package org.drupal.project.computing.test;

import org.drupal.project.computing.DRecord;
import org.junit.Test;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DRecordTest {

    @Test
    public void testConversion() {
        DRecord record = new DRecord();
        record.setId(1L);
        record.setApplication("default");
        record.setCommand("Echo");

        Bindings input = new SimpleBindings();
        input.put("message", "hello, world");
        input.put("test", 1);
        record.setInput(input);
        record.setOutput(input);

        String json = record.toJson();
        System.out.println(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));

        DRecord rebuild = DRecord.fromJson(json);
        assertEquals(new Long(1), rebuild.getId());
        assertEquals("default", rebuild.getApplication());
        assertEquals("Echo", rebuild.getCommand());
        assertNull(rebuild.getMessage());
        assertEquals("hello, world", rebuild.getInput().get("message"));
        assertEquals(1, rebuild.getOutput().get("test"));
    }
}
