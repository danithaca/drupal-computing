package org.drupal.project.computing;

import com.google.gson.JsonParseException;
import org.apache.commons.lang3.StringUtils;
import org.drupal.project.computing.exception.DConfigException;
import org.drupal.project.computing.exception.DSiteException;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * This class allows accessing Drupal using the services.module. It requires Drupal REST Sever module, and both HTTP
 * Request and HTTP Response will use Content-Type = application/json. See Drupal Computing documentation about how to
 * configure at the Drupal end.
 */
public class DRestfulJsonServices {

    protected String baseUrl;
    protected String endpoint;
    protected String userName;
    protected String userPass;
    protected String httpUserAgent = "DrupalComputingAgent";
    protected String httpContentType = "application/json";

    protected URL servicesEndpoint;
    protected String servicesSessionToken;

    protected Logger logger = DUtils.getInstance().getPackageLogger();


    public DRestfulJsonServices(String baseUrl, String endpoint, String userName, String userPass) throws IllegalArgumentException {
        assert StringUtils.isNotBlank(baseUrl) && StringUtils.isNotBlank(endpoint) && StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(userPass);

        this.baseUrl = baseUrl;
        this.endpoint = endpoint;
        this.userName = userName;
        this.userPass = userPass;

        try {
            this.servicesEndpoint = new URL(new URL(baseUrl), endpoint);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Malformed URL: " + baseUrl + " and endpoint: " + endpoint, e);
        }

        // set cookie support.
        // this is required to maintain sessions among different calls.
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    /**
     * Use the settings in config file and create access to Drupal services.
     *
     * @return the DRestfulJsonServices object.
     * @throws DConfigException
     */
    public static DRestfulJsonServices loadDefault() throws DConfigException {
        DConfig config = DConfig.loadDefault();
        String baseUrl = config.getProperty("dcomp.site.base_url", "");
        String endpoint = config.getProperty("dcomp.services.endpoint", "");
        String userName = config.getProperty("dcomp.services.user.name", "");
        String userPass = config.getProperty("dcomp.services.user.pass", "");

        if (StringUtils.isNotBlank(baseUrl) && StringUtils.isNotBlank(endpoint) && StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(userPass)) {
            return new DRestfulJsonServices(baseUrl, endpoint, userName, userPass);
        } else {
            throw new DConfigException("Access Drupal Services configuration error.");
        }
    }


    /**
     * High level API to make HTTP request and get response in JSON.
     *
     * @param directive Services command, such as CRUD, Actions, Targeted Actions, etc.
     * @param params Data to send in HTTP request.
     * @param method HTTP Request method, e.g. GET, POST, DELETE, etc.
     *
     * @return arsed JSON object, either null, or a Primitive, or a List, or a Bindings.
     * @throws IllegalArgumentException
     * @throws DSiteException
     * @see org.drupal.project.computing.DUtils.Json
     */
    public Object request(String directive, Bindings params, String method) throws IllegalArgumentException, DSiteException {
        String jsonResponse = null;
        method = method.toUpperCase();

        switch (method) {
            case "POST": {
                // construct request url
                String data = (params == null || params.isEmpty()) ? null : DUtils.Json.getInstance().toJson(params);
                jsonResponse = httpRequest(servicesEndpoint.toString() + "/" + directive, data, "POST");
                break;
            }
            case "GET": {
                StringBuilder requestUrl = new StringBuilder();
                requestUrl.append(servicesEndpoint.toString()).append("/").append(directive);
                if (params != null && !params.isEmpty()) {
                    // here we need to construct GET url.
                    Properties urlParams = new Properties();
                    for (String key : params.keySet()) {
                        Object value = params.get(key);
                        // make value into Strings to encode in GET URL.
                        String valueString = (value instanceof String) ? (String) value : DUtils.Json.getInstance().toJson(value);
                        urlParams.put(key, valueString);
                    }
                    requestUrl.append('?').append(DUtils.getInstance().encodeURLQueryParameters(urlParams));
                }
                jsonResponse = httpRequest(requestUrl.toString(), null, "GET");
                break;
            }
            case "PUT": {
                String data = (params == null || params.isEmpty()) ? null : DUtils.Json.getInstance().toJson(params);
                jsonResponse = httpRequest(servicesEndpoint.toString() + "/" + directive, data, "PUT");
                break;
            }
            default:
                // not supported.
                throw new IllegalArgumentException("Request method is not supported: " + method);
        }

        // parse json
        try {
            if (StringUtils.isNotBlank(jsonResponse)) {
                return DUtils.Json.getInstance().fromJson(jsonResponse);
            } else {
                return null;
            }
        } catch (JsonParseException e) {
            e.printStackTrace();
            throw new DSiteException("Cannot parse JSON result: " + jsonResponse, e);
        }
    }


    public <T> T request(String directive, Bindings params, String method, Class<T> classOfT) throws IllegalArgumentException, DSiteException {
        Object result = null;
        try {
            result = request(directive, params, method);
            return classOfT.cast(result);
        } catch (ClassCastException e) {
            e.printStackTrace();
            throw new DSiteException("Unexpected JSON result type: " + result.getClass().getSimpleName() + ". Failure might be caused by the Drupal end.");
        }
    }


    /**
     * Generic template to make a HTTP request.
     *
     * @param url The absolute URL to make request on.
     * @param data Extra data to pass to the URL connection.
     * @param method
     *  a valid HTTP request method: GET, PUT, etc in upper case.
     *  Following HttpURLConnection, this parameter is defined as a String not Enum.
     *
     * @return HTTP response in String from InputSteam (not ErrorStream). Content from ErrorStream will be passed out in DSiteException.
     * @throws IllegalArgumentException
     * @throws org.drupal.project.computing.exception.DSiteException
     */
    public String httpRequest(String url, String data, String method) throws IllegalArgumentException, DSiteException {
        assert method.equals("GET") || method.equals("POST") || method.equals("PUT") || method.equals("DELETE");
        HttpURLConnection connection = null;

        try {
            URL requestUrl = new URL(url);
            // this does not connect to the actual network. URLConnection.connect() does.
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestProperty("User-Agent", httpUserAgent);

            connection.setRequestMethod(method);
            connection.setRequestProperty("Content-Type", httpContentType); // this is the request content-type
            //connection.setRequestProperty("Content-Language", "en-US");

            if (StringUtils.isNotBlank(servicesSessionToken)) {
                connection.setRequestProperty("X-CSRF-Token", servicesSessionToken);
            }

            // Logistics. Will use InputSteam (which is HTTP response).
            connection.setUseCaches (false);
            connection.setDoInput(true);

            if (StringUtils.isNotBlank(data)) {
                //Send request data
                connection.setRequestProperty("Content-Length", "" + Integer.toString(data.getBytes().length));
                connection.setDoOutput(true);
                DataOutputStream dataOutput = new DataOutputStream (connection.getOutputStream());
                dataOutput.writeBytes(data);
                dataOutput.flush();
                dataOutput.close();
            }

            int responseCode = connection.getResponseCode();

            //Get Response, or throw an exception with HTTPCode (other than 200) as error code.
            String responseContent = null;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // seems if responseCode != 200, then this will cause error.
                InputStream responseStream = connection.getInputStream();
                if (responseStream != null) {
                    responseContent = DUtils.getInstance().readContent(responseStream);
                    responseStream.close();
                }
            } else {
                String responseError = null;
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    responseError = DUtils.getInstance().readContent(errorStream);
                }
                StringBuilder errorMessage = new StringBuilder();
                errorMessage.append("HTTP response code is not OK. Code: ").append(responseCode).
                        append(". Message: ").append(responseError).append(". ");
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    errorMessage.append("Possible reason: Services endpoint not enabled.");
                }
                DSiteException e = new DSiteException(errorMessage.toString());
                e.setErrorCode(responseCode);
                throw e;
            }
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
     * Check connection to Drupal using system/connect.json.
     * @return true if connection successful, or false if not.
     */
    public boolean checkConnection() {
        try {
            // we know the result has to be Bindings from system/connect.
            Bindings result = request("system/connect.json", null, "POST", Bindings.class);
            logger.info(DUtils.Json.getInstance().toJson(result));
            return true;
        } catch (DSiteException e) {
            return false;
        }
    }


