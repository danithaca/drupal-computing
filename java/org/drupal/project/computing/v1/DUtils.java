package org.drupal.project.computing.v1;

import com.google.gson.*;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcCommonsTransportFactory;
import org.junit.Test;

import java.io.*;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import static junit.framework.Assert.*;

/**
 * Singleton of the utilities class.
 */
public class DUtils {

    ////// Singleton template ////////
    private static DUtils ourInstance = new DUtils();
    public static DUtils getInstance() {
        return ourInstance;
    }
    private DUtils() {
        // According to JDK doc, LogManager should do it right, but here I'll still do it myself.
        logger = Logger.getLogger("org.drupal.project.computing");
        // logger.setUseParentHandlers(false);
    }

    ///////////////////  code begins /////////////////

    public final String VERSION = "7.x-1.0-alpha3";

    private Logger logger;

    private Gson defaultGson;


    public Logger getPackageLogger() {
        return logger;
    }


    /**
     * Execute a command in the working dir, and return the output as a String. If error, log the errors in logger.
     * This is the un-refined version using Process and ProcessBuilder. See the other version with commons-exec.
     *
     * @param command The list of command and parameters.
     * @param workingDir The working directory. Could be null. The it's default user.dir.
     * @return command output.
     */
    @Deprecated
    public String executeShell(List<String> command, File workingDir) {
        logger.finest("Running system command: " + StringUtils.join(command, ' '));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDir != null && workingDir.exists() && workingDir.isDirectory()) {
            processBuilder.directory(workingDir);
        } else {
            logger.fine("Using current user directory to run system command.");
        }

