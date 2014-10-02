package org.drupal.project.computing;

import com.google.gson.*;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.exec.*;
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
import org.drupal.project.computing.exception.DConfigException;
import org.drupal.project.computing.exception.DRuntimeException;
import org.drupal.project.computing.exception.DSiteException;
import org.drupal.project.computing.exception.DSystemExecutionException;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.io.*;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Logger;

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

    public final String VERSION = "7.x-2.0-alpha1";

    private Logger logger;

    private Gson defaultGson;


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
     * @param fileName the name of the file to locate. Do not include directory.
     * @return The file object if found.
     * @throws FileNotFoundException
     */
    public File locateFile(String fileName) throws FileNotFoundException {
        assert StringUtils.isNotBlank(fileName);
        File theFile = null;

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
     * Execute a command in the working dir, and return the output as a String. If error, log the errors in logger.
     * This is the un-refined version using Process and ProcessBuilder. See the other version with commons-exec.
     *
     * @param command The list of command and parameters.
     * @param workingDir The working directory. Could be null. The it's default user.dir.
     * @return command output.
     * @deprecated In favor of the other executeShell with Apache.Commons.Exec.
     */
    /*@Deprecated
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
    }*/

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
        int timeout = new Integer(DConfig.loadDefault().getProperty("drupal.computing.exec.timeout", "120000"));
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
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            throw new IllegalArgumentException("Cannot parse value: " + value.toString());
        }
    }


//    @Deprecated
//    public String toJson(Object obj) {
//        return getDefaultGson().toJson(obj);
//    }


//    @Deprecated
//    public Object fromJson(String json) {
//        if (StringUtils.isEmpty(json)) {
//            return null;
//        }
//        JsonElement element = new JsonParser().parse(json);
//        return fromJson(element);
//    }

//    @Deprecated
//    private Object fromJson(JsonElement element) {
//        if (element.isJsonNull()) {
//            return null;
//        } else if (element.isJsonPrimitive()) {
//            JsonPrimitive primitive = element.getAsJsonPrimitive();
//            if (primitive.isBoolean()) {
//                return primitive.getAsBoolean();
//            } else if (primitive.isNumber()) {
//                // attention: this returns gson.internal.LazilyParsedNumber, which has problem when use gson.toJson(obj) to serialize again.
//                return primitive.getAsNumber();
//            } else if (primitive.isString()) {
//                return primitive.getAsString();
//            }
//            throw new AssertionError("Invalid JsonPrimitive.");
//        } else if (element.isJsonArray()) {
//            List<Object> list = new ArrayList<Object>();
//            for (JsonElement e : element.getAsJsonArray()) {
//                list.add(fromJson(e));
//            }
//            return list;
//        } else if (element.isJsonObject()) {
//            Map<String, Object> map = new HashMap<String, Object>();
//            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
//                map.put(entry.getKey(), fromJson(entry.getValue()));
//            }
//            return map;
//        }
//        throw new AssertionError("Invalid JsonElement.");
//    }

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

