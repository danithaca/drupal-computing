Drupal Computing Clients
========================

This is the Java and Python agent client code for [Drupal Computing module](http://drupal.org/project/computing).

Requirements:

  * Java SE 7+
  * Python 3+ (vote support for Python 2.7 is planned at ...)
  
Current branch release: 7.x-2.0. (Follows Drupal release convention)


Setup
=====

You need JavaSE 7+ and Python 2.7+ (currently only supports Python 3.0+, see ...).

You need to setup Java "CLASSPATH" or Python "PYTHONPATH".

Examples:

    export PYTHONPATH=$PYTHONPATH:/home/daniel/Development/d7-computing/computing_java/pythonpkg
    export CLASSPATH=$CLASSPATH:/home/daniel/Development/d7-computing/computing_java/pythonpkg
  
It is beyond the scope of this documentation if you use Pip (for Python) or Maven (for Java) for dependency management. Refer to relevant documentation for details.



FAQ
===

**Q: What are the differences between the Java implementation and the Python implementation?**

A: The basic structure is the same, but details are different. See corresponding documentations.


Versions
========

Version control follows the recommendations on Drupal.org.

* master: Current development branch.
* 7.x-2.x: This is the agent code for Drupal Computing module 7.x-2.x release.
* 7.x-1.x: This is the agent code for Drupal Computing module 7.x-1.x release. Obsolete.