package org.drupal.project.computing.test;

import org.drupal.project.computing.DRecord;
import org.junit.Test;

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

        String json = record.toJson();
        System.out.println(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));

        DRecord rebuild = DRecord.fromJson(json);
        assertEquals(new Long(1), rebuild.getId());
        assertEquals("default", rebuild.getApplication());
        assertEquals("Echo", rebuild.getCommand());
        assertNull(rebuild.getMessage());
    }
}
