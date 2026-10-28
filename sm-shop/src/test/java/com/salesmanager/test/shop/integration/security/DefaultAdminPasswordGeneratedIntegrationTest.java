package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
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
		"shopizer.admin.initial-password=",
		"db.jdbcUrl=jdbc:h2:mem:SALESMANAGER-PR174-GENERATED;AUTOCOMMIT=OFF;;mv_store=false;INIT=CREATE SCHEMA IF NOT EXISTS SALESMANAGER"
})
@Import(TestPropertyPlaceholderConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DefaultAdminPasswordGeneratedIntegrationTest extends ServicesTestSupport {

	@Autowired
	private DataSource dataSource;

	@Test
	void generatedPasswordReplacesLegacyAndConfiguredTestPasswords() throws Exception {
		TestDatabaseSupport.assertDatabaseUrl(dataSource, "SALESMANAGER-PR174-GENERATED");
		assertUnauthenticated("password");
		assertUnauthenticated(ADMIN_PASSWORD);
	}

	private void assertUnauthenticated(String password) {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(ADMIN_USER, password)),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.UNAUTHORIZED, login.getStatusCode());
	}
}
