__author__ = 'Daniel Zhou'


class DConfig():
  def __init__(self):
    self.properties = {}


class DDrush():

  def __init__(self, drush_command, site_alias):
    self.drush_command = drush_command
    self.site_alias = site_alias

def check_python_version():
  import sys
  return sys.version_info[0] >= 3 or (sys.version_info[0] >= 2 and sys.version_info[1] >=7)


def get_agent_name():
  import socket
  return socket.gethostname()
