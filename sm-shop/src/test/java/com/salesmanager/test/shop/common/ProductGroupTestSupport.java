package com.salesmanager.test.shop.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.catalog.product.group.ProductGroup;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;

public abstract class ProductGroupTestSupport extends ServicesTestSupport {

	protected String uniqueCode() {
		return "PG" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
	}

	protected String createStore() {
		String code = uniqueCode();
		PersistableAddress address = new PersistableAddress();
		address.setAddress("123 Test Street");
		address.setPostalCode("33301");
		address.setCountry("US");
		address.setCity("Fort Lauderdale");
		address.setStateProvince("FL");
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("store@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity("/api/v1/private/store/",
				new HttpEntity<>(store, getHeader()), String.class).getStatusCode());
		return code;
	}

	protected HttpHeaders createAdmin(String store, String role) {
		String username = uniqueCode().toLowerCase(Locale.ROOT) + "@test.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Group");
		user.setLastName("Tester");
		user.setUserName(username);
		user.setPassword("Password1");
		user.setRepeatPassword("Password1");
		user.getGroups().add(new PersistableGroup(role));
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity("/api/v1/private/user/?store=" + store,
				new HttpEntity<>(user, getHeader()), String.class).getStatusCode());
		return getHeader(username, "Password1");
	}

	protected String createGroup(String store, HttpHeaders headers) {
		String code = uniqueCode();
		ProductGroup group = new ProductGroup();
		group.setCode(code);
		group.setActive(true);
		ResponseEntity<ProductGroup> response = testRestTemplate.postForEntity(
				"/api/v1/private/products/group?store=" + store, new HttpEntity<>(group, headers), ProductGroup.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(code, response.getBody().getCode());
		assertGroupExists(store, code, true);
		return code;
	}

	protected void assertGroupExists(String store, String code, boolean exists) {
		ResponseEntity<ProductGroup[]> response = testRestTemplate.exchange(
				"/api/v1/private/product/groups?store=" + store, HttpMethod.GET,
				new HttpEntity<>(getHeader()), ProductGroup[].class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("Group existence: " + code, exists,
				Arrays.stream(response.getBody()).anyMatch(group -> code.equals(group.getCode())));
	}
}
