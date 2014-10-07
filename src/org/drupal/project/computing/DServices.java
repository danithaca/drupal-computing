package org.drupal.project.computing;

import com.google.gson.JsonParseException;
import org.apache.commons.lang3.StringUtils;
import org.drupal.project.computing.exception.DConfigException;
import org.drupal.project.computing.exception.DSiteException;

import javax.script.Bindings;
import java.io.*;
import java.net.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * This class helps connect to Drupal using the services.module.
 * Requires Drupal REST Sever module enabled.
 */
public class DServices {

    protected String baseUrl;
    protected String endpoint;
    protected String userName;
    protected String userPass;
    protected String userAgent = "DrupalComputingAgent";

    protected URL servicesEndpoint;
    protected String servicesSessionToken;

    protected Logger logger = DUtils.getInstance().getPackageLogger();


    public DServices(String baseUrl, String endpoint, String userName, String userPass) throws IllegalArgumentException {
        assert StringUtils.isNotBlank(baseUrl) && StringUtils.isNotBlank(endpoint) && StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(userPass);

        this.baseUrl = baseUrl;
        this.endpoint = endpoint;
        this.userName = userName;
        this.userPass = userPass;

        try {
            this.servicesEndpoint = new URL(new URL(baseUrl), endpoint);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Mal-format URL: " + baseUrl + " and endpoint: " + endpoint, e);
        }

        // set cookie support.
        // this is required to maintain sessions among different calls.
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    public static DServices loadDefault() throws DConfigException {
        DConfig config = DConfig.loadDefault();
        String baseUrl = config.getProperty("dc.site.base_url", "");
        String endpoint = config.getProperty("dc.services.endpoint", "");
        String userName = config.getProperty("dc.services.user.name", "");
        String userPass = config.getProperty("dc.services.user.pass", "");

        if (StringUtils.isNotBlank(baseUrl) && StringUtils.isNotBlank(endpoint) && StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(userPass)) {
            return new DServices(baseUrl, endpoint, userName, userPass);
        } else {
            throw new DConfigException("Access Drupal Services configuration error.");
        }
    }


    /**
     * This is a lightweight method to access Drupal REST Server without using Apache HttpClient.
     * Use only REST Server. Use other servers might lead to unexpected error.
     *
     * @return Parsed JSON object, either null, or a Primitive, or an Array, or a Binding.
     * @see org.drupal.project.computing.DUtils.Json
     */
    public Object request(String requestString, String requestMethod, Properties params) throws IllegalArgumentException, DSiteException {
        String jsonResponse = null;
        requestMethod = requestMethod.toUpperCase();

        // encode params.
        String urlParams = (params != null && !params.isEmpty()) ? DUtils.getInstance().encodeURLQueryParameters(params) : null;

        // we won't do too much reusable code to keep this flexible
        if (requestMethod.equals("POST")) {
            // construct request url
            jsonResponse = httpPost(servicesEndpoint.toString() + "/" + requestString, urlParams);

        } else if (requestMethod.equals("GET")) {
            StringBuilder requestUrl = new StringBuilder();
            requestUrl.append(servicesEndpoint.toString()).append("/").append(requestString);
            if (StringUtils.isNotBlank(urlParams)) {
                requestUrl.append('?').append(urlParams);
            }
            jsonResponse = httpGet(requestUrl.toString());

        } else {
            // not supported.
            throw new IllegalArgumentException("Request method is not supported: " + requestMethod);
        }

        // parse json
        try {
            if (StringUtils.isNotBlank(jsonResponse)) {
                return DUtils.Json.getInstance().fromJson(jsonResponse);
            } else {
                return null;
            }
        } catch (JsonParseException e) {
            throw new DSiteException("Cannot parse JSON result.", e);
        }
    }


    public String httpGet(String url) throws IllegalArgumentException, DSiteException {
        HttpURLConnection connection = null;

        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();

            // this does not connect to the actual network. URLConnection.connect() does.
            connection = (HttpURLConnection) requestUrl.openConnection();
            // handle GET. no need to set Content-Type per http://stackoverflow.com/questions/5661596/do-i-need-a-content-type-for-http-get-requests
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", userAgent);

            if (StringUtils.isNotBlank(servicesSessionToken)) {
                connection.setRequestProperty("X-CSRF-Token", servicesSessionToken);
            }

            connection.setDoInput(true);

            // Quote from URLConnection::connect(): Operations that depend on being connected, like getContentLength,
            // will implicitly perform the connection, if necessary.
            // That means, this will make connection if not yet.
            int responseCode = connection.getResponseCode();

            //Get Response
            InputStream dataInput = (responseCode == HttpURLConnection.HTTP_OK) ? connection.getInputStream() : connection.getErrorStream();
            String responseContent = DUtils.getInstance().readContent(dataInput);
            dataInput.close();

            checkResponseCode(responseCode, responseContent);
            return responseContent;

        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DSiteException(e);
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
    }


    public String httpPost(String url, String urlParams) throws IllegalArgumentException, DSiteException {
        HttpURLConnection connection = null;

        try {
            URL requestUrl = new URL(url);
            // this does not connect to the actual network. URLConnection.connect() does.
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestProperty("User-Agent", userAgent);

            // handel post
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            //connection.setRequestProperty("Content-Language", "en-US");

            if (StringUtils.isNotBlank(servicesSessionToken)) {
                connection.setRequestProperty("X-CSRF-Token", servicesSessionToken);
            }

            connection.setUseCaches (false);
            connection.setDoInput(true);

            if (StringUtils.isNotBlank(urlParams)) {
                connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParams.getBytes().length));
                connection.setDoOutput(true);
                //Send request
                DataOutputStream dataOutput = new DataOutputStream (connection.getOutputStream ());
                dataOutput.writeBytes(urlParams);
                dataOutput.flush();
                dataOutput.close();
            }

            int responseCode = connection.getResponseCode();

            //Get Response.
            // TODO: seems if responseCode != 200, then this will cause error.
            InputStream dataInput = (responseCode == HttpURLConnection.HTTP_OK) ? connection.getInputStream() : connection.getErrorStream();
            String responseContent = DUtils.getInstance().readContent(dataInput);
            dataInput.close();


            checkResponseCode(responseCode, responseContent);
            return responseContent;

        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DSiteException(e);
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
    }


