Drupal Computing Client
=======================

This is the Java and Python client library for [Drupal Computing module](http://drupal.org/project/computing). To use the same terminology, I'll call the client as "Agent".

Requirements:

  * For the Java client: Java SE 7+, Apache Commons Lang3 (included), Apache Commons Exec (included), Google Gson (included).
  * For the Python client: Python 3+
  
Current release (following the Drupal convention): 7.x-2.0-alpha1 (tag), in branch 7.x-2.0


Technical Features
------------------

  * Reusable Java/Python client code to access Drupal using Services (REST Server) and Drush.
  * Reusable code to read & write data to and from Drupal.
  * Multi-thread support (to be implemented).
  * MapReduce/Hadoop support (to be implemented).
  * Database connection pooling support for Java (to be implemented).
  

Install and Config
------------------

It is recommended to download this code to a different server than the Drupal production server.

You need to setup Java "CLASSPATH" or Python "PYTHONPATH".

Examples:

    export PYTHONPATH=$PYTHONPATH:/home/daniel/Development/d7-computing/computing_java/pythonpkg
    export CLASSPATH=$CLASSPATH:/home/daniel/Development/d7-computing/computing_java/pythonpkg
  
It is beyond the scope of this documentation if you use Pip (for Python) or Maven (for Java) for dependency management. Refer to relevant documentation for details.

The most important config file is config.properties, which specifies how the Agent program accesses Drupal. Usually you can save the file under the working directory, or define system variable: DCOMP_CONFIG_FILE. For the Java client, you can also specify the location using -Dcomp.config.file=...

To turn on debugging: Java ..., Python ...


### config.properties ###

Anything defined in this file can also be accessed with a system environment variable (replacing '.' with '_' and to upper case). For example, dcomp.command.file would be DCOMP_COMMAND_FILE.

Global settings:

  * __dcomp.command.file__: specifies the location of "command.properties" file.
  * __dcomp.site.access__: Specifies how to access Drupal using either "drush" (default) or "services".

Drush:

  * __dcomp.drush.command__: Specifies the "drush" system command (e.g., "/usr/bin/drush"). Default is "drush".
  * __dcomp.drush.site__: Specifies the default drush site alias to use. Default is "@self". You need to use '@'. See Drush documentation "site alias" for more details.

Services (see Drupal Computing documentation for details): 

  * __dcomp.site.base_url__ (required): Drupal site url, eg http://exmaple.com
  * __dcomp.services.endpoint__ (required): Services endpoint defined in Drupal
  * __dcomp.services.user.name__ (required): Drupal user's name to access Drupal
  * __dcomp.services.user.pass__ (required): Drupal user's password

Other settings:

  * __dcomp.database.url__: JDBC database url.
  * __dcomp.database.properties.*__: other database settings, e.g., dcomp.database.properties.username, dcomp.database.properties.password, etc. This will be used to establish database connections.
  * __dcomp.agent.name__: Specifies the agent name to distinguish between different agent programs. Default is the hostname.



Code Examples
-------------
    

Connect to Drupal Using Drush, and get node info:

    # requires python3, set drush alias in config.properties
    drush = dcomp.load_default_drush()
    
    # execute any Drupal/PHP functions and get results in JSON.
    node = drush.computing_eval("return node_load(1);")
    print(node['title'])
    
    user = drush.computing_call('user_load', 1)
    print(user['name'])


Use the "queue" mechanism and execute command through the use of Computing Record:

    # register command in command.properties:
    # check_python = CheckPython
    
    # then create a new python file example.py:
    
    class CheckPython(DCommand):
    
        # overrides execute() to run a command. 
        def execute(self):
            # save results in self.result, which will be saved to Computing Record "output" field.
            self.result['python'] = {'title': 'Python', 'version': sys.version, 'installed': True}
    
        # overrides prepare() to handle the parameters from Computing Record "input" field.
        def prepare(self, params): pass
        
    # run application
    if __name__ == '__main__':
    
        # create a new Computing Record, and specifies to run 'check_python' command
        # It will map to the "CheckPython" class as defined in command.properties.
        record = DRecord(application='computing', command='check_python', label='Check Python Libraries')
        
        # use run_once() to execute the command and save result back to Drupal via the use of Computing Record.
        with ComputingApplication() as app:
          app.run_once(record)


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

The basic structure is the same, but details are different. See corresponding documentations.
There are some differences between the Java client APIs and Python client APIs due to their different naming conventions. However, the structure is the same. 

#### Q: Is this project going to support languages other than Java and Python? ####

No for now. In the meantime, you can use:
you can also use Jython 2.7 and the Java client library. Also you can use Groovy, Scala, JRuby, Jython etc with the Java client library.

#### Q: Will this be distributed in Maven and PyPi?

Possibly. Please follow [issue #4](https://github.com/danithaca/drupal-computing/issues/4).

#### Q: Where can I find more info? ####

  * Read the Source Code.
  * For the Java client, you can acess javadoc at java/doc.
  * For the Python client, you can use dir() and help() to get more info.
  * Go to the project page on drupal.org (http://drupal.org/project/computing) to find more info about the module.