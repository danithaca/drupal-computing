Drupal Computing: Client Library
================================

This is the Java and Python client library for the [Drupal Computing module](http://drupal.org/project/computing) to help you write non-PHP programs, or _agent programs_, that interact with Drupal in a distributed computing environment.

Current release (following the Drupal convention): _7.x-2.0-alpha2_ in _7.x-2.x_ branch.

Requirements:

  * For the Java client: Java SE 7+, Apache Commons Lang3 (included), Apache Commons Exec (included), Google Gson (included).
  * For the Python client: Python 3.0+ (Python 3.4 recommended) or Python 2.7+ (Python 2.7 recommended), Python Six (https://pypi.python.org/pypi/six)


Technical Features
------------------

  * Simple and reusable Java/Python utility code to interact with Drupal using Services (REST Server) and Drush.
  * A framework to read, write and process data to/from Drupal.
  * Multi-thread support (to be implemented).
  * MapReduce/Hadoop support (to be implemented).
  * Database connection pooling support for Java (to be implemented).
  

Install and Config
------------------

First, install the [Drupal Computing module](http://drupal.org/project/computing) on your Drupal site. Next, download the Client Library code to the server where you would run the agent programs (usually on a different server than the Drupal server). On that server, setup "CLASSPATH" (for Java) and/or "PYTHONPATH" (for Python) for your agent programs to access the code library, e.g.:

    export DRUPAL_COMPUTING_HOME=/opt/drupal-computing
    export PYTHONPATH=PYTHONPATH=${PYTHONPATH}:${DRUPAL_COMPUTING_HOME}/python
    export CLASSPATH=${CLASSPATH}:${DRUPAL_COMPUTING_HOME}/java/computing.jar:${DRUPAL_COMPUTING_HOME}/java/lib/*
  
_(Note: It is beyond the scope of this documentation if you do dependency management using Maven (for Java) or pip/virtualenv (for Python). Refer to their documentations for details.)_

Finally, create and configure _config.properties_ file to access Drupal. Specify the file's location in system environment variable `DCOMP_CONFIG_FILE`, or save the file to the current working directory. If you prefer not to use this file, you can use environment variables instead (e.g., `dcomp.site.access` would be env var `DCOMP_SITE_ACCESS`).

### config.properties ###

Required settings:

  * __dcomp.site.access__: Specifies whether to access Drupal using either "drush" (default) or "services".
  * __dcomp.command.file__: Specifies the location of "command.properties" file, which maps addition "command" string into a Python/Java command class. 

Drush settings (required if using Drush):

  * __dcomp.drush.site__: Specifies the default drush site alias. Default is "@self" (including '@'). See Drush documentation "site alias" for more details.
  * __dcomp.drush.command__: Specifies the "drush" system command (e.g., "/usr/bin/drush"). Default is "drush".

Services settings (see Drupal Computing documentation and Services module documentation for details): 

  * __dcomp.site.base_url__ (required): Drupal site url, e.g. http://exmaple.com
  * __dcomp.services.endpoint__ (required): Services endpoint, defined in Drupal
  * __dcomp.services.user.name__ (required): Drupal user's name
  * __dcomp.services.user.pass__ (required): Drupal user's password

Optional settings:

  * __dcomp.database.url__: JDBC database connection url, which overrides all settings below, if any.
  * __dcomp.database.properties.*__: Other database settings, e.g., dcomp.database.properties.username, dcomp.database.properties.password, etc, if you prefer this than dcomp.database.url.
  * __dcomp.agent.name__: The name of the agent program to distinguish in Drupal site. Default is the agent server's hostname.
  * __dcomp.exec.timeout__: Maximum milliseconds to execute command line programs (e.g., the drush executable). Default is 120000, or 2 minutes.
  * __dcomp.processing.batch_size__: Specifies how many computing record to process in one single run. Default is 100.



Code Examples
-------------

Most of these examples are for Python. The Java code would be similar.

Use one-shot script to access Drupal using Drush and Services, and print node/user info:

    # code copied from python/dcomp_example.py
    
    # turn on debugging
    logging.basicConfig(level=logging.DEBUG)

    # requires 'drush alias' in config.properties.
    drush = dcomp.load_default_drush()

    # execute any Drupal/PHP functions and get results in JSON.
    n1 = drush.computing_eval("$nid = 1; return node_load($nid);")
    print('Node name (using Drush): %s' % n1['title'])

    # 'computing_call' is to execute any one drupal function.
    u1 = drush.computing_call('user_load', 1)
    print('User name (using Drush): %s' % u1['name'])

    # use services module. access info defined in config.properties.
    # requires proper Drupal permissions and Services resources to be able to run successfully.
    # see the Drupal Computing documentation for more details.
    services = dcomp.load_default_services()
    services.check_connection()
    services.user_login()

    # see the list of things you can do at: https://www.drupal.org/node/783254
    n2 = services.request('node/1.json', None, 'GET')
    print('Node name (using Services): %s' % n2['title'])

    # get drupal variable
    v1 = services.request('system/get_variable.json', {'name': 'install_profile', 'default': 'n/a'}, 'POST')
    print('"install_profile" drupal variable: %s' % v1)

Use the "computing record" framework to run agent programs in a systematic way:

    # code copied from Drupal's "machine_learning" module.
    # make sure to register command in 'command.properties' file: check_python = CheckPython
    
    # then create a new python file check.py:
    
    class CheckPython(DCommand):
        # overrides execute() to run a command. 
        def execute(self):
            # save data in self.result to be saved automatically to Computing Record "output" field
            # which you can later access in Drupal. 
            self.result['python'] = {'title': 'Python', 'version': sys.version, 'installed': True}
    
        # overrides prepare() to handle the parameters from Computing Record "input" field
        # which is an easy way to feed data from Drupal into agent programs using either Drush or Services.
        # this particular example doesn't take advantage of this.
        def prepare(self, params): pass
        
    # run application
    if __name__ == '__main__':
    
        # create a new Computing Record, and specifies to run 'check_python' command
        # It will map to the "CheckPython" class as defined in command.properties.
        record = DRecord(application='computing', command='check_python', label='Check Python Libraries')
        
        # use run_once() to execute the command and save result back to Drupal via the use of Computing Record.
        with ComputingApplication() as app:
        
          # after 'run_once', you'll see a new Computing Record entity created in Drupal.
          app.run_once(record)
          
          # instead of using "run_once" with a new computing record, you can process computing records already created in Drupal
          # you would normally use this approach is Drupal initiates a computing request with data saved in "input".
          # app.launch()


FAQ
---

#### Q: Any difference between the Java client and the Python client? ####

The Java client and Python client use different naming conventions. But the code structures are very similar.

Java is a "strong type" language and does not have native support for a flexible "JSON Object" data type. The Java client uses `javax.script.Bindings` for data in "JSON Object", and you would see many lines of code just to do data type conversion, which are not present in the Python client.

#### Q: Can I use languages other than Java and Python? ####

You can use the Java client to work with JRuby, Groovy, Scala, etc. You can also use Jython 2.x with the Java client for Python 2.6 and below. To use R, you can try rpy2 (for Python/R) or JRI (for Java/R). Native support for other languages are not planned.

#### Q: Will this be distributed in Maven and PyPi?

Possibly. Please follow [issue #4](https://github.com/danithaca/drupal-computing/issues/4).

#### Q: Where to find more info? ####

  * Read the source code.
  * Read javadoc at java/doc (for the Java client).
  * Use `dir()` and `help()` to get docstring help info (for the Python client)
  * Learn more about the [Drupal Computing module](http://drupal.org/project/computing) on drupal.org.
  