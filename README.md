Drupal Computing Clients
========================

This is the Java and Python agent client code for [Drupal Computing module](http://drupal.org/project/computing).

Requirements:

  * Java SE 7+
  * Python 3+ (vote support for Python 2.7 is planned at ...)
  
Current branch release: 7.x-2.0. (Follows Drupal release convention)

Technical Features
------------------

  * Reusable Java/Python client code to connect to Drupal using Services (REST Server) and Drush.
  * Reusable code to read/write data to/from Drupal.
  * Multi-thread support
  * MapReduce/Hadoop support
  

Setup
-----

You need JavaSE 7+ and Python 2.7+ (currently only supports Python 3.0+, see ...).

You need to setup Java "CLASSPATH" or Python "PYTHONPATH".

Examples:

    export PYTHONPATH=$PYTHONPATH:/home/daniel/Development/d7-computing/computing_java/pythonpkg
    export CLASSPATH=$CLASSPATH:/home/daniel/Development/d7-computing/computing_java/pythonpkg
  
It is beyond the scope of this documentation if you use Pip (for Python) or Maven (for Java) for dependency management. Refer to relevant documentation for details.


### Config.properties ###


Drush:

Services: 



Code Examples
-------------

Connect to Drupal Using Drush, and get node info:

    # requires jython 2.7+, and computing.jar in classpath.
    import json
    drush = DUtils.Drush("drush @dev")
    # execute any Drupal/PHP functions and get results in JSON.
    result = drush.computingEval("return node_load(1);")
    
    # parse the JSON results
    node = json.loads(result)
    
    print node['title']


Directly query Drupal database:

    DConfig config = new DConfig();
    // if you don't set this, the library will automatically tries to find settings.php
    config.setProperties("drupal.settings.file", "/drupal/sites/default/settings.php");
    // this will read db connection info from settings.php or other places
    Properties dbProperties = config.getDbProperties();
    
    // create direct database connection with JDBC and DBCP.
    DDatabase db = new DDatabase(dbProperties);
    
    // note here you can use {node} to take care of db_prefix, and type=? to take care of escaping.
    long count = (Long) db.queryValue("SELECT COUNT(*) FROM {node} WHERE type=?", "forum");
    System.out.println(count);


FAQ
---

#### Q: What are the differences between the Java implementation and the Python implementation? ####

A: The basic structure is the same, but details are different. See corresponding documentations.


Versions
--------

Version control follows the recommendations on Drupal.org.

* master: Current development branch.
* 7.x-2.x: This is the agent code for Drupal Computing module 7.x-2.x release.
* 7.x-1.x: This is the agent code for Drupal Computing module 7.x-1.x release. Obsolete.