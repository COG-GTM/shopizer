package com.salesmanager.test.shop.integration.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.manufacturer.ReadableManufacturer;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@ExtendWith(SpringExtension.class)
public class ManufacturerStoreScopeIntegrationTest extends ServicesTestSupport {

	private static final String STORE_B = "mfrscopeb";

	@BeforeEach
	public void ensureSecondStoreExists() {
		ResponseEntity<String> storeResponse = testRestTemplate.getForEntity("/api/v1/store/" + STORE_B, String.class);
		if (!storeResponse.getStatusCode().is2xxSuccessful()) {
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

			ResponseEntity<Void> createResponse = testRestTemplate.exchange("/api/v1/private/store/",
					HttpMethod.POST, new HttpEntity<>(store, getHeader()), Void.class);
			assertTrue(createResponse.getStatusCode().is2xxSuccessful());
		}
	}

	@Test
	public void deleteFromDifferentStoreIsRejectedAndManufacturerSurvives() {
		PersistableManufacturer manufacturer = createManufacturer(STORE_B);

		ResponseEntity<Void> deleteResponse = delete(manufacturer.getId(), MerchantStore.DEFAULT_STORE, getHeader());

		assertEquals(HttpStatus.NOT_FOUND, deleteResponse.getStatusCode());
		ResponseEntity<ReadableManufacturer> readResponse = testRestTemplate.exchange(
				"/api/v1/manufacturer/" + manufacturer.getId() + "?store=" + STORE_B,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableManufacturer.class);
		assertEquals(HttpStatus.OK, readResponse.getStatusCode());
		assertEquals(manufacturer.getCode(), readResponse.getBody().getCode());
	}

	@Test
	public void storeCanDeleteItsOwnManufacturer() {
		PersistableManufacturer manufacturer = createManufacturer(MerchantStore.DEFAULT_STORE);

		ResponseEntity<Void> deleteResponse = delete(manufacturer.getId(), MerchantStore.DEFAULT_STORE, getHeader());

		assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
		ResponseEntity<String> readResponse = testRestTemplate.exchange(
				"/api/v1/manufacturer/" + manufacturer.getId() + "?store=" + MerchantStore.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(getHeader()), String.class);
		assertNotEquals(HttpStatus.OK, readResponse.getStatusCode());
	}

	@Test
	public void ownerStoreCanDeleteItsOwnManufacturer() {
		PersistableManufacturer manufacturer = createManufacturer(STORE_B);

		ResponseEntity<Void> deleteResponse = delete(manufacturer.getId(), STORE_B, getHeader());

		assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
	}

	@Test
	public void unknownManufacturerReturnsNotFound() {
		ResponseEntity<Void> response = delete(987654321L, MerchantStore.DEFAULT_STORE, getHeader());

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void deleteWithoutAuthorizationIsUnauthorized() {
		ResponseEntity<Void> response = delete(987654321L, MerchantStore.DEFAULT_STORE, new HttpHeaders());

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	private PersistableManufacturer createManufacturer(String storeCode) {
		PersistableManufacturer manufacturer = manufacturer(uniqueCode());
		ResponseEntity<PersistableManufacturer> response = testRestTemplate.postForEntity(
				"/api/v1/private/manufacturer?store=" + storeCode,
				new HttpEntity<>(manufacturer, getHeader()), PersistableManufacturer.class);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getId());
		return response.getBody();
	}

	private ResponseEntity<Void> delete(Long id, String storeCode, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/manufacturer/" + id + "?store=" + storeCode,
				HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
	}

	private String uniqueCode() {
		return "mfrscope" + Long.toString(System.nanoTime(), 36);
	}
}
