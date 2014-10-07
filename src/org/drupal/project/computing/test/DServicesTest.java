package org.drupal.project.computing.test;


import org.drupal.project.computing.DServices;
import org.drupal.project.computing.DUtils;
import org.drupal.project.computing.exception.DConfigException;
import org.drupal.project.computing.exception.DSiteException;
import org.junit.Test;

import javax.script.Bindings;

import java.net.HttpURLConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DServicesTest {

    @Test
    public void checkConnection() throws DConfigException, DSiteException {
        DServices services = DServices.loadDefault();
        assertTrue(services.checkConnection());

        String token = services.httpGet("http://d7.dev/services/session/token");
        System.out.println(token);
    }

    @Test
    public void testAuthentication() throws DConfigException, DSiteException {
        DServices services = DServices.loadDefault();
        assertFalse(services.isAuthenticated());
        services.userLogin();
        assertTrue(services.isAuthenticated());
        Bindings data = (Bindings) services.request("system/connect.json", "POST", null);
        System.out.println(DUtils.Json.getInstance().toJson(data));
        int uid =  new Integer((String) ((Bindings) data.get("user")).get("uid"));
        assertTrue(uid > 0);
        try {
            services.userLogin();
            assertTrue(false); // can't login again.
        } catch (DSiteException e) {
            assertEquals(HttpURLConnection.HTTP_NOT_ACCEPTABLE, e.getErrorCode());
        }
        services.userLogout();
    }



}
