package net.swisstech.gradle.dropwizard;

import groovy.util.GroovyTestCase

/** test the DropwizardConfigLoader */
public class DropwizardConfigLoaderTest extends GroovyTestCase {

	public void testApplicationAndAdminConnectors() {
		String yaml = """\
		|---
		|server:
		|  applicationConnectors:
		|    - type: http
		|      port: 7000
		|    - type: https
		|      port: 7001
		|    - type: spdy3
		|      port: 7002
		|  adminConnectors:
		|    - type: http
		|      port: 8000
		|    - type: https
		|      port: 8001
		|    - type: spdy3
		|      port: 8002
		|
		|metrics:
		|  reporters:
		|    - type: graphite
		|      host: 127.0.0.1
		|      port: 2003
		|      prefix: testing
		|
		|""".stripMargin();

		DropwizardConfig cfg = DropwizardConfigLoader.parse(yaml);
		assertNotNull(cfg)
		assertNotNull(cfg.ports)
		assertNotNull(cfg.urls)

		assertTrue(cfg.ports.contains(7000));
		assertTrue(cfg.ports.contains(7001));
		assertTrue(cfg.ports.contains(7002));

		assertTrue(cfg.ports.contains(8000));
		assertTrue(cfg.ports.contains(8001));
		assertTrue(cfg.ports.contains(8002));

		assertFalse(cfg.ports.contains(2003));

		assertEquals( "http://localhost:7000" , cfg.urls.DROPWIZARD_APP_HTTP)
		assertEquals("https://localhost:7001" , cfg.urls.DROPWIZARD_APP_HTTPS)
		assertEquals("spdy3://localhost:7002" , cfg.urls.DROPWIZARD_APP_SPDY3)
		assertEquals( "http://localhost:8000" , cfg.urls.DROPWIZARD_ADM_HTTP)
		assertEquals("https://localhost:8001" , cfg.urls.DROPWIZARD_ADM_HTTPS)
		assertEquals("spdy3://localhost:8002" , cfg.urls.DROPWIZARD_ADM_SPDY3)
	}

	public void testConnector() {
		String yaml = """\
		|---
		|server:
		|  applicationContextPath: /appCtx
		|  adminContextPath:       /admCtx
		|  connector:
		|    type: http
		|    port: 7000
		|
		|metrics:
		|  reporters:
		|    - type: graphite
		|      host: 127.0.0.1
		|      port: 2003
		|      prefix: testing
		|
		|""".stripMargin();

		DropwizardConfig cfg = DropwizardConfigLoader.parse(yaml);
		assertNotNull(cfg)
		assertNotNull(cfg.ports)
		assertNotNull(cfg.urls)

		assertTrue(cfg.ports.contains(7000));

		assertFalse(cfg.ports.contains(2003));

		assertEquals("http://localhost:7000/appCtx" , cfg.urls.DROPWIZARD_APP_HTTP)
		assertEquals("http://localhost:7000/admCtx" , cfg.urls.DROPWIZARD_ADM_HTTP)
	}
}
