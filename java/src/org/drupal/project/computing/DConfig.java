package org.drupal.project.computing;

import org.apache.commons.lang3.StringUtils;
import org.drupal.project.computing.exception.DConfigException;
import org.drupal.project.computing.exception.DNotFoundException;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.logging.Logger;


/**
 * This is the config class to help initialize DSite and DApplication.
 */
public class DConfig {

    protected Logger logger = DUtils.getInstance().getPackageLogger();

    private static DConfig defaultConfig;

    /**
     * This is the main place we save configurations.
     * We don't use hard coded fields because we don't know which are needed.
     */
    protected Properties properties;

    public DConfig() {
        logger.info("Drupal Computing agent library version: " + DUtils.getInstance().VERSION);
        properties = new Properties();
        // properties = System.getProperties();
        // here we can initialize a few default properties too.
    }

    /**
     * Default constructor that construct the config object.
     *
     * @param properties additional properties
     */
    public DConfig(Properties properties) {
        this();
        this.properties.putAll(properties);
    }


    /**
     * Factory method. Load default config from config.properties file.
     * Load priority:
     * 1. Arbitrary properties file specified by java -Ddrupal.computing.config.file
     * 2. Properties file in current working directory
     * 3. Properties file in the same directory as the Jar file
     * 4. Properties file in "user.home".
     *
     * @param reload specifies whether to read the config.properties file again, or use the cached one.
     * @return A DConfig object with loaded properties.
     */
    public static DConfig loadDefault(boolean reload) {
        // load config file only when needed.
        if (reload || defaultConfig == null) {
            DConfig config = new DConfig();
            // "dcomp.config.file" can only get set as ENVIRONMENT or system properties.
            String configFileName = config.getProperty("dcomp.config.file", "config.properties");
            Reader configFileReader = null;

            // try to locate config file.
            try {
                File configFile = DUtils.getInstance().locateFile(configFileName);
                if (!configFile.exists() || !configFile.isFile()) {
                    throw new FileNotFoundException();
                }
                configFileReader = new FileReader(configFile);
            } catch (FileNotFoundException e) {
                config.logger.warning("Cannot find configuration file: " + configFileName);
            }

            // read config file and load into config.
            if (configFileReader != null) {
                Properties configProperties = new Properties();
                try {
                    configProperties.load(configFileReader);
                } catch (IOException e) {
                    config.logger.warning("Cannot read configuration file: " + configFileName);
                }
                config.properties.putAll(configProperties);
            }

            // set defaultConfig
            defaultConfig = config;
        }
        return defaultConfig;
    }

    /**
     * @return loadDefault(false)
     */
    public static DConfig loadDefault() {
        return loadDefault(false);
    }

    /**
     * Read priority:
     * 1. Property set in this.properties.
     * 2. Java system property
     * 3. Property set in system ENV: replace '.' with '_' and lower case to upper case.
     *
     * @param propertyName the property name to read.
     * @param defaultValue the default value.
     * @return propertyValue if set, of defaultValue
     */
    public String getProperty(String propertyName, String defaultValue) {
        assert StringUtils.isNotBlank(propertyName);

        // 1. Property set in this.properties.
        if (properties.containsKey(propertyName)) {
            return properties.getProperty(propertyName);
        }

        // 2. Java system property (not assuming it's already in system properties)
        Properties systemProperties = System.getProperties();
        if (systemProperties.containsKey(propertyName)) {
            return systemProperties.getProperty(propertyName);
        }

        // 3.Property set in system ENV: replace '.' with '_' and lower case to upper case.
        String envPropertyName = propertyName.replaceAll("\\.", "_").toUpperCase();
        String envValue = System.getenv(envPropertyName);

        return envValue == null ? defaultValue : envValue;
    }

    public void setProperty(String propertyName, String value) {
        assert StringUtils.isNotBlank(propertyName);
        properties.setProperty(propertyName, value);
    }


    /**
     * Try to read "dcomp.database.url" in config.properties or system env. Throws exception if can't find.
     *
     * @return The database url string that can be used directly in JDBC connections.
     * @throws DConfigException
     * @see <a href="http://dev.mysql.com/doc/connector-j/en/connector-j-usagenotes-connect-drivermanager.html">Simple MySQL example</a>
     * @see <a href="http://dev.mysql.com/doc/connector-j/en/connector-j-reference-configuration-properties.html">MySQL connection properties</a>
     */
    public String getDatabaseUrl() throws DConfigException {
        String url = getProperty("dcomp.database.url", "");
        if (StringUtils.isNotBlank(url)) {
            return url;
        } else {
            throw new DConfigException("Cannot find 'dcomp.database.url' settings.");
        }
    }


    /**
     * Try to read "dcomp.database.properties.*" in config.properties.
     * Requires to have "driver", "database", "username", "password" and "host" to be considered as valid.
     * See more about db settings in Drupal settings.php.
     * Extra parameters will be passed to database connection too.
     *
     * @return The database properties as defined in dcomp.database.properties.*. Or throws DNotFoundException.
     * @see <a href="http://dev.mysql.com/doc/connector-j/en/connector-j-usagenotes-connect-drivermanager.html">Simple MySQL example</a>
     * @see <a href="http://dev.mysql.com/doc/connector-j/en/connector-j-reference-configuration-properties.html">MySQL connection properties</a>
     */
    public Properties getDatabaseProperties() throws DNotFoundException {
        Properties dbProperties = new Properties();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("dcomp.database.properties.")) {
                dbProperties.put(key.substring("dcomp.database.properties.".length()), properties.getProperty(key));
            }
        }
        if (!dbProperties.isEmpty()) {
            return dbProperties;
        } else {
            throw new DNotFoundException("Cannot find database configuration properties.");
        }
    }


    /**
     * @return The drush executable command.
     */
    public String getDrushCommand() {
        return this.getProperty("dcomp.drush.command", "drush");
    }

    /**
     * @return The default drush site alias. Users should include "@" in the config file.
     */
    public String getDrushSiteAlias() {
        return this.getProperty("dcomp.drush.site", "@self");
    }


    /**
     * Retrieve the agent name either from config.properties, or from host name, or set as "Unknown".
     *
     * @return the agent name.
     */
    public String getAgentName() {
        String agentName = getProperty("dcomp.agent.name", "");

        if (agentName.length() == 0) {
            logger.info("Cannot find agent name. Use host name instead.");
            try {
                agentName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                e.printStackTrace();
                logger.info("Cannot find host name. Use MAC address instead.");
                agentName = "Unknown";
            }
        }

        return agentName;
    }

}
