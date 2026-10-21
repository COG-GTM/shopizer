package com.salesmanager.test.shop.integration.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.utils.CacheUtils;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.utils.WebApplicationCacheUtils;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class CacheApiIntegrationTest extends ServicesTestSupport {

	@Inject
	private WebApplicationCacheUtils cacheUtils;

	@Inject
	private CacheUtils cacheCleaner;

	@Test
	public void authenticatedCustomerCanClearOneOrAllStoreCacheKeys() throws Exception {
		String suffix = UUID.randomUUID().toString();
		String email = "cache-" + suffix + "@test.com";
		String password = "clear123";
		registerCustomer(email, password);
		HttpHeaders customerHeaders = customerHeaders(email, password);
		ReadableMerchantStore store = fetchStore();
		String firstKey = store.getId() + "_k1-" + suffix;
		String secondKey = store.getId() + "_k2-" + suffix;
		String referenceKey = "REF_keep-" + suffix;
		try {
			cacheUtils.putInCache(firstKey, "first");
			cacheUtils.putInCache(secondKey, "second");
			cacheUtils.putInCache(referenceKey, "reference");

			ResponseEntity<String> singleKeyResponse = testRestTemplate.exchange(
					"/api/v1/auth/cache/store/DEFAULT/clear?cacheKey=k1-" + suffix,
					HttpMethod.DELETE, new HttpEntity<>(customerHeaders), String.class);
			assertEquals(OK, singleKeyResponse.getStatusCode());
			assertNull(cacheUtils.getFromCache(firstKey));
			assertEquals("second", cacheUtils.getFromCache(secondKey));

			ResponseEntity<String> allKeysResponse = testRestTemplate.exchange(
					"/api/v1/auth/cache/store/DEFAULT/clear", HttpMethod.DELETE,
					new HttpEntity<>(customerHeaders), String.class);
			assertEquals(OK, allKeysResponse.getStatusCode());
			assertNull(cacheUtils.getFromCache(secondKey));
			assertEquals("reference", cacheUtils.getFromCache(referenceKey));

			ResponseEntity<String> unauthenticatedResponse = testRestTemplate.exchange(
					"/api/v1/auth/cache/store/DEFAULT/clear", HttpMethod.DELETE,
					new HttpEntity<>(new HttpHeaders()), String.class);
			assertTrue(unauthenticatedResponse.getStatusCode().value() == 401
					|| unauthenticatedResponse.getStatusCode().value() == 403);
		} finally {
			cacheCleaner.removeFromCache(firstKey);
			cacheCleaner.removeFromCache(secondKey);
			cacheCleaner.removeFromCache(referenceKey);
			testRestTemplate.exchange("/api/v1/auth/customer/?store=DEFAULT", HttpMethod.DELETE,
					new HttpEntity<>(customerHeaders), String.class);
		}
	}

	private void registerCustomer(String email, String password) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Cache");
		billing.setLastName("Test");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()),
				PersistableCustomer.class);
		assertEquals(OK, response.getStatusCode());
	}

	private HttpHeaders customerHeaders(String email, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest(email, password)),
				AuthenticationResponse.class);
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getToken());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(response.getBody().getToken());
		return headers;
	}
}
