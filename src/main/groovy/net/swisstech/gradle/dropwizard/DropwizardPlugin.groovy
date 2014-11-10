package net.swisstech.gradle.dropwizard

import static net.swisstech.swissarmyknife.lang.Strings.isBlank
import static net.swisstech.swissarmyknife.lang.Strings.isNotBlank

import org.slf4j.*

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.testing.Test

/** build shadowed jar and adds a dropwizardRun task to run dropwizard from gradle */
class DropwizardPlugin implements Plugin<Project> {

	static final Logger LOG = LoggerFactory.getLogger(DropwizardPlugin.class)

	void apply(Project project) {

		if (!"Linux".equals(System.getProperty("os.name"))) {
			LOG.error("!!!")
			LOG.error("!!! WARNING: the dropwizard plugin uses some linux specific code, namely java.lang.UNIXProcess")
			LOG.error("!!! This plugin has only been tested on Debian Linux.")
			LOG.error("!!!")
			LOG.error("!!! ... you are entering uncharted territory")
			LOG.error("!!!")
		}

		project.extensions.create('dropwizard', DropwizardExtension)

		project.afterEvaluate {

			if (isBlank(project.version)) {
				throw new InvalidUserDataException("Project Version cannot be null/blank")
			}

			project.ext.dwConfig = DropwizardConfigLoader.parse(project.dropwizard.dropwizardConfigFile as File)

			configureProject(project)

			if (isNotBlank(project.dropwizard.integrationTestTaskName)) {
				configureTestTask(project, project.dropwizard.integrationTestTaskName)
			}

			if (isNotBlank(project.dropwizard.acceptanceTestTaskName)) {
				configureTestTask(project, project.dropwizard.acceptanceTestTaskName)

				// additionally, we need a shadow jar for the acceptance tests
				project.configure(project) {
					apply plugin: 'com.github.johnrengelman.shadow'
					task("${project.dropwizard.acceptanceTestTaskName}ShadowJar", type: Class.forName('com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar')) {
						// build should depend on us
						tasks['assemble'].dependsOn name

						// shadow extends jar, same config
						classifier = "acceptance"
						from sourceSets[project.dropwizard.acceptanceTestTaskName].runtimeClasspath
						manifest {
							mergeServiceFiles()
							attributes 'Main-Class': 'org.testng.TestNG'
						}
					}
				}
			}
		}
	}

	/** base config of project, always applied */
	void configureProject(Project project) {

		project.configure(project) {

			// npn-boot is available from maven central
			repositories {
				mavenCentral()
				mavenLocal()
			}

			// we need a special classpath just for the boot classpath
			configurations {
				dropwizardRunBootClassPath
			}

			dependencies {
				// classpath for dropwizard's jvm's bootclasspath
				// get the correct npn lib version here:
				// http://www.eclipse.org/jetty/documentation/current/npn-chapter.html#npn-versions
				// TODO select correct version based on JVM version (although there seems to be some leeway)
				dropwizardRunBootClassPath "org.mortbay.jetty.npn:npn-boot:1.1.7.v20140316"
			}

			// we need to append to the bootclasspath manually because the impl
			// in JavaExec is buggy: http://gsfn.us/t/4mjt7

			// TODO the SERVER_VERSION is a thing specific to my personal use case and it
			// should be made configurable via the dropwizard extension

			task('dropwizardRun', type: JavaExec) {
				workingDir = projectDir
				classpath  = sourceSets.main.runtimeClasspath
				main       = dropwizard.mainClass
				jvmArgs    "-DSERVER_VERSION=${project.version}", "-Xbootclasspath/a:${configurations.dropwizardRunBootClassPath.asPath}"
				args       "server", dropwizard.dropwizardConfigFile
			}

			// fat jar with merged service files
			apply plugin: 'com.github.johnrengelman.shadow'
			shadowJar {
				manifest {
					mergeServiceFiles()
					attributes 'Main-Class': dropwizard.mainClass
				}
			}
		}
	}

