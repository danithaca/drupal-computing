from .base import ComputingApplication

if __name__ == '__main__':
    with ComputingApplication() as app:
        app.launch()