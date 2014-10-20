from abc import ABCMeta, abstractmethod
import json
import logging
from .utils import load_default_drush, load_default_config, load_default_services, get_class, read_properties

__author__ = 'daniel'


class DRecord(object):
    def __init__(self, **entries):
        """
        Code copied from:
        http://stackoverflow.com/questions/1305532/convert-python-dict-to-object
        :param entries: dictionary type of input.
        :return: the DRecord object
        """
        # special handle when those are strings instead of int, which can happen from json.
        for k in entries:
            if k in ('id', 'created', 'changed', 'uid', 'weight'):
                entries[k] = int(entries[k])
        self.__dict__.update(entries)

    def is_new(self):
        return not hasattr(self, 'id') or self.id is None

    def get(self, field_name, default_value=None):
        return self.__dict__.get(field_name, default_value)

    def to_json(self):
        return json.dumps(self.__dict__)

    def to_dict(self, keeponly=None, keepout=None):
        if keeponly is not None and len(keeponly) > 0:
            return {k: v for k, v in self.__dict__.items() if k in keeponly}
        elif keepout is not None and len(keepout) > 0:
            return {k: v for k, v in self.__dict__.items() if k not in keepout}
        else:
            return {k: v for k, v in self.__dict__.items()}


class DSite(metaclass=ABCMeta):

    def check_connection(self):
        version = self.get_drupal_version()
        return len(version) > 0 and version[0] in ('6', '7', '8')

    @abstractmethod
    def get_drupal_version(self): pass

    @abstractmethod
    def get_timestamp(self): pass

    @abstractmethod
    def load_record(self, record_id): pass
    
    @abstractmethod
    def create_record(self, record): pass

    @abstractmethod
    def claim_record(self, app_name):
        """Return None if not found."""
        pass

    @abstractmethod
    def update_record(self, record): pass

    @abstractmethod
    def update_record_field(self, record, field_name): pass

    @abstractmethod
    def finish_record(self, record): pass
    

class DSiteExtended(metaclass=ABCMeta):
    
    @abstractmethod
    def get_variable(self, name, default=None): pass

    @abstractmethod
    def set_variable(self, name, value): pass


def create_default_drush_connection():
    drush = load_default_drush()
    return DDrushSite(drush)


class DDrushSite(DSite):
    def __init__(self, drush):
        self.drush = drush

    def get_drupal_version(self):
        status = self.drush.get_core_status()
        return status['drupal-version']

    def get_timestamp(self):
        return int(self.drush.computing_call('time'))
    
    def load_record(self, record_id):
        record_dict = self.drush.computing_call('computing_load', record_id)
        return DRecord(**record_dict)

    def create_record(self, record):
        assert record.is_new() and record.application is not None and record.command is not None
        result = self.drush.computing_call(
            'computing_create',
            record.application,
            record.command,
            record.get('label') if record.get('label') is not None else "Process %s" % record.command,
            record.get('input'),
            record.to_dict(keepout=('application', 'command', 'label', 'input')))
        return result

    def claim_record(self, app_name):
        result = self.drush.computing_call('computing_claim', app_name)
        if isinstance(result, bool):
            return None
        else:
            return DRecord(**result)

    def update_record(self, record):
        assert not record.is_new()
        # we have to use record.to_dict() so that json.dumps() knows how to encode it.
        return self.drush.computing_call('computing_update', record.to_dict())

    def update_record_field(self, record, field_name):
        assert not record.is_new()
        return self.drush.computing_call('computing_update_field', record.id, field_name, record.get(field_name))

    def finish_record(self, record):
        assert not record.is_new()
        # 'id' and 'status' are required. 'message' and 'output' are not required so we use record.get().
        return self.drush.computing_call('computing_finish', record.id, record.status, record.get('message'), record.get('output'))


def create_default_services_connection():
    services = load_default_services()
    return DServicesSite(services)


class DServicesSite(DSite):
    def __init__(self, services):
        self.services = services

    def connect(self):
        if not self.services.is_authenticated():
            self.services.user_login()
        else:
            logging.warning('User already logged in. Do nothing.')

    def close(self):
        if self.services.is_authenticated():
            self.services.user_logout()
        else:
            logging.warning('User already logged out. Do nothing.')

    def get_site_info(self):
        assert self.services.is_authenticated()
        return self.services.request('computing/info.json', None, 'POST')

    def get_drupal_version(self):
        assert self.services.is_authenticated()
        info = self.get_site_info()
        return info['drupal_version']

    def get_timestamp(self):
        assert self.services.is_authenticated()
        info = self.get_site_info()
        return info['drupal_time']

    def load_record(self, record_id):
        assert self.services.is_authenticated()
        record_dict = self.services.request('computing/%d.json' % record_id, None, 'GET')
        return DRecord(**record_dict)

    def create_record(self, record):
        assert self.services.is_authenticated() and record.is_new() and record.application is not None and record.command is not None
        result = self.services.request('computing.json', record.to_dict(), 'POST')
        # for some reason result is a list. so we retrieve the first item.
        return result[0]

    def claim_record(self, app_name):
        assert self.services.is_authenticated()
        result = self.services.request('computing/claim.json', {'application': app_name}, 'POST')
        if isinstance(result, list) and isinstance(result[0], bool) and not result[0]:
            return None
        else:
            return DRecord(**result)

    def update_record(self, record):
        assert not record.is_new() and self.services.is_authenticated()
        # we have to use record.to_dict() so that json.dumps() knows how to encode it.
        return self.services.request('computing/%d.json' % record.id, record.to_dict(), 'PUT')

    def update_record_field(self, record, field_name):
        assert not record.is_new() and self.services.is_authenticated()
        params = {'name': field_name, 'value': record.get(field_name)}
        return self.services.request('computing/%d/field.json' % record.id, params, 'POST')

    def finish_record(self, record):
        assert not record.is_new() and self.services.is_authenticated()
        # 'id' and 'status' are required. 'message' and 'output' are not required so we use record.get().
        params = {'status': record.status, 'message': record.get('message', 'N/A')}
        if record.get('output') is not None:
            params['output'] = record.get('output')
        return self.services.request('computing/%d/finish.json' % record.id, params, 'POST')

    def check_connection(self):
        return self.services.check_connection()


