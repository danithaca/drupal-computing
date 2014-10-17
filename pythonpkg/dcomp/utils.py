import os
import subprocess
import sys
import socket
import re
import logging
import json
import urllib.request
import urllib.parse

__author__ = 'Daniel Zhou'


class DConfig():
    """
    This class helps read configurations for Drupal python agent.
    """

    def __init__(self, filename=None):
        """ Load settings from "filename" if given, or load settings from config.properties
            specified in OS ENV or the current working folder.
        """

        self.logger = get_logger()
        self.properties = {}

        if filename is None:
            filename = self.get('dcomp.config.file', 'config.properties')
        assert filename is not None

        # read files and add to properties.
        try:
            config_file_properties = read_properties(filename)
            self.properties.update(config_file_properties)
            self.logger.info('Use configuration in: "%s"' % filename)
        except FileNotFoundError:
            self.logger.warning('Cannot find config file: "%s". Use defaults.' % filename)

    def get(self, key, value=None):
        """Try to get config settings from config.properties or system environment variables."""

        # 1. try get config from local properties.
        result = self.properties.get(key, None)
        # 2. or try to get from system settings.
        if result is None:
            env_key = key.replace('.', '_').upper()
            result = os.getenv(env_key, None)
        return result if result is not None else value

    def set(self, key, value):
        self.properties[key] = value

    def get_drush_command(self):
        return self.get('dcomp.drush.command', 'drush')

    def get_drush_site_alias(self):
        return self.get('dcomp.drush.site', '@self')

    def get_agent_name(self):
        name = self.get('dcomp.agent.name')
        return name if name is not None else socket.gethostname()


_default_config = None


def load_default_config(reload=False):
    global _default_config
    # lazy initialization
    if _default_config is None or reload:
        _default_config = DConfig()
    return _default_config


class DDrush():
    def __init__(self, drush_command, site_alias):
        self.drush_command = drush_command
        self.site_alias = site_alias

    def execute(self, extra_args=[], input_string=None):
        """
        This does not handle possible exceptions. Caller functions should take care of them.
        :except: CalledProcessError, TimeExpired
        """
        config = load_default_config()
        timeout = int(config.get('dcomp.exec.timeout', 120000))

        all_args = [self.drush_command, self.site_alias]
        if extra_args is not None:
            all_args.extend(extra_args)

        # TODO: handle error output and exceptions.
        return subprocess.check_output(all_args, input=input_string, universal_newlines=True, timeout=timeout)

    def computing_call_raw(self, func_name, *args):
        calls = ['computing-call', '--pipe', func_name]
        for arg in args:
            calls.append(json.dumps(arg))
        return self.execute(calls)

    def computing_call(self, func_name, *args):
        json_result = self.computing_call_raw(func_name, *args)
        return json.loads(json_result)

    def computing_eval_raw(self, code):
        eval_args = ['computing-eval', '--pipe', '-']
        return self.execute(eval_args, code)

    def computing_eval(self, code):
        json_result = self.computing_eval_raw(code)
        return json.loads(json_result)

    def get_core_status(self):
        return json.loads(self.execute(["core-status", "--pipe", "--format=json"]))

    def get_drush_string(self):
        return "%s %s" % (self.drush_command, self.site_alias)

    def get_version(self):
        return self.execute(["version", "--pipe"]).strip()


_default_drush = None


def load_default_drush(reload=False):
    global _default_drush
    # lazy initialization
    if _default_drush is None or reload:
        config = load_default_config()
        _default_drush = DDrush(config.get_drush_command(), config.get_drush_site_alias())
    return _default_drush


