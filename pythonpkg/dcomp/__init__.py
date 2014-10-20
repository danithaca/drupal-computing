from .base import DRecord, DCommand, DApplication, DSite, DDrushSite, DServicesSite, DSiteExtended, \
    ComputingApplication, EchoCommand
from .utils import load_default_drush, load_default_config, load_default_services, get_class, read_properties, \
    DConfig, DDrush, DRestfulJsonServices, check_python_version

__author__ = 'Daniel Zhou'
__version__ = '7.x-2.0-alpha1'

if __name__ == '__main__':
    app = ComputingApplication()
    app.launch()