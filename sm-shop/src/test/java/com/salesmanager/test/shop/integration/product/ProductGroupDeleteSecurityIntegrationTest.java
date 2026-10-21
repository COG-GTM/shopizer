package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.catalog.product.group.ProductGroup;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductGroupDeleteSecurityIntegrationTest extends ServicesTestSupport {

	private static final AtomicLong IDS = new AtomicLong();

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void anonymousDeleteIsUnauthorizedAndPreservesGroup() {
		assertDeniedAndPreserved(createGroup(), null);
	}

	@Test
	public void administratorCanDeleteGroup() {
		String code = createGroup();
		ResponseEntity<String> response = delete("/api/v1/private/products/group/" + code, getHeader());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertGroupExists(code, false);
	}

	@Test
	public void anonymousDeleteOnLegacyPathIsMethodNotAllowed() {
		String code = createGroup();
		ResponseEntity<String> response = delete("/api/v1/products/group/" + code, null);
		assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
		assertGroupExists(code, true);
	}

	@Test
	public void adminOrderOnlyUserCannotDeleteGroup() {
		String code = createGroup();
		HttpHeaders headers = loginAs(createUser(Constants.GROUP_ADMIN_ORDER));
		assertDeniedAndPreserved(code, headers);
	}

	@Test
	public void catalogueAdministratorCanDeleteGroup() {
		String code = createGroup();
		HttpHeaders headers = loginAs(createUser(Constants.GROUP_ADMIN_CATALOGUE));
		ResponseEntity<String> response = delete("/api/v1/private/products/group/" + code, headers);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertGroupExists(code, false);
	}

	@Test
	public void registeredCustomerCannotDeleteGroup() {
		String code = createGroup();
		HttpHeaders headers = registerAndLoginCustomer();
		assertDeniedAndPreserved(code, headers);
	}

	private String createGroup() {
		String code = unique("SECURITY");
		ProductGroup group = new ProductGroup();
		group.setCode(code);
		group.setActive(true);
		ResponseEntity<ProductGroup> response = testRestTemplate.postForEntity(
				"/api/v1/private/products/group", new HttpEntity<>(group, getHeader()), ProductGroup.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertGroupExists(code, true);
		return code;
	}

	private void assertDeniedAndPreserved(String code, HttpHeaders headers) {
		ResponseEntity<String> response = delete("/api/v1/private/products/group/" + code, headers);
		assertTrue("Expected 401 or 403 but received " + response.getStatusCode(),
				response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN);
		assertGroupExists(code, true);
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
		String id = unique("groupuser");
		String username = id.toLowerCase() + "@test.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Group");
		user.setLastName("Tester");
		user.setUserName(username);
		user.setPassword("Password1");
		user.setRepeatPassword("Password1");
		user.getGroups().add(new PersistableGroup(groupName));
		ResponseEntity<?> response = testRestTemplate.postForEntity("/api/v1/private/user/",
				new HttpEntity<>(user, getHeader()), Object.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return username;
	}

	private HttpHeaders loginAs(String username) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, "Password1")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getToken());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(response.getBody().getToken());
		return headers;
	}

	private HttpHeaders registerAndLoginCustomer() {
		String email = unique("customer").toLowerCase() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(com.salesmanager.core.business.constants.Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Security");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<?> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), Object.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}

	private static String unique(String prefix) {
		return prefix + Long.toString(System.currentTimeMillis(), 36).toUpperCase() + IDS.incrementAndGet();
	}
}
