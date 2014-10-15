package net.swisstech.gradle.dropwizard;

import java.io.File;
import org.yaml.snakeyaml.Yaml;

public class DropwizardConfigLoader {

	public static DropwizardConfig parse(File f) {
		return parse(f.text);
	}

	public static DropwizardConfig parse(String text) {
		def server = new Yaml().load(text).server
		DropwizardConfig cfg = new DropwizardConfig()

		// process the 'normal' config with split ports for app/admin
		def processSplit = { def conn, String kind ->
			String key = "DROPWIZARD_${kind.toUpperCase()}_${conn.type.toUpperCase()}"
			String url = "${conn.type}://localhost:${conn.port}"
			cfg.urls.put(key, url)
			cfg.ports << conn.port
		}

		server.applicationConnectors?.each { processSplit(it, "APP") }
		server.adminConnectors      ?.each { processSplit(it, "ADM") }

		// process the 'simple' config where app/admin have different
		// context paths on the same port
		if (server.connector) {
			String key, url
			def conn = server.connector
			key = "DROPWIZARD_APP_${conn.type.toUpperCase()}"
			url = "${conn.type}://localhost:${conn.port}${server.applicationContextPath}"
			cfg.urls.put(key, url)

			key = "DROPWIZARD_ADM_${conn.type.toUpperCase()}"
			url = "${conn.type}://localhost:${conn.port}${server.adminContextPath}"
			cfg.urls.put(key, url)

			cfg.ports << conn.port
		}

		return cfg
	}
}

public class DropwizardConfig {
	/** all ports of the config */
	Set<Integer> ports = [] as Set

	/**
	 * base urls reconstructed from config.
	 */
	Map<String, String> urls = [:]
}
