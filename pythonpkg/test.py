import unittest
from dcomp.utils import *


class TestUtils(unittest.TestCase):

    def testMisc(self):
        self.assertTrue(check_python_version())

    def testConfig(self):
        config = load_default_config()
        self.assertEquals(None, config.get('xoxo'))
        self.assertEquals('drush', config.get_drush_command())
        print(config.get_agent_name())


if __name__ == '__main__':
    # print(check_python_version())
    # print(get_agent_name())

    # config = DConfig('../config.properties')
    config = DConfig()
    print(config.get('path1', 'haha'))
    print(config.get('dcomp.drush.site', 'haha'))
    #d = read_properties('../command.properties')
    #pprint(d)