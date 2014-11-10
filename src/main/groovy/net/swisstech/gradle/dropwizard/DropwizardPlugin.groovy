package net.swisstech.gradle.dropwizard

import net.swisstech.swissarmyknife.lang.*
import net.swisstech.gradle.plugins.util.*

import org.slf4j.*

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.plugins.*
import org.gradle.api.publish.maven.*

import org.gradle.util.ConfigureUtil

/** build shadowed jar and adds a dropwizardRun task to run dropwizard from gradle */
class DropwizardPlugin implements Plugin<Project> {

	void apply(Project project) {

		project.extensions.create('dropwizard', DropwizardExtension)

		project.afterEvaluate {
			configureProject(project)
		}
	}

	void configureProject(Project project) {

		project.configure(project) {
			DropwizardExtension dwe = project.dropwizard.validate(project)

			// we need a special classpath just for the
//			configurations {
//				dropwizardRunBootClassPath
//			}

//			dependencies {
//				// classpath for dropwizard's jvm's bootclasspath
//				// get the correct npn lib version here:
//				// http://www.eclipse.org/jetty/documentation/current/npn-chapter.html#npn-versions
//				// TODO select correct version based on JVM version (although there seems to be some leeway)
//				dropwizardRunBootClassPath "org.mortbay.jetty.npn:npn-boot:1.1.7.v20140316"
//			}

			// we need to append to the bootclasspath manually because the impl
			// in JavaExec is buggy: http://gsfn.us/t/4mjt7
			// we also supply the version parameter here so the development config doesn't need
			// to specify it. in production, ansible uses the /version servlet to check if the
			// new version of the service is up and running
			task('dropwizardRun', type: JavaExec) {
				workingDir = projectDir
				classpath  = sourceSets.main.runtimeClasspath
				main       = dwe.mainClass
//				jvmArgs    "-Xbootclasspath/a:${configurations.dropwizardRunBootClassPath.asPath}", "-Dgrakins.version=${project.version}"
				jvmArgs    "-Dgrakins.version=${project.version}"
				args       "server", dwe.dropwizardConfigFile
			}

			// fat jar with merged service files
			apply plugin: 'com.github.johnrengelman.shadow'
			shadowJar {
				manifest {
					mergeServiceFiles()
					attributes 'Main-Class': dwe.mainClass
				}
			}
		}
	}
}

class DropwizardExtension {

	/** the main class of your dropwizard application */
	def String mainClass = null

	/** the yaml config file used to start dropwizard */
	def String dropwizardConfigFile = null

	/** validate the configuration */
	DropwizardExtension validate(Project project) {
		if (Strings.isBlank(mainClass)) {
			throw new InvalidUserDataException("The mainClass must not be null/empty")
		}
		if (Strings.isBlank(dropwizardConfigFile)) {
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
