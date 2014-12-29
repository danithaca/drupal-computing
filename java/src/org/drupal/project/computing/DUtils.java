package org.drupal.project.computing;

import com.google.gson.*;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.drupal.project.computing.exception.DRuntimeException;
import org.drupal.project.computing.exception.DSystemExecutionException;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.io.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Logger;

/**
 * Singleton of utilities class.
 */
public class DUtils {

    ////// Singleton template ////////

    private static DUtils ourInstance = new DUtils();
    public static DUtils getInstance() {
        return ourInstance;
    }
    private DUtils() {
        // TODO: According to JDK doc, LogManager should do it right, but here I'll still do it myself.
        logger = Logger.getLogger("org.drupal.project.computing");
        // logger.setUseParentHandlers(false);
    }

    ///////////////////  code begins /////////////////

    // TODO: use version defined in MANIFEST?
    public final String VERSION = "7.x-2.0-alpha2";

    private Logger logger;
    public Logger getPackageLogger() {
        return logger;
    }


    /**
     * Try to locate file in default locations. Or throw exception if not found.
     *
     * Priority:
     * 1. the working directory,
     * 2. the same directory as the jar file located.
     * 3. user home directory
     *
     * @param fileName
     *   the name of the file to locate. Do not include directory.
     *   If given absolute path as filename, return the file without searching.
     * @return The file object if found.
     * @throws FileNotFoundException
     */
    public File locateFile(String fileName) throws FileNotFoundException {
        assert StringUtils.isNotBlank(fileName);
        File theFile = null;

        // 0. check whether the file is absolute or relative.
        theFile = new File(fileName);
        if (theFile.isAbsolute() && theFile.exists()) {
            return theFile;
        }

        // 1. the working directory,
        String workingDir = System.getProperty("user.dir");
        theFile = new File(workingDir + File.separator + fileName);
        if (theFile.exists()) {
            return theFile;
        }

        // the same directory as the jar file located.
        String jarDir = DUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        theFile = new File(jarDir + File.separator + fileName);
        if (theFile.exists()) {
            return theFile;
        }

        // 3. user home directory
        String userDir = System.getProperty("user.home");
        theFile = new File(userDir + File.separator + fileName);
        if (theFile.exists()) {
            return theFile;
        }

        // if still can't find file, throw exception
        throw new FileNotFoundException("Cannot locate file: " + fileName);
    }


    /**
     * Encode URL query parameters. Basically copied from Apache.HttpClient => URLEncodeUtils.format().
     *
     * @param params parameters to encode
     * @return encoded parameters
     */
    public String encodeURLQueryParameters(Properties params) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> keysIterator = params.stringPropertyNames().iterator();

        while (keysIterator.hasNext()) {
            String key = keysIterator.next();
            try {
                sb.append(URLEncoder.encode(key, "UTF-8")).append('=').append(URLEncoder.encode(params.getProperty(key), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e);
            }
            if (keysIterator.hasNext()) {
                sb.append('&');
            }
        }

        return sb.toString();
    }



    /**
     * Execute a command in the working dir, and return the output as a String. If error, log the errors in logger.
     *
     * @param commandLine The command line object
     * @param workingDir The working directory. Could be null. The it's default user.dir.
     * @param input Input string
     * @return command output.
     */
    public String executeShell(CommandLine commandLine, File workingDir, String input) throws DSystemExecutionException {
        byte[] inputBytes = (input == null) ? null : input.getBytes();
        return new String(executeShell(commandLine, workingDir, inputBytes, Charset.defaultCharset()));
    }

    public String executeShell(CommandLine commandLine, String input) throws DSystemExecutionException {
        return executeShell(commandLine, null, input);
    }

    public String executeShell(CommandLine commandLine) throws DSystemExecutionException {
        return executeShell(commandLine, (String) null);
    }

    public String executeShell(String command) throws DSystemExecutionException {
        logger.finest("Running system command: " + command);
        CommandLine commandLine = CommandLine.parse(command);
        return executeShell(commandLine);  // specifies with executeShell() to call.
    }

