package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.config.MultipleEntryPointsSecurityConfig;

/**
 * Loads only the actuator security chain with every endpoint exposed, to check
 * it denies everything except health and info even if exposure is reopened.
 * A minimal context is used because the full ShopApplication context cannot be
 * started twice in one JVM (ehcache CacheManager name clash).
 */
@SpringBootTest(classes = ActuatorSecurityChainIntegrationTest.ActuatorOnlyApp.class,
		webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "management.endpoints.web.exposure.include=*", "management.endpoint.shutdown.enabled=true" })
@RunWith(SpringRunner.class)
public class ActuatorSecurityChainIntegrationTest {

	private static final String[] EXPOSED_SENSITIVE_ENDPOINTS = { "/actuator", "/actuator/heapdump",
			"/actuator/env", "/actuator/beans", "/actuator/mappings", "/actuator/threaddump", "/actuator/loggers",
			"/actuator/configprops", "/actuator/conditions" };

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class,
			DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
	@EnableWebSecurity
	@Import(MultipleEntryPointsSecurityConfig.ActuatorConfigurationAdapter.class)
	static class ActuatorOnlyApp {
	}

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void healthAndInfoStayPublic() {
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/health", String.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/health/ping", String.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/info", String.class).getStatusCode());
	}

	@Test
	public void exposedSensitiveEndpointsAreForbidden() {
		for (String endpoint : EXPOSED_SENSITIVE_ENDPOINTS) {
			assertEquals(endpoint, HttpStatus.FORBIDDEN,
					testRestTemplate.getForEntity(endpoint, String.class).getStatusCode());
		}
	}

	@Test
	public void credentialsDoNotUnlockSensitiveEndpoints() {
		HttpEntity<String> entity = new HttpEntity<>(authHeaders());
		for (String endpoint : EXPOSED_SENSITIVE_ENDPOINTS) {
			ResponseEntity<String> response = testRestTemplate.exchange(endpoint, HttpMethod.GET, entity, String.class);
			assertEquals(endpoint, HttpStatus.FORBIDDEN, response.getStatusCode());
		}
	}

	@Test
	public void writeOperationsAreForbidden() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		assertEquals(HttpStatus.FORBIDDEN, testRestTemplate.postForEntity("/actuator/loggers/ROOT",
				new HttpEntity<>("{\"configuredLevel\":\"OFF\"}", headers), String.class).getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, testRestTemplate.postForEntity("/actuator/shutdown",
				new HttpEntity<>("{}", headers), String.class).getStatusCode());
	}

	private static HttpHeaders authHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("admin@shopizer.com", "password");
		return headers;
	}

}
