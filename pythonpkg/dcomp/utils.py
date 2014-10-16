import os
import subprocess
import sys
import socket
import re
import logging
import json
from io import StringIO, TextIOWrapper
import traceback
from .exceptions import DSiteException

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
        Might throw:CalledProcessError, TimeExpired
        """
        config = load_default_config()
        timeout = int(config.get('dcomp.exec.timeout', 120000))

        all_args = [self.drush_command, self.site_alias]
        if extra_args is not None:
            all_args.extend(extra_args)

        # TODO: handle error output and exceptions.
        return subprocess.check_output(all_args, input=input_string, universal_newlines=True, timeout=timeout)

    def computing_call(self, *args):
        call_args = ['computing-call', '--pipe']
        for arg in call_args:
            call_args.append(json.dumps(arg))
        return self.execute(call_args)

    def computing_eval(self, code):
        eval_args = ['computing-eval', '--pipe', '-']
        return self.execute(eval_args, code)


_default_drush = None


def load_default_drush(reload=False):
    global _default_drush
    # lazy initialization
    if _default_drush is None or reload:
        config = load_default_config()
        _default_drush = DDrush(config.get_drush_command(), config.get_drush_site_alias())
    return _default_drush


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