    /**
     * This is the underlying System Exec entry
     *
     * @param commandLine The command line to execute.
     * @param workingDir optional working directory. or null to use default.
     * @param input input byte stream. or null.
     * @param charset charset to convert bytes into string.
     * @return output byte stream from command line execution
     * @throws DSystemExecutionException
     */
    public byte[] executeShell(CommandLine commandLine, File workingDir, byte[] input, Charset charset) throws DSystemExecutionException {
        DefaultExecutor executor = new DefaultExecutor();

        // default exit value is 0. need to handle it if needed.
        // see http://commons.apache.org/proper/commons-exec/tutorial.html
        executor.setExitValue(0);

        // handle timeout. default 2 minutes.
        int timeout = new Integer(DConfig.loadDefault().getProperty("dcomp.exec.timeout", "120000"));
        ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
        executor.setWatchdog(watchdog);

        // set working dir
        if (workingDir != null) {
            executor.setWorkingDirectory(workingDir);
        }

        // set in/out/err stream.
        ByteArrayInputStream in = ArrayUtils.isNotEmpty(input) ? new ByteArrayInputStream(input) : null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        executor.setStreamHandler(new PumpStreamHandler(out, err, in));

        // log input.
        logger.finest("Shell command to run: " + commandLine.toString());
        if (ArrayUtils.isNotEmpty(input)) {
            logger.finest("Shell command input stream: " + new String(input, charset));
        }

        // result handler.
        // no need to use it because we don't mind blocking.
        //DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

        try {

            // execute the command line.
            int exitValue = executor.execute(commandLine);

        } catch (IOException e) {
            // note: this could also throw ExecuteException, which is a subclass of IOException.
            // handle them using the same logic.
            if (ArrayUtils.isNotEmpty(out.toByteArray())) {
                logger.finest("Shell command failed output: " + new String(out.toByteArray(), charset));
            }
            throw new DSystemExecutionException(e);

        } finally {
            // if there's any error, give it a chance to print error message in "finally" before throwing exception.
            if (ArrayUtils.isNotEmpty(err.toByteArray())) {
                logger.warning("Shell command error stream message: " + new String(err.toByteArray(), charset));
            }
        }

        // return output byte stream.
        return out.toByteArray();
    }


    /**
     * From the input reader and get all its content.
     *
     * @param input input reader
     * @return the content of the reader in String.
     * @throws java.io.IOException
     */
    public String readContent(Reader input) throws IOException {
        if (input == null) return null;
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = input.read()) != -1) {
            sb.append((char)c);
        }
        return sb.toString();
    }

    public String readContent (InputStream input) throws IOException {
        if (input == null) return null;
        Reader reader = new InputStreamReader(input);
        String content = readContent(reader);
        reader.close();
        return content;
    }


    /**
     * Check to make sure Java is > 1.7
     *
     * @return True if Java version is satisfied.
     */
    public boolean checkJavaVersion() {
        //String version = System.getProperty("java.version");
        //return version.compareTo("1.6") >= 0;
        return SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_7);
    }


    /**
     * Get the long value from any Object, if possible.
     *
     * @param value The object that could either be null, or int, or string.
     * @return  The long value of the "value".
     */
    public Long getLong(Object value) {
        if (value == null) {
            return null;
        } else if (Integer.class.isInstance(value)) {
            return ((Integer) value).longValue();
        } else if (Long.class.isInstance(value)) {
            return (Long) value;
        } else if (String.class.isInstance(value)) {
            return Long.valueOf((String) value);
        } else if (Float.class.isInstance(value)) {
            return ((Float) value).longValue();
        } else if (Double.class.isInstance(value)) {
            return ((Double) value).longValue();
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            throw new IllegalArgumentException("Cannot parse value: " + value.toString());
        }
    }


    /**
     * Get the Boolean value from any object, if possible. Mostly used to bridge with PHP json settings.
     *
     * @param value the value, could be '1' or "true", or null.
     * @return
     */
    public boolean getBoolean(Object value) {
        if (value == null) {
            return false;
        } else if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof Integer) {
            return BooleanUtils.toBoolean((Integer) value);
        } else if (value instanceof String) {
            String str = (String) value;
            if (StringUtils.isBlank(str)) return false;
            try {
                int i = Integer.parseInt(str);
                return BooleanUtils.toBoolean(i);
            } catch (NumberFormatException e) {
                return BooleanUtils.toBoolean(str);
            }
        } else {
            throw new IllegalArgumentException("Cannot parse value: " + value.toString());
        }
    }



    public Properties loadProperties(String configString) {
        Properties config = new Properties();
        try {
            config.load(new StringReader(configString));
        } catch (IOException e) {
            throw new DRuntimeException("Cannot read config string in DUtils.");
        }
        return config;
    }

    /**
     * Get the string of any object.
     *
     * @param object the object
     * @return the string
     */
    public String objectToString(Object object) {
        return ReflectionToStringBuilder.toString(object);
    }

    /**
     * Convert a Bindings object (usually from JSON object) into a Properties object using toString() approach.
     * @param bindings
     * @return
     */
    public Properties bindingsToProperties(Bindings bindings) {
        assert bindings != null;
        Properties properties = new Properties();
        for (String key : bindings.keySet()) {
            properties.put(key, bindings.get(key).toString());
        }
        return properties;
    }


    /**
     * Utility class for Json.
     */
    public static class Json {

        // singleton design pattern
        private static Json ourInstance = new Json();
        private Json() {};
        public static Json getInstance() {
            return ourInstance;
        }

        // might use more sophisticated approach of GsonBuilder().
        private Gson defaultGson = new Gson();
        private JsonParser defaultJsonParser = new JsonParser();

        /**
         * Encapsulate json object.
         *
         * @param obj the object to encode
         * @return the json string
         */
        public String toJson(Object obj) {
            if (obj instanceof Number) {
                return defaultGson.toJson(new BigDecimal(obj.toString()));
            } else {
                return defaultGson.toJson(obj);
            }
        }

        /**
         * Parse a Json string into either a null, a primitive (Number, String, Boolean), a list (ArrayList), or a map (Bindings).
         *
         * @param json the json string
         * @return json object in Bindings usually.
         */
        public Object fromJson(String json) throws JsonParseException {
            if (StringUtils.isEmpty(json)) {
                return null;
            }
            JsonElement element = defaultJsonParser.parse(json);
            return fromJson(element);
        }


        public <T> T fromJson(String json, Class<T> classOfT) throws JsonSyntaxException {
            if (StringUtils.isEmpty(json)) {
                return null;
            }
            return defaultGson.fromJson(json, classOfT);
        }


        private Object fromJson(JsonElement element) {
            if (element.isJsonNull()) {
                return null;

            } else if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    return primitive.getAsBoolean();
                } else if (primitive.isNumber()) {
                    // attention: this returns gson.internal.LazilyParsedNumber, which has problem when use gson.toJson(obj) to serialize again.
                    // LazilyParsedNumber is a subclass of Number, can't be cast to Integer, Long, etc.
                    return primitive.getAsNumber();
                    // this is to avoid using LazilyParsedNumber
                    //return new BigDecimal(primitive.getAsString());
                } else if (primitive.isString()) {
                    return primitive.getAsString();
                }
                throw new AssertionError("Invalid JsonPrimitive.");

            } else if (element.isJsonArray()) {
                List<Object> list = new ArrayList<Object>();
                for (JsonElement e : element.getAsJsonArray()) {
                    list.add(fromJson(e));
                }
                return list;

            } else if (element.isJsonObject()) {
                Bindings bindings = new SimpleBindings();
                for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                    bindings.put(entry.getKey(), fromJson(entry.getValue()));
                }
                return bindings;
            }
            throw new AssertionError("Invalid JsonElement.");
        }

        /**
         * @param json the Json string, can be either null or Json object.
         * @return either null or a Json object in class Bindings.
         */
        public Bindings fromJsonObject(String json) throws JsonIOException, JsonParseException, JsonSyntaxException {
            Object obj = fromJson(json);
            if (obj == null) return null;
            if (!(obj instanceof Bindings)) {
                throw new JsonParseException("Not a JSON Object.");
            }
            return (Bindings) obj;
        }
    }


