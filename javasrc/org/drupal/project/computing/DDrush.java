package org.drupal.project.computing;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.drupal.project.computing.exception.DSiteException;
import org.drupal.project.computing.exception.DSystemExecutionException;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.logging.Logger;

/**
 * This is the utility class to run drush command.
 */
public class DDrush {

    private String drushCommand;
    private String drushSiteAlias;

    private Logger logger = DUtils.getInstance().getPackageLogger();

    //private Boolean computingEnabled;

    /**
     * This is the only initialization code. Need to specify drush command and siteAlias.
     * Default site alias is @self.
     *
     * @param drushCommand drush executable command
     * @param drushSiteAlias drush site alias
     */
    public DDrush(String drushCommand, String drushSiteAlias) {
        assert StringUtils.isNotBlank(drushCommand) && StringUtils.isNotBlank(drushSiteAlias);
        // TODO: check "drush cc" and existence of computing module.
        this.drushCommand = drushCommand;
        this.drushSiteAlias = drushSiteAlias;
    }


    public static DDrush loadDefault() {
        // might need to check validity.
        DConfig config = DConfig.loadDefault();
        return new DDrush(config.getDrushCommand(), config.getDrushSiteAlias());
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
            String output = DUtils.getInstance().executeShell(cmdLine, input);
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

//    @Deprecated
//    public String getDrushExec() {
//        return getDrushString();
//    }

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
            Bindings jsonObject = (Bindings) DUtils.Json.getInstance().fromJson(output);
            coreStatus.putAll(jsonObject);

        } catch (Exception e) {
            logger.severe("Error running drush core-status.");
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
            logger.severe("Error executing PHP code through computing-eval: " + phpCode);
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
            logger.severe("Error executing function call through computing-call: " + ArrayUtils.toString(params));
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