    /**
     * Make sure responseCode is 200, or else throw the exception.
     *
     * @param responseCode
     * @param responseContent Option text if responseCode is not 200.
     * @throws DSiteException
     */
    protected void checkResponseCode(int responseCode, String responseContent) throws DSiteException {
        if (responseCode != HttpURLConnection.HTTP_OK) {
            DSiteException e = new DSiteException("HTTP response code is not OK. Code: " + responseCode + ". Message: " + responseContent);
            e.setErrorCode(responseCode);
            throw e;
        }
    }


    public boolean checkConnection() {
        try {
            // we know the result has to be Bindings from system/connect.
            Bindings result = (Bindings) request("system/connect.json", "POST", null);
            logger.info(DUtils.Json.getInstance().toJson(result));
            return true;
        } catch (DSiteException e) {
            return false;
        }
    }


    public String obtainServicesSessionToken() throws DSiteException {
        try {
            String url = new URL(new URL(baseUrl), "services/session/token").toString();
            return httpGet(url);
        } catch (MalformedURLException e) {
            // baseUrl should already be verified, and should not generate error.
            throw new IllegalStateException(e);
        }
    }


    /**
     * Connect to Drupal services, and
     */
    public void userLogin() throws DSiteException {
        // seems not necessary to assign a temporary token here.
//        if (StringUtils.isBlank(servicesSessionToken)) {
//            // set a temporary token to verify
//            servicesSessionToken = obtainServicesSessionToken();
//        }

        Properties params = new Properties();
        params.put("username", userName);
        params.put("password", userPass);

        Bindings result = (Bindings) request("user/login.json", "POST", params);
        if (result.containsKey("token") && result.get("token") instanceof String) {
            servicesSessionToken = (String) result.get("token");
            logger.info("Services successfully login with user: " + userName);
        } else {
            logger.severe("Cannot login with user: " + userName);
            throw new DSiteException("Cannot get authentication token.");
        }
    }


    public void userLogout() throws DSiteException {
        // we don't care about the output.
        request("user/logout.json", "POST", null);
        // clear sessionToken anyways.
        servicesSessionToken = null;
        logger.info("Services successfully logout with user: " + userName);
    }


    public boolean isAuthenticated() {
        return StringUtils.isNotBlank(servicesSessionToken);
    }


    public URL getServicesEndpoint() {
        return servicesEndpoint;
    }


}
