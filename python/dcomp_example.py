import logging
import dcomp


def simple_script():
    # turn on debugging
    logging.basicConfig(level=logging.DEBUG)

    # requires 'drush alias' in config.properties.
    drush = dcomp.load_default_drush()

    # execute any Drupal/PHP functions and get results in JSON.
    n1 = drush.computing_eval("$nid = 1; return node_load($nid);")
    print('Node name (using Drush): %s' % n1['title'])

    # 'computing_call' is to execute any one drupal function.
    u1 = drush.computing_call('user_load', 1)
    print('User name (using Drush): %s' % u1['name'])

    # use services module. access info defined in config.properties.
    # requires proper Drupal permissions and Services resources to be able to run successfully.
    # see the Drupal Computing documentation for more details.
    services = dcomp.load_default_services()
    services.check_connection()
    services.user_login()

    # see the list of things you can do at: https://www.drupal.org/node/783254
    n2 = services.request('node/1.json', None, 'GET')
    print('Node name (using Services): %s' % n2['title'])

    # get drupal variable
    v1 = services.request('system/get_variable.json', {'name': 'install_profile', 'default': 'n/a'}, 'POST')
    print('"install_profile" drupal variable: %s' % v1)


if __name__ == '__main__':
    simple_script()