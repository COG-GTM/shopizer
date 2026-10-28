package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"shopizer.admin.initial-password=password",
		"db.jdbcUrl=jdbc:h2:mem:SALESMANAGER-PR174-WEAK;AUTOCOMMIT=OFF;;mv_store=false;INIT=CREATE SCHEMA IF NOT EXISTS SALESMANAGER"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DefaultAdminPasswordWeakIntegrationTest extends ServicesTestSupport {

	@Test
	void weakConfiguredPasswordDoesNotBecomeTheAdminPassword() throws Exception {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(ADMIN_USER, "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.UNAUTHORIZED, login.getStatusCode());
	}
}