class DCommand(object, metaclass=ABCMeta):

    def __init__(self):
        self.status = None
        self.message = None
        self.result = {}

    @abstractmethod
    def prepare(self, params):
        """Prepare the command."""
        pass

    @abstractmethod
    def execute(self):
        """Run the command, and return status, message, and output."""
        pass

    def set_context(self, record, site, application, config):
        self.record = record
        self.site = site
        self.application = application
        self.config = config


class DApplication(object, metaclass=ABCMeta):

    def __init__(self, app_name):
        self.app_name = app_name
        self.config = load_default_config()

        # build command mapping
        self.command_mapping = {}
        # 1. load declares in code.
        self.command_mapping.update(self.declare_command_mapping())
        # 2. load from command files.
        try:
            custom_command_mapping = read_properties(self.config.get("dcomp.command.file", "command.properties"))
            if len(custom_command_mapping) > 0:
                self.command_mapping.update(custom_command_mapping)
        except FileNotFoundError as e:
            logging.warning('Cannot locate and/or read command mapping file. Use defaults.')
        logging.info('Command mapping: %s' %(str(self.command_mapping)))

        # create drupal site connection
        site_access = self.config.get("dcomp.site.access", "drush")
        if site_access == 'services':
            logging.info('Access Drupal using Services module.')
            self.site = create_default_services_connection()
            self.site.connect()
        else:
            logging.info('Access Drupal using Drush.')
            self.site = create_default_drush_connection()

        # check drupal connection without breaking the program on error
        if not self.site.check_connection():
            logging.warning('Drupal access is not validated.')

    def launch(self):
        assert self.app_name is not None and self.site is not None and self.config is not None and self.command_mapping is not None
        self.launch_single_thread()

    def launch_single_thread(self):
        batch_size = int(self.config.get('dcomp.processing.batch_size', 100))
        for i in range(batch_size):
            record = self.site.claim_record(self.app_name)
            if record is None:
                logging.info('No more record with READY status for application: %s' % self.app_name)
                break
            # this is the main execution
            # TODO: handle exceptions.
            self.process_record(record)
            self.site.finish_record(record)
        else:
            logging.info('Finished processing %d records.' % batch_size)

    def run_once(self, record):
        assert record is not None and self.app_name is not None and self.site is not None and self.config is not None and self.command_mapping is not None

        if record.is_new():
            record.status = 'RUN'
            record_id = self.site.create_record(record)
            record = self.site.load_record(record_id)

        self.process_record(record)
        self.site.finish_record(record)
        return self.site.load_record(record.id)

    def process_record(self, record):
        assert record is not None and not record.is_new() and record.application == self.app_name and record.command is not None
        logging.info('Preparing to executing command: %s (%d)' % (record.command, record.id))

        try:
            # instantiate command object
            command_class = get_class(self.command_mapping.get(record.command, record.command))
            c = command_class()
            c.set_context(record, self.site, self, self.config)

            # execution
            c.prepare(record.input)
            c.execute()

            # take care of result
            record.status = c.status if hasattr(c, 'status') and c.status is not None and len(c.status) == 3 else 'SCF'
            record.message = c.message if hasattr(c, 'message') and c.message is not None else 'Run command "%s" successful.' % record.command
            record.output = c.result if hasattr(c, 'result') else None

        except Exception as e:
            # handle problems
            record.status = 'FLD'
            record.message = e.message

    @abstractmethod
    def declare_command_mapping(self):
        """Return a {} that has command mapping."""
        pass


class ComputingApplication(DApplication):
    def __init__(self):
        super(ComputingApplication, self).__init__('computing')

    def declare_command_mapping(self):
        return {
            'Echo': 'dcomp.base.EchoCommand',
            'echo': 'dcomp.base.EchoCommand'
        }


class EchoCommand(DCommand):
    def __init__(self):
        super(EchoCommand, self).__init__()
        self.ping = None

    def prepare(self, params):
        self.ping = params.get('ping')

    def execute(self):
        self.result['pong'] = self.ping

