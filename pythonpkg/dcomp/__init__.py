from .base import DRecord, DCommand, DApplication, DSite, DDrushSite, DServicesSite, DSiteExtended, \
    ComputingApplication, EchoCommand

__author__ = 'Daniel Zhou'

if __name__ == '__main__':
    app = ComputingApplication()
    app.launch()