//    @Deprecated
//    public Gson getDefaultGson() {
//        if (defaultGson == null) {
//            defaultGson = new GsonBuilder().create();
//        }
//        return defaultGson;
//    }

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
            String json = DUtils.Json.getInstance().toJson(value);
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
            return DUtils.Json.getInstance().fromJson(unserializeToJson(serializedBytes));
        }

        /**
         * FIXME: toClass can't handle "Type" cases.
         */
        public <T> T unserialize(byte[] serializedBytes, Class<T> toClass) throws DSystemExecutionException {
            return new Gson().fromJson(unserializeToJson(serializedBytes), toClass);
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

        private String drushCommand;
        private String drushSiteAlias;

//        private Boolean computingEnabled;

        /**
         * This is the only initialization code. Need to specify drush command and siteAlias.
         * Default site alias is @self.
         *
         * @param drushCommand drush executable command
         * @param drushSiteAlias drush site alias
         */
        public Drush (String drushCommand, String drushSiteAlias) {
            assert StringUtils.isNotBlank(drushCommand) && StringUtils.isNotBlank(drushSiteAlias);
            // TODO: check "drush cc" and existence of computing module.
            this.drushCommand = drushCommand;
            this.drushSiteAlias = drushSiteAlias;
        }


        public static Drush loadDefault() {
            // might need to check validity.
            DConfig config = DConfig.loadDefault();
            return new Drush(config.getDrushCommand(), config.getDrushSiteAlias());
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
         * @param command The drush command to execute, ignoring drush binary and site alias.
         * @param input Input stream, could be null.
         * @return STDOUT results.
         * @throws org.drupal.project.computing.exception.DSiteException
         */
        public String execute(String[] command, String input) throws DSiteException {
            try {
                // initialize command line
                CommandLine cmdLine = new CommandLine(drushCommand);
                cmdLine.addArgument(drushSiteAlias);
                //CommandLine cmdLine = CommandLine.parse(drushExec);

                // 2nd parameter is crucial. without it, there would be escaping problems.
                // false means we didn't escape the params and we want CommandLine to escape for us.
                cmdLine.addArguments(command, false);

                //System.out.println(cmdLine.toString());
                String output = getInstance().executeShell(cmdLine, input);
                return output;

            } catch (DSystemExecutionException e) {
                throw new DSiteException("Cannot execute drush.", e);
            }
        }


        public String execute(String[] command) throws DSiteException {
            return execute(command, null);
        }


        /**
         * Get Drush version.
         *
         * @return Drush version string
         * @throws DSiteException
         */
        public String getVersion() throws DSiteException {
            try {
                return execute(new String[]{"version", "--pipe"}).trim();
            } catch (Exception e) {
                throw new DSiteException("Cannot get drush version.", e);
            }
        }

        @Deprecated
        public String getDrushExec() {
            return getDrushString();
        }

        public String getDrushString() {
            return drushCommand + ' ' + drushSiteAlias;
        }


        /**
         * Get Drupal core-status info.
         *
         * @see "drush core-status"
         *
         * @return Core status in Map<String, Object>.
         * @throws DSiteException
         */
        public Bindings getCoreStatus() throws DSiteException {
            Bindings coreStatus = new SimpleBindings();
            try {

                // execute drush status
                String output = execute(new String[]{"core-status", "--pipe", "--format=json"});
                Bindings jsonObject = (Bindings) Json.getInstance().fromJson(output);
                coreStatus.putAll(jsonObject);

            } catch (Exception e) {
                getInstance().logger.severe("Error running drush core-status.");
                throw new DSiteException("Cannot run drush core-status.", e);
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
         * @throws DSiteException
         */
        public String computingEval(String phpCode) throws DSiteException {
            String result;
            try {
                result = execute(new String[] {"computing-eval", "--pipe", "-"}, phpCode);
            } catch (Exception e) {
                getInstance().logger.severe("Error executing PHP code through computing-eval: " + phpCode);
                throw new DSiteException("Cannot execute computing-eval.", e);
            }
            return result;
        }


        /**
         * Call any Drupal functions and returns results in json.
         *
         * @param params First param is the function name; the rest are parameters in json.
         *   Callers are responsible to wrap the params in json, but not responsible to escape them as command line args.
         * @return Execution results in JSON.
         * @throws DSiteException
         */
        public String computingCall(String[] params) throws DSiteException {
            String result;
            try {
                String[] args = {"computing-call", "--pipe"};
                args = ArrayUtils.addAll(args, params);
                result = execute(args);
            } catch (Exception e) {
                getInstance().logger.severe("Error executing function call through computing-call: " + ArrayUtils.toString(params));
                throw new DSiteException("Cannot execute computing-call.", e);
            }
            return result;
        }

        /**
         * Utility function to use with the first computingCall().
         * @param function the name of the Drupal function to call.
         * @param funcParams the parameters not encoded in JSON.
         * @return Excution results in JSON.
         * @throws DSiteException
         */
        public String computingCall(String function, Object... funcParams) throws DSiteException {
            String[] params = new String[funcParams.length + 1];
            params[0] = function;
            for (int i = 0; i < funcParams.length; i ++) {
                params[i + 1] = DUtils.Json.getInstance().toJson(funcParams[i]);
            }
            return computingCall(params);
        }


        /**
         * Check if the "computing" module drush command is available. It doesn't check if the module itself is enabled or not.
         * @return true if computing* drush command is available, or false if not.
         */
//        public boolean checkComputing(boolean force) {
//            if (computingEnabled != null && !force) {
//                return computingEnabled;
//            }
//            try {
//                String[] command = {"help", "--pipe", "--filter=computing"};
//                String results = execute(command);
//                return computingEnabled = true; // if computing category is not found, then there'll be exception.
//            } catch (DSiteException e) {
//                return computingEnabled = false;
//            }
//        }
//
//
//        public boolean checkComputing() {
//            return checkComputing(false);
//        }

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
         * Parse a Json string into either a primitive, a list, or a map.
         *
         * @param json the json string
         * @return json object in map usually.
         */
        public Object fromJson(String json) throws JsonIOException, JsonParseException, JsonSyntaxException {
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
                    // LazilyParsedNumber is a subclass of Number.
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

        public Object execute(String method, Object... params) throws DSiteException {
            assert StringUtils.isNotBlank(method);
            if (ArrayUtils.isEmpty(params)) {
                params = new Object[]{};
            }
            try {
                return xmlrpcClient.execute(method, params);
            } catch (XmlRpcException e) {
                throw new DSiteException("Cannot execute XML-RPC method: " + method, e);
            }
        }

        public Map login(String username, String password) throws DSiteException {
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

        public boolean logout() throws DSiteException {
            //printCookies();
            boolean success = (Boolean) execute("user.logout");
            //printCookies();

            // when log out, cookies are cleared automatically. so no need to manually clear them.

            //httpClient.getState().clearCookies();
            return success;
        }

        public Map connect() throws DSiteException {
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

}
