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


    def testDrush(self):
        drush = load_default_drush()
        print(drush.execute(['status', '--pipe']))
        print(drush.computing_call('node_load', 1))
        print(drush.computing_eval('return node_load(1);'))


if __name__ == '__main__':
    # print(check_python_version())
    # print(get_agent_name())

    # config = DConfig('../config.properties')
    # config = DConfig()
    # print(config.get('path1', 'haha'))
    # print(config.get('dcomp.drush.site', 'haha'))
    #d = read_properties('../command.properties')
    #pprint(d)

    drush = load_default_drush()
    print(drush.execute(['computing-eval', '--pipe', '-'], 'return node_load(1);'))