//    public static class Xmlrpc {
//        private final URL endpointUrl;
//        private final XmlRpcClient xmlrpcClient;
//        private final HttpClient httpClient;
//        private final int COOKIE_EXPIRE = 60 * 60 * 24 * 1000;  // 1-day of expiration.
//
//        public Xmlrpc(String endpoint) throws DConfigException {
//            try {
//                endpointUrl = new URL(endpoint);
//            } catch (MalformedURLException e) {
//                throw new DConfigException("Invalid endpoint.", e);
//            }
//
//            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
//            config.setServerURL(endpointUrl);
//
//            xmlrpcClient = new XmlRpcClient();
//            httpClient = new HttpClient();
//            httpClient.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
//
//            XmlRpcCommonsTransportFactory factory = new XmlRpcCommonsTransportFactory(xmlrpcClient);
//            factory.setHttpClient(httpClient);
//            xmlrpcClient.setTransportFactory(factory);
//            xmlrpcClient.setConfig(config);
//        }
//
//        public Object execute(String method, Object... params) throws DSiteException {
//            assert StringUtils.isNotBlank(method);
//            if (ArrayUtils.isEmpty(params)) {
//                params = new Object[]{};
//            }
//            try {
//                return xmlrpcClient.execute(method, params);
//            } catch (XmlRpcException e) {
//                throw new DSiteException("Cannot execute XML-RPC method: " + method, e);
//            }
//        }
//
//        public Map login(String username, String password) throws DSiteException {
//            //printCookies();
//            // seems that cookies/session_id are set automatically.
//            Map result = (Map) execute("user.login", username, password);
//            //printCookies();
//
//            // so there's no need to manually set cookies again.
//            // but below is the code to set cookies/session_id if needed.
//
//            /*httpClient.getState().clearCookies();
//            httpClient.getState().addCookie(new Cookie(
//                    endpointUrl.getHost(),
//                    (String) result.get("session_name"),
//                    (String) result.get("sessid"),
//                    "/", COOKIE_EXPIRE, false));*/
//            //printCookies();
//
//            return (Map) result.get("user");
//        }
//
//        public boolean logout() throws DSiteException {
//            //printCookies();
//            boolean success = (Boolean) execute("user.logout");
//            //printCookies();
//
//            // when log out, cookies are cleared automatically. so no need to manually clear them.
//
//            //httpClient.getState().clearCookies();
//            return success;
//        }
//
//        public Map connect() throws DSiteException {
//            return (Map) execute("system.connect");
//        }
//
//        private void printCookies() {
//            System.out.print("Cookies: ");
//            for (Cookie cookie : httpClient.getState().getCookies()) {
//                System.out.print(cookie.toExternalForm() + "; ");
//            }
//            System.out.println("");
//        }
//
//    }

}
