from pprint import pprint
import unittest
import urllib.error
from dcomp.utils import *
from dcomp.basic import *


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

    def testRecord(self):
        record = DRecord(application='computing', command='echo', input={'message': 'hello,world'}, label='python unittest')
        self.assertEquals('echo', record.get('command'))
        self.assertEquals(None, record.get('foo'))
        self.assertEquals('computing', record.application)
        self.assertTrue(record.is_new())

        d1 = record.to_dict(keeponly=('application'))
        self.assertTrue('application' in d1)
        self.assertTrue('command' not in d1)

        d1 = record.to_dict(keepout=('application'))
        self.assertTrue('application' not in d1)
        self.assertTrue('command' in d1)

    def testServicesSite(self):
        site = create_default_services_connection()
        site.connect()

        # check connection, version, timestamp
        self.assertTrue(site.check_connection())
        drupal_version = site.get_drupal_version()
        self.assertEquals('7', drupal_version[0])
        ts = site.get_timestamp()
        self.assertTrue(ts > 0)

        # create record, load
        record = DRecord(application='computing', command='echo', input={'message': 'hello,world'}, label='python unittest')
        id1 = site.create_record(record)
        self.assertTrue(id1 > 0)
        self.assertTrue(record.is_new())
        r2 = site.load_record(id1)
        self.assertFalse(r2.is_new())
        self.assertEquals('RDY', r2.status)

        # update/save record
        r2.message = 'hello'
        site.update_record(r2)
        r3 = site.load_record(r2.id)
        self.assertEquals('hello', r2.message)
        r3.output = {'message': 'bar'}
        site.update_record_field(r3, 'output')
        r4 = site.load_record(r3.id)
        self.assertEquals('bar', r4.output['message'])

        # claim/finish
        r5 = site.claim_record('computing')
        self.assertFalse(r5.is_new())
        self.assertEqual('RUN', r5.status)
        r5.status = 'SCF'
        r5.message = 'works'
        site.finish_record(r5)
        r6 = site.load_record(r5.id)
        self.assertEqual('SCF', r6.status)
        self.assertEqual('works', r6.message)

        # claim not exist
        r6 = site.claim_record('foobar')
        self.assertIsNone(r6)
        site.close()

    def testDrushSite(self):
        site = create_default_drush_connection()

        # check connection, version, timestamp
        self.assertTrue(site.check_connection())
        drupal_version = site.get_drupal_version()
        self.assertEquals('7', drupal_version[0])
        ts = site.get_timestamp()
        self.assertTrue(ts > 0)

        # create record, load
        record = DRecord(application='computing', command='echo', input={'message': 'hello,world'}, label='python unittest')
        id1 = site.create_record(record)
        pprint(id1)
        self.assertTrue(id1 > 0)
        self.assertTrue(record.is_new())
        r2 = site.load_record(id1)
        self.assertFalse(r2.is_new())
        self.assertEquals('RDY', r2.status)

        # update/save record
        r2.message = 'hello'
        site.update_record(r2)
        r3 = site.load_record(r2.id)
        self.assertEquals('hello', r2.message)
        r3.output = {'message': 'bar'}
        site.update_record_field(r3, 'output')
        r4 = site.load_record(r3.id)
        self.assertEquals('bar', r4.output['message'])

        # claim/finish
        r5 = site.claim_record('computing')
        self.assertFalse(r5.is_new())
        self.assertEqual('RUN', r5.status)
        r5.status = 'SCF'
        r5.message = 'works'
        site.finish_record(r5)
        r6 = site.load_record(r5.id)
        self.assertEqual('SCF', r6.status)
        self.assertEqual('works', r6.message)

        # claim not exist
        r6 = site.claim_record('foobar')
        self.assertIsNone(r6)



if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
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

    # services = load_default_services()
    # result = services.request('system/connect.json', None, 'POST')
    # result = services.request('node/1.json', None, 'GET')
    # result = services.check_connection()
    # # result = services.obtain_session_token()
    # services.user_login()
    # result = services.is_authenticated()
    # services.user_logout()
    # result = services.is_authenticated()
    # pprint(result)
    # pprint(urllib.parse.urlencode({'a': 1, 'b': 2}))

    # record = DRecord(application='computing', command='echo', input={'message': 'hello,world'})
    # pprint(record)
    # pprint(record.application)
    # pprint(record.to_json())

    site = create_default_drush_connection()
    pprint(site.get_timestamp())

