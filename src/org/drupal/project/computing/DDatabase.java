package org.drupal.project.computing;

import java.util.logging.Logger;

/**
 * Connect to Drupal through direct database access. It uses JDBC and Apache Commons DBUtils, so it's independent of
 * DBMS implementations. Currently we support MySQL and PostgreSQL.
 *
 * Due to security reasons, it is not recommended to use this class directly to access Drupal database unless necessary.
 * Typical reasons to use this class: 1) performance, 2) prototyping. Other than those 2 cases, you should consider
 * to use {computing_record} to pass data in/out of Drupal.
 *
 * This can be used as standalone class.
 */
public class DDatabase {
    protected Logger logger = DUtils.getInstance().getPackageLogger();



}
