package org.drupal.project.computing;

import java.util.logging.Logger;

/**
 * TODO: not implemented yet.
 *
 * Connect to Drupal through direct database access. It uses JDBC and Apache Commons DBUtils, so it's independent of
 * DBMS implementations. Currently we support MySQL and PostgreSQL.
 *
 * Due to security reasons, it is not recommended to use this class directly to access Drupal database unless necessary.
 * Typical reasons to use this class: 1) performance, 2) prototyping. Other than those 2 cases, you should consider
 * use {computing_record} to pass data in/out of Drupal.
 *
 * It is also recommended to save database credentials in Drupal settings.php, and then use $GLOBALS['databases']
 * to get the credentials and pass to agent using a computing record.
 *
 * This can be used as standalone class.
 *
 * @see DConfig().getDatabaseUrl()
 * @see DConfig().getDatabaseProperties()
 */
public class DDatabase {
    protected Logger logger = DUtils.getInstance().getPackageLogger();

}