    /**
     * Use services.module "services/session/token" to obtain a session token.
     *
     * @return The session token.
     * @throws DSiteException
     */
    public String obtainServicesSessionToken() throws DSiteException {
        try {
            // this will not use services endpoint. The URL is builtin by services.module.
            String url = new URL(new URL(baseUrl), "services/session/token").toString();
            return httpRequest(url, null, "GET");
        } catch (MalformedURLException e) {
            // baseUrl should already be verified, and should not generate error.
            throw new IllegalStateException(e);
        }
    }


    /**
     * Connect to Drupal services, and login using the specified user credentials. Set session token too to access
     * Drupal services again.
     */
    public void userLogin() throws DSiteException {
        // seems not necessary to assign a temporary token here.
//        if (StringUtils.isBlank(servicesSessionToken)) {
//            // set a temporary token to verify
//            servicesSessionToken = obtainServicesSessionToken();
//        }

        Bindings params = new SimpleBindings();
        params.put("username", userName);
        params.put("password", userPass);

        Bindings result = request("user/login.json", params, "POST", Bindings.class);
        if (result.containsKey("token") && result.get("token") instanceof String) {
            servicesSessionToken = (String) result.get("token");
            logger.info("Services successfully login with user: " + userName);
        } else {
            logger.severe("Cannot login with user: " + userName);
            throw new DSiteException("Cannot get authentication token.");
        }
    }


    /**
     * Logout Drupal user.
     * @throws DSiteException
     */
    public void userLogout() throws DSiteException {
        // we don't care about the output.
        request("user/logout.json", null, "POST");
        // clear sessionToken anyways.
        servicesSessionToken = null;
        logger.info("Services successfully logout with user: " + userName);
    }

    /**
     * Whether we have logged in or not, as indicated by whether we have a non-empty session token.
     *
     * @return true if we have logged in.
     */
    public boolean isAuthenticated() {
        return StringUtils.isNotBlank(servicesSessionToken);
    }


    public URL getServicesEndpoint() {
        return servicesEndpoint;
    }

    public void setHttpUserAgent(String httpUserAgent) {
        this.httpUserAgent = httpUserAgent;
    }

}
