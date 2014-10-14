
gradle-dropwizard
=================

Gradle plugin to start/stop dropwizard manually as well as part of a test task and build a runnable jar file

<img src="https://travis-ci.org/stackmagic/gradle-dropwizard.svg?branch=master" />
[ ![Download](https://api.bintray.com/packages/stackmagic/maven/gradle-dropwizard/images/download.svg) ](https://bintray.com/stackmagic/maven/gradle-dropwizard/_latestVersion)

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

this project contains 2 plugins

Dropwizard
----------

This plugin will add a 'dropwizardRun' task which is similar to 'jettyRun': it starts the server and blocks until interrupted via Ctrl-C.
It will also add a 'shadowJar' task (reusing the plugin 'com.github.johnrengelman.shadow') to create a shaded jar with merged service files so
the jar can be run with 'java -jar your.jar'.

SPDY is supported, since this plugin adds the 'npn-boot' library to the boot classpath of the dropwizard instance being started. However,
it only adds a fairly recent version of the 'npn-boot' library and not the one intended for your jdk (apparently npn-boot is picky about
your jdk version).

```groovy
apply plugin: 'dropwizard'
dropwizard {
	mainClass            = 'net.swisstech.DropwizardMain'
	dropwizardConfigFile = 'development.yml'
}
```

DropwizardInttest
-----------------

This plugin is optional, but requires the Dropwizard plugin to be applied and configured if it is to be used.
It will add 1-2 new test tasks with user-definable names. These tasks will start dropwizard, then run the tests
from the appropriate source set and then stop dropwizard again.

If the plugin is applied, the intTests are mandatory, the accTests are optional. The example
below configures both.

Besides configuring the plugin, you must also add a configuration for the two compile configurations. These are needed
so you can add the specific integration/acceptance test dependencies you need.

The test tasks are hard-wired to use TestNG.

```groovy
configurations {
	intTestCompile
	accTestCompile
}

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

apply plugin: 'dropwizard-inttest'
dropwizard_inttest {
	intTestTaskName = 'intTest'
	accTestTaskName = 'accTest'
}
```

Your actual test classes and resources then go into 'src/{int|acc}Test/{java|resources}'. The plugin will read your
dropwizard yml config file and use the *lowest* port it can find to supply an url to your tests as an environment variable.

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
	@Parameters("DROPWIZARD_URL")
	public void test(String dwUrl) {
		HelloWorld hw = HelloWorldClientFactory.newClient(dwUrl);
		Saying saying = hw.sayHello("INTEGRATION");
		assertNotNull(saying);
		assertEquals(saying.getContent(), "Hello, INTEGRATION!");
	}
}
```

todo
====

* real parsing of the yml file to always supply a correct url
* npn-boot jar version selector util
* streamline the test task generation
* make usage of testng vs. junit configurable
