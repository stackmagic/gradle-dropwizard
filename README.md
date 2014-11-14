
gradle-dropwizard
=================

Gradle plugin to

* start/stop dropwizard manually
* start/stop dropwizard as part of your integration/acceptance test tasks (these tasks are created by the plugin)
* build a runnable jar file

> **WARNING**: this plugin has only been tested on Debian/Linux and relies on `java.lang.UNIXProcess`. It is unknown if it works on a Mac but it will most definitely break on Windows! Hack away on your platforms and submit a pull request :)

[ ![TravicCI](https://travis-ci.org/stackmagic/gradle-dropwizard.svg?branch=master) ](https://travis-ci.org/stackmagic/gradle-dropwizard)
[ ![Download](https://api.bintray.com/packages/stackmagic/maven/gradle-dropwizard/images/download.svg) ](https://bintray.com/stackmagic/maven/gradle-dropwizard/_latestVersion)

related work
============

* [dropwizard](http://dropwizard.io): Dropwizard itself, obviously
* [dropwizard-utils](https://github.com/stackmagic/dropwizard-utils): Various DW utils

For a sample project, have a look at [stackmagic/gradle-dropwizard-sample](https://github.com/stackmagic/gradle-dropwizard-sample)

downloading
===========

gradle
------

the [jcenter() shortcut requires at least gradle 1.7](http://www.gradle.org/docs/1.7/release-notes#jcenter-repository-support)

```groovy
buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        compile 'net.swisstech:gradle-dropwizard:+'
    }
}
```

usage
=====

This plugin will add a `dropwizardRun` task which is similar to `jettyRun`: it starts the server and blocks until interrupted via Ctrl-C. It will also add a `shadowJar` task (reusing the plugin [com.github.johnrengelman.shadow](https://github.com/johnrengelman/shadow/)) to create a shaded jar with merged service files so the jar can be run with `java -jar your.jar`.

Usage in your project should roughly look like this. See also: [gradle-dropwizard-sample](https://github.com/stackmagic/gradle-dropwizard-sample).

```groovy

// you *must* add a configuration for either int/acc tests.
// the naming must match the config below in the dropwizard closure

configurations {
	// dependency configuration for intTest task (task is added by dropwizard plugin)
	intTestCompile

	// dependency configuration for accTest task (task is added by dropwizard plugin)
	accTestCompile
}

// add your dependencies as usual

dependencies {
	// normal main classpath
	compile ...

	// classpath for normal unittests, nothing special here
	testCompile ...

	// classpath for integration tests
	intTestCompile ...

	// classpath for acceptance tests
	accTestCompile ...
}

// redis port (exemplary for starting more processes, see also further belo)
ext.redisPort = 63000

// apply the plugin, enable int/acc tests by assigning a name, if desired

apply plugin: 'net.swisstech.dropwizard'
dropwizard {
	// your implementation of the dropwizard application class
	mainClass               = 'net.swisstech.dropwizardsample.Main'

	// the config file to use when running the server manually or as part of the tests
	dropwizardConfigFile    = 'development.yml'

	// name of the integration tests task, note the matching 'configuration' above.
	// the tasks are only added when the value is set, if it remains null (the default)
	// this task will not be added and/or callable
	integrationTestTaskName = "intTest"

	// name of the acceptance tests task, note the matching 'configuration' above.
	// the tasks are only added when the value is set, if it remains null (the default)
	// this task will not be added and/or callable
	acceptanceTestTaskName  = "accTest"

	// list of jvmArgs to be added, same semantics as JavaExec.jvmArgs(), put your args
	// including '-D' and everything there
	// added in dropwizard-gradle 1.1.6
	jvmArgs                 = [
		  "-DSERVER_VERSION=${project.version}"
		, "-DREDIS_PORT=${project.redisPort}"
	]
}

// run in afterEvaluate since the plugin does the same, plus this must be
// run after the plugin has done it's thing.
// this example uses 2 start/stop tasks so each kind of test would start
// off with a fresh redis instance. if you are fine witch 1 instance for
// both then ditch one of the start and stop tasks and change the dependsOn
// and finalizedBy declarations
afterEvaluate {
	task "intRedisStart" << {
		project.ext.intRedisProcess = net.swisstech.swissarmyknife.sys.linux.BackgroundProcess
			.launch([ 'redis-server', '--port', project.redisPort as String ], null)
			.waitForOpenPorts([ project.redisPort ], 10000)
	}
	task "intRedisStop" << {
		project.intRedisProcess.shutdown()
	}
	tasks.intTest.dependsOn("intRedisStart")
	tasks.intTest.finalizedBy("intRedisStop")

	task "accRedisStart" << {
		project.ext.accRedisProcess = net.swisstech.swissarmyknife.sys.linux.BackgroundProcess
			.launch([ 'redis-server', '--port', project.redisPort as String ], null)
			.waitForOpenPorts([ project.redisPort ], 10000)
	}
	task "accRedisStop" << {
		project.accRedisProcess.shutdown()
	}
	tasks.accTest.dependsOn("accRedisStart")
	tasks.accTest.finalizedBy("accRedisStop")
}
```

The plugin will add 1 or 2 new test tasks for integration/acceptance testing with user-definable names. These tasks will start dropwizard, then run the tests from the appropriate source set and then stop dropwizard again. Both, integration and acceptance tests are optional.

writing tests
=============

*Please note*: The test tasks are hard-wired to use TestNG.

Your actual test classes and resources go into `src/xyzTests/{java|resources}`. Where `xyzTests` corresponds to the
name of your integration/acceptance tasks.

The plugin will read your dropwizard yaml config and parse/reconstruct the urls to access your app/admin ports. This information is added to your tests as a java system property. The available properties depend on your actual configuration. The maximum available properties are:

* `DROPWIZARD_APP_HTTP`
* `DROPWIZARD_APP_HTTPS`
* `DROPWIZARD_APP_SPDY3`
* `DROPWIZARD_ADM_HTTP`
* `DROPWIZARD_ADM_HTTPS`
* `DROPWIZARD_ADM_SPDY3`

An example test to call the hello-world service from the dropwizard getting started project looks something like this:

```java
package net.swisstech;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import dwtest.client.HelloWorld;
import dwtest.client.HelloWorldClientFactory;
import dwtest.model.Saying;

public class SomeIntTest {

	@Test
	@Parameters("DROPWIZARD_APP_HTTP")
	public void test(String dwUrl) {
		HelloWorld hw = HelloWorldClientFactory.newClient(dwUrl);
		Saying saying = hw.sayHello("INTEGRATION");
		assertNotNull(saying);
		assertEquals(saying.getContent(), "Hello, INTEGRATION!");
	}
}
```

When your `integrationTestTaskName` is configured as `intTest`, then the above test would be located in `src/intTest/java/net/swisstech` and can be called trough `gradle intTest`.

acceptance test jar
===================

In case acceptance tests are enabled, a fat jar is built from the sourceSet/configuration for your acceptance Tests with the TestNG Main class as the jar's main. Simply add a `testng.xml` like below and you can run `java -jar myproject-acceptance.jar testng.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd" >
<suite name="mysuite" verbose="2">
	<test name="myproject">
		<packages>
			<package name="myproject.acceptance.*" />
		</packages>
	</test>
</suite>
```

experimental spdy3 support
==========================

SPDY3 is supported, since this plugin adds the `npn-boot` library to the boot classpath of the dropwizard instance being started. However, this feature is not very well tested and it only adds a fairly recent version of the `npn-boot` library and not the one intended for your jdk (apparently npn-boot is picky about your jdk version). Use this with caution

todo
====

* [ ] npn-boot jar version selector util
* [ ] make usage of TestNG vs. JUnit configurable (need to investigate if and how we can pass in urls to the tests like TestNG does)
* [ ] generate testng.xml and a script or Main wrapper that adds all the urls to the environment the same way as it's already done in the int/acc tests so you get a 'portable' jar with a script and can run the acceptance tests on any server as post-deployment tests.
* [ ] env parameters such as SERVER_VERSION should be configurable and optional
* [ ] check if this plugin works on mac
* [ ] make this plugin work on windows

known issues
============

* integration and acceptance tests are only run if there's at least 1 test class available in `src/test/java` so always run `test intTest` or `build intTest` or anything that includes the `test` task before your `intTest` and `accTest` tasks.
