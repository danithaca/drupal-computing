package org.drupal.project.computing.v1.test;

import org.drupal.project.computing.v1.*;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        DConfig.UnitTest.class,
        DDatabase.UnitTest.class,
        DDrushSite.UnitTest.class,
        DSqlSite.UnitTest.class,
        DUtils.UnitTest.class
})

public class ComputingTestSuite {
}
