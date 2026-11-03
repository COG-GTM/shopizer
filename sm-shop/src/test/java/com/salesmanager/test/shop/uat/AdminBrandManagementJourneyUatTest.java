package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.manufacturer.ReadableManufacturer;
import com.salesmanager.shop.model.catalog.manufacturer.ReadableManufacturerList;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@ExtendWith(SpringExtension.class)
public class AdminBrandManagementJourneyUatTest extends ServicesTestSupport {

	private static final String STORE_B = "mfrscopeb";

	@Test
	public void adminCanManageBrandsWithoutCrossingStoreBoundaries() {
		ensureSecondStoreExists();
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		assertFalse(login.getBody().getToken().isEmpty());
		HttpHeaders authHeaders = bearer(login.getBody().getToken());

		ResponseEntity<ReadableManufacturerList> authenticatedList = testRestTemplate.exchange(
				"/api/v1/private/manufacturers?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(authHeaders), ReadableManufacturerList.class);
		assertEquals(HttpStatus.OK, authenticatedList.getStatusCode());

		ResponseEntity<String> unauthenticatedList = testRestTemplate.exchange(
				"/api/v1/private/manufacturers?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, unauthenticatedList.getStatusCode());

		HttpHeaders invalidHeaders = bearer("garbage");
		ResponseEntity<String> invalidTokenList = testRestTemplate.exchange(
				"/api/v1/private/manufacturers?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(invalidHeaders), String.class);
		assertFalse(invalidTokenList.getStatusCode().is2xxSuccessful(),
				"Invalid token must not grant access: " + invalidTokenList.getStatusCode());

		PersistableManufacturer defaultBrand = manufacturer(uniqueCode());
		ResponseEntity<PersistableManufacturer> created = testRestTemplate.postForEntity(
				"/api/v1/private/manufacturer?store=DEFAULT",
				new HttpEntity<>(defaultBrand, authHeaders), PersistableManufacturer.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		Long defaultBrandId = created.getBody().getId();
		assertNotNull(defaultBrandId);

		ResponseEntity<ReadableManufacturer> initialRead = getBrand(defaultBrandId, MerchantStore.DEFAULT_STORE,
				authHeaders);
		assertEquals(HttpStatus.OK, initialRead.getStatusCode());

		defaultBrand.getDescriptions().get(0).setName("Updated " + defaultBrand.getCode());
		ResponseEntity<Void> update = testRestTemplate.exchange(
				"/api/v1/private/manufacturer/" + defaultBrandId + "?store=DEFAULT",
				HttpMethod.PUT, new HttpEntity<>(defaultBrand, authHeaders), Void.class);
		assertEquals(HttpStatus.OK, update.getStatusCode());
		ResponseEntity<ReadableManufacturer> updatedRead = getBrand(defaultBrandId, MerchantStore.DEFAULT_STORE,
				authHeaders);
		assertEquals("Updated " + defaultBrand.getCode(), updatedRead.getBody().getDescription().getName());

		ResponseEntity<Void> delete = delete(defaultBrandId, MerchantStore.DEFAULT_STORE, authHeaders);
		assertEquals(HttpStatus.OK, delete.getStatusCode());
		assertFalse(getBrand(defaultBrandId, MerchantStore.DEFAULT_STORE, authHeaders).getStatusCode().is2xxSuccessful());

		PersistableManufacturer storeBrand = manufacturer(uniqueCode());
		ResponseEntity<PersistableManufacturer> createdInStoreB = testRestTemplate.postForEntity(
				"/api/v1/private/manufacturer?store=" + STORE_B,
				new HttpEntity<>(storeBrand, authHeaders), PersistableManufacturer.class);
		assertEquals(HttpStatus.CREATED, createdInStoreB.getStatusCode());
		Long storeBrandId = createdInStoreB.getBody().getId();
		assertNotNull(storeBrandId);

		ResponseEntity<Void> crossStoreDelete = delete(storeBrandId, MerchantStore.DEFAULT_STORE, authHeaders);
		assertEquals(HttpStatus.NOT_FOUND, crossStoreDelete.getStatusCode());
		ResponseEntity<ReadableManufacturerList> storeBList = testRestTemplate.exchange(
				"/api/v1/private/manufacturers?store=" + STORE_B, HttpMethod.GET,
				new HttpEntity<>(authHeaders), ReadableManufacturerList.class);
		assertEquals(HttpStatus.OK, storeBList.getStatusCode());
		assertTrue(storeBList.getBody().getManufacturers().stream()
				.anyMatch(brand -> brand.getId().equals(storeBrandId)));
	}

	private void ensureSecondStoreExists() {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/api/v1/store/" + STORE_B, String.class);
		if (response.getStatusCode().is2xxSuccessful()) {
			return;
		}

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(STORE_B);
		store.setName(STORE_B);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setEmail("mfrscope@example.com");
		store.setPhone("444-555-6666");
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");
		store.setAddress(address);
		assertTrue(testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class).getStatusCode().is2xxSuccessful());
	}

	private ResponseEntity<ReadableManufacturer> getBrand(Long id, String storeCode, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/manufacturer/" + id + "?store=" + storeCode,
				HttpMethod.GET, new HttpEntity<>(headers), ReadableManufacturer.class);
	}

	private ResponseEntity<Void> delete(Long id, String storeCode, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/manufacturer/" + id + "?store=" + storeCode,
				HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private String uniqueCode() {
		return "mfrscope" + Long.toString(System.nanoTime(), 36);
	}
}
