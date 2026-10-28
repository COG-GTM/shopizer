package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.catalog.product.group.ProductGroup;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductGroupSecurityIntegrationTest extends ServicesTestSupport {

	private static final AtomicLong IDS = new AtomicLong();

	@Test
	public void anonymousDeleteOnLegacyPublicPathIsRejected() {
		String code = createGroup();
		ResponseEntity<String> response = delete("/api/v1/products/group/" + code, null);
		assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
		assertGroupExists(code, true);
	}

	@Test
	public void anonymousDeleteOnPrivatePathIsUnauthorized() {
		String code = createGroup();
		ResponseEntity<String> response = delete("/api/v1/private/products/group/" + code, null);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertGroupExists(code, true);
	}

	@Test
	public void orderAdministratorCannotDeleteGroup() {
		String code = createGroup();
		HttpHeaders headers = getHeader(createUser(Constants.GROUP_ADMIN_ORDER), "Password1");
		ResponseEntity<String> response = delete("/api/v1/private/products/group/" + code, headers);
		assertTrue("Expected 401 or 403 but was " + response.getStatusCode(),
				response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN);
		assertGroupExists(code, true);
	}

	@Test
	public void catalogueAdministratorCanDeleteGroup() {
		String code = createGroup();
		HttpHeaders headers = getHeader(createUser(Constants.GROUP_ADMIN_CATALOGUE), "Password1");
		ResponseEntity<String> response = delete("/api/v1/private/products/group/" + code, headers);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertGroupExists(code, false);
	}

	@Test
	public void addingUnknownProductToGroupReturnsNotFound() {
		String code = createGroup();
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/products/" + Long.MAX_VALUE + "/group/" + code, HttpMethod.POST,
				new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	private String createGroup() {
		String code = "SECURITY" + Long.toString(System.currentTimeMillis(), 36).toUpperCase() + IDS.incrementAndGet();
		ProductGroup group = new ProductGroup();
		group.setCode(code);
		group.setActive(true);
		ResponseEntity<ProductGroup> response = testRestTemplate.postForEntity("/api/v1/private/products/group",
				new HttpEntity<>(group, getHeader()), ProductGroup.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertGroupExists(code, true);
		return code;
	}

	private ResponseEntity<String> delete(String path, HttpHeaders headers) {
		return testRestTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
	}

	private void assertGroupExists(String code, boolean exists) {
		ResponseEntity<ProductGroup[]> response = testRestTemplate.exchange("/api/v1/private/product/groups",
				HttpMethod.GET, new HttpEntity<>(getHeader()), ProductGroup[].class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		boolean found = Arrays.stream(response.getBody()).anyMatch(group -> code.equals(group.getCode()));
		assertEquals("Unexpected existence of group " + code, exists, found);
	}

	private String createUser(String groupName) {
		String username = "groupuser" + IDS.incrementAndGet() + System.currentTimeMillis() + "@test.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Group");
		user.setLastName("Tester");
		user.setUserName(username);
		user.setPassword("Password1");
		user.setRepeatPassword("Password1");
		user.getGroups().add(new PersistableGroup(groupName));
		ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/private/user/",
				new HttpEntity<>(user, getHeader()), String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return username;
	}
}
