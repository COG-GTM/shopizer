package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import com.salesmanager.test.shop.common.TestDatabaseSupport;
import com.salesmanager.test.shop.common.TestPropertyPlaceholderConfiguration;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"shopizer.admin.initial-password=Sh0pizer-Test-Admin-1",
		"db.jdbcUrl=jdbc:h2:mem:SALESMANAGER-PR174-CONFIGURED;AUTOCOMMIT=OFF;;mv_store=false;INIT=CREATE SCHEMA IF NOT EXISTS SALESMANAGER"
})
@Import(TestPropertyPlaceholderConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DefaultAdminPasswordIntegrationTest extends ServicesTestSupport {

	@Autowired
	private DataSource dataSource;

	@Test
	void configuredPasswordAuthenticatesAndProtectsAdminEndpoints() throws Exception {
		TestDatabaseSupport.assertDatabaseUrl(dataSource, "SALESMANAGER-PR174-CONFIGURED");
		ResponseEntity<AuthenticationResponse> login = login(ADMIN_PASSWORD);

		assertEquals(200, login.getStatusCodeValue());
		assertTrue(login.getBody() != null && login.getBody().getToken() != null
				&& !login.getBody().getToken().trim().isEmpty());

		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + login.getBody().getToken());
		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/private/user/profile", HttpMethod.GET, new HttpEntity<>(headers), String.class);
		assertEquals(200, profile.getStatusCodeValue());

		ResponseEntity<AuthenticationResponse> legacyLogin = login("password");
		assertTrue(legacyLogin.getStatusCode().is4xxClientError());
		assertFalse(legacyLogin.getStatusCode().is2xxSuccessful());
	}

	private ResponseEntity<AuthenticationResponse> login(String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(ADMIN_USER, password)), AuthenticationResponse.class);
	}
}