class DRestfulJsonServices():

    def __init__(self, base_url, endpoint, username, password):
        self.base_url = base_url.strip()
        # remove left '/' if any
        self.endpoint = endpoint.strip().lstrip('/')
        self.username = username.strip()
        self.password = password.strip()

        # set other things
        self.services_link = "%s/%s" % (base_url, endpoint)
        self.services_session_token = None
        self.http_user_agent = 'DrupalComputingAgent'
        self.http_content_type = 'application/json'
        self.logger = get_logger()

        # set cookie handler

        # first, create an opener than has cookie support.
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor())
        # then install the opener to request instead of using the default BaseHandler.
        urllib.request.install_opener(opener)

    def request(self, directive, params, method):
        """
        Make request to Drupal services.
        See https://www.drupal.org/node/783254 for a list of RESTful directives.
        :param directive: eg system/connect.json, node/1.json, variable_get.json, etc.
        :param params: data in python {}
        :param method: GET, POST, PUT, DELETE, etc.
        :return: the JSON object from Drupal services.
        :exception: HTTPError
        """

        data = None
        link = "%s/%s" % (self.services_link, directive)

        if method == 'GET':
            if params is not None:
                query_string = urllib.parse.urlencode(params)
                link = "%s?%s" % (link, query_string)

        elif method == 'POST' or method == 'PUT':
            if params is not None:
                data = json.dumps(params).encode('utf-8')

        # process request
        self.logger.info('Making connection to: %s' % link)
        headers = {'User-Agent': self.http_user_agent}
        if data is not None:
            headers['Content-Type'] = self.http_content_type
            # I assume 'Content-Length' is handled in urllib.request.Request
            # header['Content-Length'] = len(data)
        if self.services_session_token is not None:
            headers['X-CSRF-Token'] = self.services_session_token

        request = urllib.request.Request(link, data=data, headers=headers, method=method)
        # this is the actually connection.
        response = urllib.request.urlopen(request)

        raw_content = response.read()
        return json.loads(raw_content.decode('utf-8'))

    def check_connection(self):
        """
        Check connection to Drupal Services.
        :return: True if connection successful, or False.
        """
        result = self.request('system/connect.json', None, 'POST')
        self.logger.info("Checking connection to '%s/system/connect.json' returns: %s" % (self.services_link, json.dumps(result)))
        return True if 'sessid' in result and len(result['sessid']) > 0 else False

    def is_authenticated(self):
        return self.services_session_token is not None

    def obtain_session_token(self):
        link = "%s/services/session/token" % self.base_url
        return urllib.request.urlopen(link).read().decode('utf-8')

    def user_login(self):
        params = {'username': self.username, 'password': self.password}
        result = self.request('user/login.json', params, 'POST')
        if 'token' in result and len(result['token']) > 0:
            self.services_session_token = result['token']
            self.logger.info('User login successful: %s' % self.username)
        else:
            self.logger.error('User login failed: %s' % self.username)

    def user_logout(self):
        result = self.request('user/logout.json', None, 'POST')
        self.services_session_token = None
        self.logger.info('User logout successful: %s' % self.username)


_default_services = None


def load_default_services(reload=False):
    global _default_services
    # lazy initialization
    if _default_services is None or reload:
        config = load_default_config()
        base_url = config.get('dcomp.site.base_url')
        endpoint = config.get('dcomp.services.endpoint')
        username = config.get('dcomp.services.user.name')
        password = config.get('dcomp.services.user.pass')
        if base_url is None or not base_url.startswith('http') or endpoint is None or username is None or password is None:
            logger = get_logger()
            logger.warning('Services configuration problem. Connection to Drupal Services is not guaranteed.')
        # initialize services.
        _default_services = DRestfulJsonServices(base_url, endpoint, username, password)
    return _default_services


def check_python_version():
    return sys.version_info[0] >= 3 or (sys.version_info[0] >= 2 and sys.version_info[1] >= 7)


def get_logger():
    return logging.getLogger('dcomp')


def read_properties(filename):
    """
    This is a helper function to read java properties file, which is "sectionless" and can't be handled directly by python configparser.
    see http://code.activestate.com/recipes/496795-a-python-replacement-for-javautilproperties/
    see http://stackoverflow.com/questions/17747627/configparser-set-with-no-section
    :param filename: the java properties file.
    :return: dict object of the properties.
    """

    def unescape(value):
        newvalue = value.replace('\:',':')
        newvalue = newvalue.replace('\=','=')
        return newvalue

    props, keymap, origprops = {}, {}, {}

    with open(filename) as f:
        lines = f.readlines()

    othercharre = re.compile(r'(?<!\\)(\s*\=)|(?<!\\)(\s*\:)')
    othercharre2 = re.compile(r'(\s*\=)|(\s*\:)')
    bspacere = re.compile(r'\\(?!\s$)')

    lineno=0
    i = iter(lines)
    for line in i:
        lineno += 1
        line = line.strip()
        if not line: continue
        if line[0] == '#' or line[0] == ';': continue
        escaped = False
        sepidx = -1
        flag = 0
        m = othercharre.search(line)
        if m:
            first, last = m.span()
            start, end = 0, first
            flag = 1
            wspacere = re.compile(r'(?<![\\\=\:])(\s)')
        else:
            if othercharre2.search(line):
                wspacere = re.compile(r'(?<![\\])(\s)')
            start, end = 0, len(line)

        m2 = wspacere.search(line, start, end)
        if m2:
            first, last = m2.span()
            sepidx = first
        elif m:
            first, last = m.span()
            sepidx = last - 1

        while line[-1] == '\\':
            nextline = i.next()
            nextline = nextline.strip()
            lineno += 1
            line = line[:-1] + nextline

        if sepidx != -1:
            key, value = line[:sepidx], line[sepidx+1:]
        else:
            key, value = line, ''

        oldkey = key
        oldvalue = value
        keyparts = bspacere.split(key)

        strippable = False
        lastpart = keyparts[-1]

        if lastpart.find('\\ ') != -1:
            keyparts[-1] = lastpart.replace('\\','')

        elif lastpart and lastpart[-1] == ' ':
            strippable = True

        key = ''.join(keyparts)
        if strippable:
            key = key.strip()
            oldkey = oldkey.strip()

        oldvalue = unescape(oldvalue)
        value = unescape(value)

        props[key] = value.strip()

        if key in keymap:
            oldkey = keymap.get(key)
            origprops[oldkey] = oldvalue.strip()
        else:
            origprops[oldkey] = oldvalue.strip()
            keymap[key] = oldkey

    # return the dict
    return props