	/** int/acc test task config, only conditionally applied */
	void configureTestTask(Project project, String taskName) {

		project.configure(project) {

			// unnecessary as long as we're called in 'afterEvaluate'. at that
			// point the user's buildscript already needs to have the
			// configuration  or else he can't add dependencies to them.
			//configurations.maybeCreate("${taskName}Compile")

			// add a source set, we don't need to bother the user with that
			// since it's trivial and he doesn't need to do anything special
			sourceSets.create("${taskName}") {
				java.srcDir(     "src/${taskName}/java")
				resources.srcDir("src/${taskName}/resources")
			}

			// we only want the main classes available, don't need the test in intTest/accTest
			dependencies.add "${taskName}Compile", sourceSets.main.output

			// actual test task, not much to configure here
			// depends on:
			// - ${taskName}Classes: our own classes to be run agains the running server
			// - classes:            the main classes which make up the server to be run
			// - test:               TODO unknown why but we need this and an actual unit test or else this task won't run
			task("${taskName}", type: Test.class, dependsOn: [ "${taskName}Classes", "classes" ]) {
				useTestNG()
				description    = 'Runs intTest against a locally running server. Generated by dropwizard_inttest plugin.'
				group          = 'verification'
				testSrcDirs    = sourceSets."${taskName}".java.srcDirs as List
				testClassesDir = sourceSets."${taskName}".output.classesDir
				classpath      = sourceSets."${taskName}".runtimeClasspath

				// print output from the test jvms
				testLogging.showStandardStreams = true

				// add all urls parsed from the config to the environment
				dwConfig.urls.each {
					systemProperty(it.key, it.value)
				}
			}

			// start dropwizard before the tests are run, we check and wait
			// until the ports registered in the yml file are open so we know
			// dropwizard is up and running!
			tasks."${taskName}".doFirst {
				LOG.info("starting server before ${taskName}")

				// we re-use the commandline from the dropwizardRun task
				def commandLine = tasks['dropwizardRun'].commandLine
				Process process = ProcessUtil.launch(commandLine, projectDir)

				Set<Integer> found   = [] as Set
				long         start   = System.currentTimeMillis()
				long         maxWait = start + 10000
				int          pid     = process.pid

				if (dwConfig.ports == null || dwConfig.ports.isEmpty()) {
					throw new InvalidUserDataException("No port definitions found in ${dropwizardConfigFile}")
				}

				LOG.info("waiting until all of these ports are open: ${dwConfig.ports}")

				// loop and sleep until all expected ports are open
				while (true) {
					for (String port : dwConfig.ports) {
						def foundPid = "lsof -t -i :${port}".execute().text.trim()
						if (foundPid != null && foundPid.length() > 0) {
							found << Integer.parseInt(port)
						}
						if (foundPid != null && !foundPid.isEmpty() && foundPid as int != pid) {
							ProcessUtil.killAndWait(process)
							throw new InvalidUserDataException("Ports are spread across multiple processes, bailing! Go check out that other process with pid: ${foundPid}!")
						}
					}

					LOG.info("Open ports right now: ${found}")
					if (found.containsAll(dwConfig.ports)) {
						break
					}

					try {
						int rv = process.exitValue()
						throw new InvalidUserDataException("Dropwizad process exited with exit code ${rv}")
					}
					catch (IllegalThreadStateException e) {
						// ignore, process is still running, all is good
					}

					if (System.currentTimeMillis() > maxWait) {
						ProcessUtil.killAndWait(process)
						throw new InvalidUserDataException("Timeout while waiting for dropwizard to start (was waiting for ports: ${dwConfig.ports})")
					}

					sleep(50)
				}

				def done = System.currentTimeMillis() - start
				LOG.info("dropwizard up and running for ${taskName} after ${done} millis")

				ext."${taskName}Process" = process
			}

			tasks."${taskName}".doLast {
				LOG.info("stopping server after ${taskName}")
				ProcessUtil.killAndWait(ext."${taskName}Process")
				LOG.info("server stopped after ${taskName}")
			}

			// add the compile classpath to eclipse
			if (plugins.hasPlugin('eclipse')) {
				eclipse.classpath.plusConfigurations += [ configurations["${taskName}Compile"] ]
			}
		}
	}
}

class DropwizardExtension {

	/** the main class of your dropwizard application */
	def String mainClass = null

	/** the yaml config file used to start dropwizard */
	def String dropwizardConfigFile = null

	/** integration tests will be configured if set */
	def String integrationTestTaskName = null

	/** acceptance tests will be configured if set */
	def String acceptanceTestTaskName = null

	/** validate the configuration */
	DropwizardExtension validate(Project project) {
		if (isBlank(mainClass)) {
			throw new InvalidUserDataException("The mainClass must not be null/empty")
		}
		if (isBlank(dropwizardConfigFile)) {
			throw new InvalidUserDataException("The dropwizardConfigFile must not be null/empty")
		}
		File cfg = project.file(dropwizardConfigFile)
		if (!cfg.exists()) {
			throw new InvalidUserDataException("The dropwizardConfigFile does not exist (expected at ${cfg.absolutePath}")
		}
		if (!cfg.isFile()) {
			throw new InvalidUserDataException("The dropwizardConfigFile is not a file (expected at ${cfg.absolutePath}")
		}
		return this
	}
}
