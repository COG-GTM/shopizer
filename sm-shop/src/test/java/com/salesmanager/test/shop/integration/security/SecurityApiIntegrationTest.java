package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.ReadableGroup;
import com.salesmanager.shop.model.security.ReadablePermission;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class SecurityApiIntegrationTest extends ServicesTestSupport {

	private static final String[] PROTECTED_PATHS = {
			"/api/v1/private/security/groups",
			"/api/v1/private/security/permissions",
			"/api/v1/private/security/ADMIN/permissions",
			"/api/v1/sec/private/groups",
			"/api/v1/sec/private/permissions",
			"/api/v1/sec/private/ADMIN/permissions" };

	@Test
	public void anonymousCallerIsRejected() {
		for (String path : PROTECTED_PATHS) {
			ResponseEntity<String> response = testRestTemplate.getForEntity(path, String.class);
			assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), path);
		}
	}

	@Test
	public void adminCanListGroups() {
		HttpEntity<String> entity = new HttpEntity<>(getHeader());
		for (String path : new String[] { "/api/v1/private/security/groups", "/api/v1/sec/private/groups" }) {
			ResponseEntity<ReadableGroup[]> response = testRestTemplate.exchange(path, HttpMethod.GET, entity,
					ReadableGroup[].class);
			assertEquals(HttpStatus.OK, response.getStatusCode(), path);
			assertNotNull(response.getBody());
			assertFalse(response.getBody().length == 0, path);
		}
	}

	@Test
	public void adminCanListPermissions() {
		HttpEntity<String> entity = new HttpEntity<>(getHeader());
		for (String path : new String[] { "/api/v1/private/security/permissions", "/api/v1/sec/private/permissions",
				"/api/v1/private/security/ADMIN/permissions" }) {
			ResponseEntity<ReadablePermission[]> response = testRestTemplate.exchange(path, HttpMethod.GET, entity,
					ReadablePermission[].class);
			assertEquals(HttpStatus.OK, response.getStatusCode(), path);
			assertNotNull(response.getBody());
		}
	}
}