        try {
            Process process = processBuilder.start();
            process.waitFor();

            if (process.exitValue() != 0) {
                logger.severe(readContent(new InputStreamReader(process.getErrorStream())));
                throw new DRuntimeException("Unexpected error executing system command: " + process.exitValue());
            } else {
                // running successfully.
                return readContent(new InputStreamReader(process.getInputStream()));
            }
        } catch (IOException e) {
            throw new DRuntimeException(e);
        } catch (InterruptedException e) {
            throw new DRuntimeException(e);
        }
    }

    /**
     * Retrieve the first valid MAC address as the Machine ID. We just trust that the order remains the same all the time.
     * If there's no valid MAC address, return null.
     * TODO: read DConfig too, eg. -Ddrupal.agent
     * @return Machine ID (as MAC address) or null.
     */
    public String getMachineId() {
        String id = null;
        try {
            // we can only hope the order of the enumeration remains the same each time we call.
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                // System.out.println(networkInterface.getName());
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null) {
                    id = Hex.encodeHexString(mac);
                    break;
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return id;
    }


    /**
     * Execute a command in the working dir, and return the output as a String. If error, log the errors in logger.
     * TODO: check to make sure it won't output confidential information from settings.php, etc.
     *
     * @param commandLine The command line object
     * @param workingDir The working directory. Could be null. The it's default user.dir.
     * @param input Input string
     * @return command output.
     */
    public String executeShell(CommandLine commandLine, File workingDir, String input) throws DSystemExecutionException {
        byte[] inputBytes = input == null ? null : input.getBytes();
        return new String(executeShell(commandLine, workingDir, inputBytes, Charset.defaultCharset()));
    }


    public byte[] executeShell(CommandLine commandLine, File workingDir, byte[] input, Charset charset) throws DSystemExecutionException {
        DefaultExecutor executor = new DefaultExecutor();
        // executor.setExitValue(0);
        // ExecuteWatchdog watchdog = new ExecuteWatchdog(60);
        // executor.setWatchdog(watchdog);
        if (workingDir != null) {
            executor.setWorkingDirectory(workingDir);
        }

        ByteArrayInputStream in = ArrayUtils.isNotEmpty(input) ? new ByteArrayInputStream(input) : null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        executor.setStreamHandler(new PumpStreamHandler(out, err, in));
        logger.finest("Shell command to run: " + commandLine.toString());
        if (ArrayUtils.isNotEmpty(input)) {
            logger.finest("Shell command input stream: " + new String(input, charset));
        }

        try {
            int exitValue = executor.execute(commandLine);
        } catch (ExecuteException e) {
            // this could happen when exit value is not 0. the error message will include exit value.
            //e.printStackTrace();
            if (ArrayUtils.isNotEmpty(out.toByteArray())) {
                logger.fine("Shell command failed output: " + new String(out.toByteArray(), charset));
            }
            throw new DSystemExecutionException(e);
        } catch (IOException e) {
            //e.printStackTrace();
            if (ArrayUtils.isNotEmpty(out.toByteArray())) {
                logger.fine("Shell command failed output: " + new String(out.toByteArray(), charset));
            }
            throw new DSystemExecutionException(e);
        } finally {
            // if there's any error, give it a chance to print error message in "finally" before throwing exception.
            if (ArrayUtils.isNotEmpty(err.toByteArray())) {
                logger.warning("Shell command error stream message: " + new String(err.toByteArray(), charset));
            }
        }
        return out.toByteArray();
    }


    public String executeShell(String command) throws DSystemExecutionException {
        logger.finest("Running system command: " + command);
        CommandLine commandLine = CommandLine.parse(command);
        return executeShell(commandLine, null, (String) null);  // specifies with executeShell() to call.
    }


    /**
     * From the input reader and get all its content.
     *
     * @param input input reader
     * @return the content of the reader in String.
     * @throws IOException
     */
    public String readContent(Reader input) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = input.read()) != -1) {
            sb.append((char)c);
        }
        return sb.toString();
    }


    /**
     * Check to make sure Java is > 1.6
     * @return True if Java version is satisfied.
     */
    public boolean checkJavaVersion() {
        //String version = System.getProperty("java.version");
        //return version.compareTo("1.6") >= 0;
        return SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_6);
    }


    /**
     * Get either the identifier if presented, or the class name.
     * @param classObject
     * @return
     */
    public String getIdentifier(Class<?> classObject) {
        Identifier id = classObject.getAnnotation(Identifier.class);
        if (id != null) {
            return id.value();
        } else {
            return classObject.getSimpleName();
        }
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
        } else {
            throw new IllegalArgumentException("Cannot parse value: " + value.toString());
        }
    }

    /**
     * Encapsulate json object.
     * @param obj
     * @return
     */
    public String toJson(Object obj) {
        return getDefaultGson().toJson(obj);
    }

    /**
     * Parse a Json string into either a primitive, a list, or a map.
     * @param json
     * @return
     */
    public Object fromJson(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        JsonElement element = new JsonParser().parse(json);
        return fromJson(element);
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
                return primitive.getAsNumber();
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
            Map<String, Object> map = new HashMap<String, Object>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), fromJson(entry.getValue()));
            }
            return map;
        }
        throw new AssertionError("Invalid JsonElement.");
    }

    // doesn't work
    /*public String appendJsonString(String oldJsonString, String key, Object extraJson) {
        Map<String, Object> finalJson;
        Object oldJson = DUtils.getInstance().fromJson(oldJsonString);
        if (oldJson != null) {
            finalJson = (Map<String, Object>) oldJson;
        } else {
            finalJson = new HashMap<String, Object>();
        }
        finalJson.put(key, extraJson);
        return DUtils.getInstance().toJson(finalJson);
    }*/

    public Gson getDefaultGson() {
        if (defaultGson == null) {
            defaultGson = new GsonBuilder().create();
        }
        return defaultGson;
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
     * @param object
     * @return
     */
    public String objectToString(Object object) {
        return ReflectionToStringBuilder.toString(object);
    }

    /**
     * Utility class to run PHP snippet
     */
    public static class Php {

        private final String phpExec;

        public Php(String phpExec) throws DSystemExecutionException{
            assert StringUtils.isNotEmpty(phpExec);
            this.phpExec = phpExec;
            check();  // check validity of phpExec right away before doing any other things.
        }

        public Php() throws DSystemExecutionException{
            this(new DConfig().getPhpExec());
        }

        /**
         * Evaluates PHP code and return the output in JSON. JSR 223 should be the recommended approach, but there is no
         * good PHP engine. PHP-Java bridge is simply a wrapper of php-cgi and doesn't do much, and is not as flexible as
         * we call php exec. Quercus works well, but the jar file is too big to include here. In short, we'll simply call
         * PHP executable and get results in JSON. see [#1220194]
         *
         * @param phpCode PHP code snippet.
         * @return PHP code execution output.
         */
        public String evaluate(String phpCode) throws DSystemExecutionException {
            CommandLine commandLine = new CommandLine(phpExec);
            // we could suppress error. but this is not good because we want it generate errors when fails.
            // in command line "-d error_reporting=0", but here it didn't work.
            //commandLine.addArgument("-d error_reporting=0");
            commandLine.addArgument("--");  // execute input from shell.
            return DUtils.getInstance().executeShell(commandLine, null, phpCode);
        }

        /**
         * Serialize a Java object into PHP serialize byte string.
         *
         * @param value
         * @return
         * @throws DSystemExecutionException
         */
        public byte[] serialize(Object value) throws DSystemExecutionException {
            String json = DUtils.getInstance().getDefaultGson().toJson(value);
            CommandLine commandLine = new CommandLine(phpExec);
            commandLine.addArgument("-E");
            // first decode json, and then serialize it. needs to escape
            commandLine.addArgument("echo serialize(json_decode($argn));", false);
            return DUtils.getInstance().executeShell(commandLine, null, json.getBytes(), Charset.defaultCharset());
        }

        private String unserializeToJson(byte[] serializedBytes) throws DSystemExecutionException {
            CommandLine commandLine = new CommandLine(phpExec);
            commandLine.addArgument("-E");
            // first unserialize php, and then encode into Json. needs to escape.
            commandLine.addArgument("echo json_encode(unserialize($argn));", false);
            return new String(DUtils.getInstance().executeShell(commandLine, null, serializedBytes, Charset.defaultCharset()));
        }

        /**
         * Unserialize a PHP serialized byte string into a Java object.
         */
        public Object unserialize(byte[] serializedBytes) throws DSystemExecutionException {
            return DUtils.getInstance().fromJson(unserializeToJson(serializedBytes));
        }

        /**
         * FIXME: toClass can't handle "Type" cases.
         */
        public <T> T unserialize(byte[] serializedBytes, Class<T> toClass) throws DSystemExecutionException {
            return DUtils.getInstance().getDefaultGson().fromJson(unserializeToJson(serializedBytes), toClass);
        }


        public void check() throws DSystemExecutionException {
            // test php executable
            String testPhp = getInstance().executeShell(phpExec + " -v");
            if (!testPhp.startsWith("PHP")) {
                throw new DSystemExecutionException("Cannot execute php executable: " + phpExec);
            }
            getInstance().logger.fine("Evaluate with PHP version: " + new Scanner(testPhp).nextLine());
        }


        /**
         * Extract the php code snippet that defines phpVar from the php file phpFile. For example, to get $databases
         * from settings.php: extractPhpVariable(DConfig.locateFile("settings.php", "$databases");
         *
         * PHP code: see parse.php.
         *
         * FIXME: if the file has something like "var_dump($databases);", the code would return "$databases);", which is incorrect.
         * need to fix it in the next release.
         *
         * @param phpFile the PHP file to be processed.
         * @param phpVar the PHP variable to be extracted. It can only be the canonical form $xxx, not $xxx['xxx'].
         * @return the PHP code that defines phpVar.
         */
        public String extractVariable(File phpFile, String phpVar) throws DSystemExecutionException {
            assert phpFile.isFile() && phpVar.matches("\\$\\w+");
            final String phpExec = "<?php\n" +
                    "function extract_variable($file, $var_name) {\n" +
                    "  $content = file_get_contents($file);\n" +
                    "  $tokens = token_get_all($content);\n" +
                    "  $code = '';\n" +
                    "\n" +
                    "  $phase = 'skip';\n" +
                    "  foreach ($tokens as $token) {\n" +
                    "    if (is_array($token) && $token[0] == T_VARIABLE && $token[1] == $var_name) {\n" +
                    "      $phase = 'accept';\n" +
                    "      $code .= $token[1];\n" +
                    "    }\n" +
                    "    else if ($phase == 'accept') {\n" +
                    "      $code .= is_array($token) ? $token[1] : $token;\n" +
                    "      if ($token == ';') {\n" +
                    "        $phase = 'skip';\n" +
                    "        $code .= \"\\n\";\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "\n" +
                    "  return $code;\n" +
                    "}\n";

            String code = String.format("%s\necho extract_variable('%s', '%s');",
                    phpExec,
                    phpFile.getAbsolutePath().replaceAll("'", "\\'"),
                    phpVar);
            return evaluate(code);
        }
    }


    /**
     * This is the utility class to run drush command.
     */
    public static class Drush {

        private final String drushExec;

        private Boolean computingEnabled;

        public Drush(String drushExec) {
            assert StringUtils.isNotBlank(drushExec) : "Cannot initiate Drush with an empty Drush executable.";
            //if (StringUtils.isBlank(drushExec)) {
            //    throw new DRuntimeException("Cannot initiate Drush with an empty Drush executable.");
            //}
            this.drushExec = drushExec;
            // TODO: check "drush cc" and existence of computing module.
        }

        public Drush() {
            this(new DConfig().getDrushExec());
        }


        public String execute(String[] command) throws DConnectionException {
            return execute(command, null);
        }

        /**
         * Execute Drush command, and returns STDOUT results.
         * attention: here we use shell stdout to output results, which is problematic.
         *
         * possible problems are:
         * 1) security concerns, where confidential info might get printed
         * 2) encoding/decoding characters to byte streams, especially with non-english languages and serializd strings.
         * 3) escaping problems
         * 4) output might get messed up with other sub-processes.
         *
         * some other IPC approaches: 1) Apache Camel, 2) /tmp files, 3) message queue
         * however, STDOUT is the simplest approach for now.
         *
         * @param command The drush command to execute.
         * @param input Input stream, could be null.
         * @return STDOUT results.
         * @throws DConnectionException
         */
        public String execute(String[] command, String input) throws DConnectionException {
            try {
                CommandLine cmdLine = CommandLine.parse(drushExec);
                // 2nd parameter is crucial. without it, there would be escaping problems.
                // false means we didn't escape the params and we want CommandLine to escape for us.
                cmdLine.addArguments(command, false);

                //System.out.println(cmdLine.toString());
                return getInstance().executeShell(cmdLine, null, input);

            } catch (DSystemExecutionException e) {
                throw new DConnectionException("Cannot execute drush.", e);
            }
        }

        /**
         * Get Drush version.
         * @return
         * @throws DConnectionException
         */
        public String getVersion() throws DConnectionException {
            return execute(new String[]{"version", "--pipe"}).trim();
        }

        public String getDrushExec() {
            return drushExec;
        }

        /**
         * Get Drupal core-status info.
         * @see "drush core-status"
         *
         * @return
         * @throws DConnectionException
         */
        public Properties getCoreStatus() throws DConnectionException {
            Properties coreStatus = new Properties();
            try {
                coreStatus.load(new StringReader(execute(new String[]{"core-status", "--pipe"})));
            } catch (IOException e) {
                getInstance().logger.severe("Error running drush core-status.");
                e.printStackTrace();
            }
            return coreStatus;
        }

        /**
         * <p>Run any drupal code and get returns results in json.</p>
         *
         * <p>You wouldn't want to eval any Drupal code here. For example, node_save($node) is very hard to eval because
         * it's hard to pass in the parameter $node.</p>
         *
         * <p>To do node_save(), you need a Drupal function as a proxy that takes into some primitive parameters,
         * and then prepares $node to do node_save(). In fact, the basic idea of the "computing" module is that
         * you have a mixed of Drupal/PHP code and non-PHP code, where the former focuses on Drupal-related
         * stuff, and the latter on non-PHP related stuff.</p>
         *
         * @param phpCode Should not use "<?php ... ?>"
         *
         * @return execution results in JSON.
         * @throws DConnectionException
         */
        public String computingEval(String phpCode) throws DConnectionException {
            if (!checkComputing()) {
                String message = String.format("Drush command '%s' is invalid, or the 'computing' drush command not found at target Drupal site.", drushExec);
                throw new DConnectionException(message);
            }
            // this is the stub function to run any Drupal code.
            String[] command = {"computing-eval", "--pipe", "-"};
            return execute(command, phpCode);
        }


        /**
         * Call any Drupal functions and returns results in json.
         *
         * @param params First param is the function name; the rest are parameters in json.
         *               Callers are responsible to wrap the params in json, but not responsible to escape them as command line args.
         * @return Execution results in JSON.
         * @throws DConnectionException
         */
        public String computingCall(String[] params) throws DConnectionException {
            if (!checkComputing()) {
                String message = String.format("Drush command '%s' is invalid, or the 'computing' drush command not found at target Drupal site.", drushExec);
                throw new DConnectionException(message);
            }
            String[] command = {"computing-call", "--pipe"};
            command = ArrayUtils.addAll(command, params);
            return execute(command);
        }

        public String computingCall(String function, Object... funcParams) throws DConnectionException {
            String[] params = new String[funcParams.length + 1];
            params[0] = function;
            for (int i = 0; i < funcParams.length; i ++) {
                params[i + 1] = DUtils.getInstance().getDefaultGson().toJson(funcParams[i]);
            }
            return computingCall(params);
        }


        public boolean checkComputing() {
            return checkComputing(false);
        }

        /**
         * Check if the "computing" module drush command is available. It doesn't check if the module itself is enabled or not.
         * @return true if computing* drush command is available, or false if not.
         */
        public boolean checkComputing(boolean force) {
            if (computingEnabled != null && !force) {
                return computingEnabled;
            }
            try {
                String[] command = {"help", "--pipe", "--filter=computing"};
                String results = execute(command);
                return computingEnabled = true; // if computing category is not found, then there'll be exception.
            } catch (DConnectionException e) {
                return computingEnabled = false;
            }
        }

    }



    public static class Xmlrpc {
        private final URL endpointUrl;
        private final XmlRpcClient xmlrpcClient;
        private final HttpClient httpClient;
        private final int COOKIE_EXPIRE = 60 * 60 * 24 * 1000;  // 1-day of expiration.

        public Xmlrpc(String endpoint) throws DConfigException {
            try {
                endpointUrl = new URL(endpoint);
            } catch (MalformedURLException e) {
                throw new DConfigException("Invalid endpoint.", e);
            }

            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(endpointUrl);

            xmlrpcClient = new XmlRpcClient();
            httpClient = new HttpClient();
            httpClient.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);

            XmlRpcCommonsTransportFactory factory = new XmlRpcCommonsTransportFactory(xmlrpcClient);
            factory.setHttpClient(httpClient);
            xmlrpcClient.setTransportFactory(factory);
            xmlrpcClient.setConfig(config);
        }

        public Object execute(String method, Object... params) throws DConnectionException {
            assert StringUtils.isNotBlank(method);
            if (ArrayUtils.isEmpty(params)) {
                params = new Object[]{};
            }
            try {
                return xmlrpcClient.execute(method, params);
            } catch (XmlRpcException e) {
                throw new DConnectionException("Cannot execute XML-RPC method: " + method, e);
            }
        }

        public Map login(String username, String password) throws DConnectionException {
            //printCookies();
            // seems that cookies/session_id are set automatically.
            Map result = (Map) execute("user.login", username, password);
            //printCookies();

            // so there's no need to manually set cookies again.
            // but below is the code to set cookies/session_id if needed.

            /*httpClient.getState().clearCookies();
            httpClient.getState().addCookie(new Cookie(
                    endpointUrl.getHost(),
                    (String) result.get("session_name"),
                    (String) result.get("sessid"),
                    "/", COOKIE_EXPIRE, false));*/
            //printCookies();

            return (Map) result.get("user");
        }

        public boolean logout() throws DConnectionException {
            //printCookies();
            boolean success = (Boolean) execute("user.logout");
            //printCookies();

            // when log out, cookies are cleared automatically. so no need to manually clear them.

            //httpClient.getState().clearCookies();
            return success;
        }

        public Map connect() throws DConnectionException {
            return (Map) execute("system.connect");
        }

        private void printCookies() {
            System.out.print("Cookies: ");
            for (Cookie cookie : httpClient.getState().getCookies()) {
                System.out.print(cookie.toExternalForm() + "; ");
            }
            System.out.println("");
        }

    }


    public static class UnitTest {
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
            DUtils.Drush drush = new DUtils.Drush("drush @local");
            assertTrue(drush.checkComputing());
            assertEquals("Expected drush version", "5.4", drush.getVersion());

            // test drupal version
            Properties coreStatus = drush.getCoreStatus();
            //coreStatus.list(System.out);
            assertTrue("Expected drupal version", coreStatus.getProperty("drupal_version").startsWith("7"));

            // test execute php
            String jsonStr = drush.computingEval("return node_load(1);").trim();
            System.out.println(jsonStr);
            assertTrue(jsonStr.startsWith("{") && jsonStr.endsWith("}"));

            jsonStr = drush.computingEval("node_load(1);").trim();
            System.out.println(jsonStr);
            assertEquals("null", jsonStr);


            // test computing call
            Gson gson = new Gson();
            String s2 = drush.computingCall(new String[]{"variable_get", gson.toJson("install_profile")});
            //System.out.println(s2);
            assertEquals("standard", gson.fromJson(s2, String.class));
            String s3 = drush.computingCall("variable_get", "install_profile");
            assertEquals("standard", gson.fromJson(s3, String.class));

            // test check computing
            Drush invalidDrush = new Drush("drush @xxx");
            assertFalse(invalidDrush.checkComputing());
            invalidDrush = new Drush("drush");
            assertFalse(invalidDrush.checkComputing());
        }

        @Test
        public void testLocalDrush() throws Exception {
            // test local environment
            Drush drush = new Drush("drush @local");
            Properties coreStatus = drush.getCoreStatus();
            assertTrue(coreStatus.getProperty("drupal_version").startsWith("7"));
            assertEquals("/Users/danithaca/Development/drupal7", coreStatus.getProperty("drupal_root"));
            assertEquals("/Users/danithaca/Development/drupal7", drush.execute(new String[]{"drupal-directory", "--local"}).trim());
        }

        @Test
        public void testMisc() {
            assertEquals(new Long(12L), DUtils.getInstance().getLong(new Long(12L)));
            assertEquals(new Long(5L), DUtils.getInstance().getLong("5"));
            assertEquals(new Long(100L), DUtils.getInstance().getLong(new Float(100.53)));

            // test tostring
            System.out.println(DUtils.getInstance().objectToString(new DConfig()));
            System.out.println(DUtils.getInstance().objectToString(1));

            System.out.println(DUtils.getInstance().getMachineId());
        }

        @Test
        public void testJson() {
            Map<String, Object> json = new HashMap<String, Object>();
            json.put("abc", 1);
            json.put("hello", "world");
            String jsonString = DUtils.getInstance().toJson(json);
            Map<String, Object> json1 = (Map<String, Object>) DUtils.getInstance().fromJson(jsonString);
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
        public void testPhp() throws Exception {
            String results;
            DConfig config = new DConfig();
            assertTrue(StringUtils.isNotBlank(config.getPhpExec()));

            Php php = new Php();

            // System.out.println(DUtils.getInstance().executeShell("php -v"));

            results = php.evaluate("<?php echo 'hello, world';");
            assertEquals("hello, world", results.trim());
            results = php.evaluate("<?php echo json_encode(100);");
            assertEquals(new Integer(100), DUtils.getInstance().getDefaultGson().fromJson(results, Integer.class));

            // try to get $databases from settings.php.
            config.setProperty("drupal.drush", "drush @local");
            File settingsFile = config.locateFile("settings.php");
            String databasesCode = php.extractVariable(settingsFile, "$databases");
            assertTrue(databasesCode.startsWith("$databases"));

            // test serialization
            byte[] serialized;
            // serialized = php.serialize(new Integer[] {1, 3}))
            serialized = php.serialize("hello, world");
            //System.out.println(new String(serialized));
            assertEquals("hello, world", php.unserialize(serialized, String.class));
            assertEquals(new Integer(1), php.unserialize(php.serialize(1), Integer.class));
        }

        /**
         * This method is not supposed to test anything. It's simply running to print out some stuff.
         */
        @Test
        public void simplePrint() throws Exception {
            DConfig config = new DConfig();
            config.setProperty("drupal.drush", "drush @local");
            File settingsFile = config.locateFile("settings.php");
            //String settingsCode = DUtils.getInstance().readContent(new FileReader(settingsFile));
            System.out.println("Settings.php: " + settingsFile.getAbsolutePath());

            Php php = new Php(config.getPhpExec());
            System.out.println(php.extractVariable(settingsFile, "$databases"));
            //System.out.println(DUtils.getInstance().stripPhpComments(settingsCode));
        }

        @Test
        public void testXmlrpc() throws Exception {
            Xmlrpc xmlrpc = new Xmlrpc("http://rgb.knowsun.com/x");
            //Xmlrpc xmlrpc = new Xmlrpc("http://d7dev1.localhost/xmlrpc");
            Map result;
            result = xmlrpc.connect();
            assertTrue(((String)result.get("sessid")).length() > 0);

            result = xmlrpc.login("test", "test");
            assertTrue(result.size() > 0);
            System.out.println(result);

            result = (Map) xmlrpc.execute("node.retrieve", 1);
            assertTrue(result.size() > 0);
            System.out.println(result);

            assertTrue(xmlrpc.logout());
        }

    }

}
