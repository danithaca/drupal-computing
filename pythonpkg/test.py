from pprint import pprint
import unittest
import urllib.error
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
        config = load_default_config()

        self.assertEquals(config.get_drush_command() + config.get_drush_site_alias(), drush.get_drush_string())

        core_status = drush.get_core_status()
        self.assertEquals('7', core_status['drupal-version'][0])
        pprint(core_status)

        drush_version = drush.get_version()
        self.assertEquals('6', drush_version[0])

        #print(drush.execute(['status', '--pipe']),)
        node1 = drush.computing_call('node_load', 1)
        self.assertEquals('1', node1['nid'])
        var1 = drush.computing_eval('return variable_get("install_profile");')
        self.assertEquals('standard', var1)

    def testServices(self):
        services = load_default_services()
        # test connection
        self.assertTrue(services.check_connection())

        # test unauthorized access => exception
        self.assertFalse(services.is_authenticated())
        try:
            pprint(services.request('system/get_variable.json', {'name': 'install_profile'}, 'POST'))
            self.assertTrue(False)
        except urllib.error.HTTPError as e:
            self.assertEquals(403, e.code)

        # test login
        services.user_login()
        self.assertTrue(services.is_authenticated())

        # test authorized access => no exception
        var = services.request('system/get_variable.json', {'name': 'install_profile'}, 'POST')
        self.assertEquals('standard', var[0])
        n1 = services.request('node/1.json', None, 'GET')
        self.assertEquals('1', n1['nid'])

        # test logout
        services.user_logout()
        self.assertFalse(services.is_authenticated())




if __name__ == '__main__':
    # print(check_python_version())
    # print(get_agent_name())

    # config = DConfig('../config.properties')
    # config = DConfig()
    # print(config.get('path1', 'haha'))
    # print(config.get('dcomp.drush.site', 'haha'))
    #d = read_properties('../command.properties')
    #pprint(d)

    #drush = load_default_drush()
    # print(drush.execute(['computing-eval', '--pipe', '-'], 'return node_load(1);'))
    #print(drush.execute(['status', '--pipe']),)
    # pprint(drush.computing_call('variable_get', 'install_profile'),)
    # pprint(drush.computing_eval('return node_load(1);'),)
    # core_status = drush.get_core_status()
    # pprint(core_status)
    # pprint(drush.get_version())

    services = load_default_services()
    # result = services.request('system/connect.json', None, 'POST')
    # result = services.request('node/1.json', None, 'GET')
    # result = services.check_connection()
    # result = services.obtain_session_token()
    services.user_login()
    result = services.is_authenticated()
    services.user_logout()
    result = services.is_authenticated()
    pprint(result)
    # pprint(urllib.parse.urlencode({'a': 1, 'b': 2}))

