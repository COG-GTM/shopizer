package com.salesmanager.test.shop.integration.tax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.tax.TaxClassService;
import com.salesmanager.core.business.services.tax.TaxRateService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.tax.taxclass.TaxClass;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.core.model.tax.taxrate.TaxRateDescription;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.tax.PersistableTaxClass;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.ReadableTaxClass;
import com.salesmanager.shop.model.tax.ReadableTaxRate;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class TaxCrossStoreIntegrationTest extends ServicesTestSupport {

	@Inject
	private MerchantStoreService merchantStoreService;
	@Inject
	private TaxClassService taxClassService;
	@Inject
	private TaxRateService taxRateService;

	@Test
	public void superadminCanOperateOnSecondStoreAndClientIdCannotMoveTaxClass() throws Exception {
		String storeCode = createSecondStore();
		MerchantStore storeB = merchantStoreService.getByCode(storeCode);
		assertNotNull(storeB);
		String classCode = unique("CB");
		Long storeBClassId = createTaxClass(storeCode, classCode, null);

		ResponseEntity<Entity> takeover = testRestTemplate.exchange(
				"/api/v1/private/tax/class?store=" + MerchantStore.DEFAULT_STORE, POST,
				new HttpEntity<>(taxClass(classCode + "X", "Default copy", storeBClassId), getHeader()), Entity.class);
		assertTrue("Expected successful create in DEFAULT, got " + takeover.getStatusCode(),
				takeover.getStatusCode().is2xxSuccessful());
		assertNotNull(takeover.getBody());
		assertNotEquals(storeBClassId, takeover.getBody().getId());

		ResponseEntity<ReadableTaxClass> unchanged = testRestTemplate.exchange(
				"/api/v1/private/tax/class/" + classCode + "?store=" + storeCode, HttpMethod.GET,
				new HttpEntity<>(getHeader()), ReadableTaxClass.class);
		assertTrue(unchanged.getStatusCode().is2xxSuccessful());
		assertEquals(storeBClassId, unchanged.getBody().getId());
		assertEquals(classCode, unchanged.getBody().getCode());
		assertEquals(storeBClassId, taxClassService.getByCode(classCode, storeB).getId());

	}

	@Test
	public void rateAndDescriptionIdsCannotMoveRowsAcrossStores() throws Exception {
		String storeCode = createSecondStore();
		MerchantStore storeB = merchantStoreService.getByCode(storeCode);
		String classCode = unique("CB");
		createTaxClass(storeCode, classCode, null);
		String rateCode = unique("RB");
		Long storeBRateId = createTaxRate(storeCode, rateCode, null, "B rate", classCode);
		TaxRate storeBRate = taxRateService.getById(storeBRateId, storeB);
		Long descriptionId = storeBRate.getDescriptions().get(0).getId();

		String takeoverCode = unique("RD");
		ResponseEntity<Entity> rateTakeover = testRestTemplate.exchange(
				"/api/v1/private/tax/rate?store=" + MerchantStore.DEFAULT_STORE, POST,
				new HttpEntity<>(taxRate(takeoverCode, storeBRateId, "Default takeover", descriptionId, "DEFAULT"),
						getHeader()), Entity.class);
		assertTrue("Expected successful rate create, got " + rateTakeover.getStatusCode(),
				rateTakeover.getStatusCode().is2xxSuccessful());
		assertNotEquals(storeBRateId, rateTakeover.getBody().getId());

		TaxRate unchanged = taxRateService.getById(storeBRateId, storeB);
		assertEquals(rateCode, unchanged.getCode());
		assertEquals(storeB.getId(), unchanged.getMerchantStore().getId());
		assertEquals("B rate", unchanged.getDescriptions().get(0).getName());

		ResponseEntity<ReadableTaxRate> crossStoreRead = testRestTemplate.exchange(
				"/api/v1/private/tax/rate/" + storeBRateId + "?store=" + MerchantStore.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableTaxRate.class);
		assertEquals("Cross-store rate GET status", 400, crossStoreRead.getStatusCode().value());

		String descriptionHijackCode = unique("RH");
		ResponseEntity<Entity> descriptionHijack = testRestTemplate.exchange(
				"/api/v1/private/tax/rate?store=" + MerchantStore.DEFAULT_STORE, POST,
				new HttpEntity<>(taxRate(descriptionHijackCode, null, "New default", descriptionId, "DEFAULT"),
						getHeader()), Entity.class);
		assertTrue("Expected successful create with a client-supplied description id, got "
				+ descriptionHijack.getStatusCode(), descriptionHijack.getStatusCode().is2xxSuccessful());
		TaxRateDescription unchangedDescription = taxRateService.getById(storeBRateId, storeB).getDescriptions().get(0);
		assertEquals("B rate", unchangedDescription.getName());
		assertEquals(storeBRateId, unchangedDescription.getTaxRate().getId());
	}

	@Test
	public void crossStoreUpdatesAreRejectedAndRowsRemainUnchanged() throws Exception {
		String storeCode = createSecondStore();
		MerchantStore storeB = merchantStoreService.getByCode(storeCode);
		String classCode = unique("CB");
		Long classId = createTaxClass(storeCode, classCode, null);
		String rateCode = unique("RB");
		Long rateId = createTaxRate(storeCode, rateCode, null, "B rate", classCode);

		ResponseEntity<Void> classUpdate = testRestTemplate.exchange(
				"/api/v1/private/tax/class/" + classId + "?store=" + MerchantStore.DEFAULT_STORE, PUT,
				new HttpEntity<>(taxClass(classCode, "Changed from DEFAULT", null), getHeader()), Void.class);
		ResponseEntity<Void> rateUpdate = testRestTemplate.exchange(
				"/api/v1/private/tax/rate/" + rateId + "?store=" + MerchantStore.DEFAULT_STORE, PUT,
				new HttpEntity<>(taxRate(rateCode, null, "Changed from DEFAULT", null, classCode), getHeader()),
				Void.class);
		assertFalse(classUpdate.getStatusCode().is2xxSuccessful());
		assertFalse(rateUpdate.getStatusCode().is2xxSuccessful());
		assertEquals(classId, taxClassService.getByCode(classCode, storeB).getId());
		assertNull(taxClassService.getByCode(classCode,
				merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE)));
		TaxRate unchanged = taxRateService.getById(rateId, storeB);
		assertEquals(rateCode, unchanged.getCode());
		assertEquals("B rate", unchanged.getDescriptions().get(0).getName());
	}

	@Test
	public void sameStoreUpdatesPreserveEntityAndDescriptionIds() throws Exception {
		MerchantStore defaultStore = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);
		String classCode = unique("CD");
		Long classId = createTaxClass(MerchantStore.DEFAULT_STORE, classCode, null);
		PersistableTaxClass classUpdateBody = taxClass(classCode, "Updated class", 999999L);
		ResponseEntity<Void> classUpdate = testRestTemplate.exchange(
				"/api/v1/private/tax/class/" + classId + "?store=" + MerchantStore.DEFAULT_STORE, PUT,
				new HttpEntity<>(classUpdateBody, getHeader()), Void.class);
		assertTrue(classUpdate.getStatusCode().is2xxSuccessful());
		assertEquals(classId, taxClassService.getByCode(classCode, defaultStore).getId());
		assertEquals("Updated class", taxClassService.getByCode(classCode, defaultStore).getTitle());

		String rateCode = unique("RD");
		Long rateId = createTaxRate(MerchantStore.DEFAULT_STORE, rateCode, null, "Initial rate", "DEFAULT");
		TaxRate before = taxRateService.getById(rateId, defaultStore);
		Long descriptionId = before.getDescriptions().get(0).getId();
		PersistableTaxRate rateUpdateBody = taxRate(rateCode, 999999L, "Updated rate", 888888L, "DEFAULT");
		rateUpdateBody.setRate(new BigDecimal("7.25"));
		ResponseEntity<Void> rateUpdate = testRestTemplate.exchange(
				"/api/v1/private/tax/rate/" + rateId + "?store=" + MerchantStore.DEFAULT_STORE, PUT,
				new HttpEntity<>(rateUpdateBody, getHeader()), Void.class);
		assertTrue(rateUpdate.getStatusCode().is2xxSuccessful());
		TaxRate updatedRate = taxRateService.getById(rateId, defaultStore);
		assertEquals(rateId, updatedRate.getId());
		assertEquals(new BigDecimal("7.2500"), updatedRate.getTaxRate());
		assertEquals(1, updatedRate.getDescriptions().size());
		assertEquals(descriptionId, updatedRate.getDescriptions().get(0).getId());
		assertEquals("Updated rate", updatedRate.getDescriptions().get(0).getName());

		ResponseEntity<Void> deleteRate = testRestTemplate.exchange(
				"/api/v1/private/tax/rate/" + rateId + "?store=" + MerchantStore.DEFAULT_STORE, DELETE,
				new HttpEntity<>(getHeader()), Void.class);
		ResponseEntity<Void> deleteClass = testRestTemplate.exchange(
				"/api/v1/private/tax/class/" + classId + "?store=" + MerchantStore.DEFAULT_STORE, DELETE,
				new HttpEntity<>(getHeader()), Void.class);
		assertTrue(deleteRate.getStatusCode().is2xxSuccessful());
		assertTrue(deleteClass.getStatusCode().is2xxSuccessful());
	}

	@Test
	public void serviceGuardsRejectCrossStoreIdsInRealContext() throws Exception {
		String storeCode = createSecondStore();
		MerchantStore storeB = merchantStoreService.getByCode(storeCode);
		MerchantStore defaultStore = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);
		String classCode = unique("CB");
		Long classId = createTaxClass(storeCode, classCode, null);
		String rateCode = unique("RB");
		Long rateId = createTaxRate(storeCode, rateCode, null, "B rate", classCode);

		TaxClass crossStoreClass = new TaxClass(classCode);
		crossStoreClass.setId(classId);
		crossStoreClass.setTitle("Attempted overwrite");
		crossStoreClass.setMerchantStore(defaultStore);
		assertServiceException(() -> taxClassService.saveOrUpdate(crossStoreClass));

		TaxRate crossStoreRate = taxRateService.getById(rateId, storeB);
		crossStoreRate.setMerchantStore(defaultStore);
		crossStoreRate.setCode(unique("RX"));
		assertServiceException(() -> taxRateService.saveOrUpdate(crossStoreRate));

		assertEquals(classId, taxClassService.getByCode(classCode, storeB).getId());
		assertEquals(storeB.getId(), taxRateService.getById(rateId, storeB).getMerchantStore().getId());
	}

	private String createSecondStore() {
		String code = unique("B");
		PersistableAddress address = new PersistableAddress();
		address.setAddress("101 Test Street");
		address.setPostalCode("10001");
		address.setCountry("US");
		address.setCity("New York");
		address.setStateProvince("NY");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail(code.toLowerCase() + "@example.com");
		store.setName(code);
		store.setPhone("212-555-0100");
		store.setSupportedLanguages(Collections.singletonList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertTrue("Store create failed with " + response.getStatusCode(), response.getStatusCode().is2xxSuccessful());
		return code;
	}

	private Long createTaxClass(String store, String code, Long id) {
		PersistableTaxClass taxClass = taxClass(code, code + " title", id);
		ResponseEntity<Entity> response = testRestTemplate.exchange("/api/v1/private/tax/class?store=" + store, POST,
				new HttpEntity<>(taxClass, getHeader()), Entity.class);
		assertTrue("Tax class create failed with " + response.getStatusCode(), response.getStatusCode().is2xxSuccessful());
		return response.getBody().getId();
	}

	private Long createTaxRate(String store, String code, Long id, String description, String taxClass) {
		PersistableTaxRate rate = taxRate(code, id, description, null, taxClass);
		ResponseEntity<Entity> response = testRestTemplate.exchange("/api/v1/private/tax/rate?store=" + store, POST,
				new HttpEntity<>(rate, getHeader()), Entity.class);
		assertTrue("Tax rate create failed with " + response.getStatusCode(), response.getStatusCode().is2xxSuccessful());
		return response.getBody().getId();
	}

	private static PersistableTaxClass taxClass(String code, String name, Long id) {
		PersistableTaxClass taxClass = new PersistableTaxClass();
		taxClass.setId(id);
		taxClass.setCode(code);
		taxClass.setName(name);
		return taxClass;
	}

	private static PersistableTaxRate taxRate(String code, Long id, String name, Long descriptionId, String taxClass) {
		PersistableTaxRate rate = new PersistableTaxRate();
		rate.setId(id);
		rate.setCode(code);
		rate.setRate(new BigDecimal("5.00"));
		rate.setCountry("US");
		rate.setZone("NY");
		rate.setPriority(0);
		rate.setStore(MerchantStore.DEFAULT_STORE);
		rate.setTaxClass(taxClass);
		com.salesmanager.shop.model.tax.TaxRateDescription description =
				new com.salesmanager.shop.model.tax.TaxRateDescription();
		description.setId(descriptionId);
		description.setLanguage("en");
		description.setName(name);
		description.setDescription(name);
		rate.setDescriptions(Arrays.asList(description));
		return rate;
	}

	private static String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
	}

	private static void assertServiceException(ServiceCall call) throws Exception {
		try {
			call.run();
		} catch (ServiceException expected) {
			return;
		}
		throw new AssertionError("Expected ServiceException");
	}

	@FunctionalInterface
	private interface ServiceCall {
		Object run() throws Exception;
	}
}
