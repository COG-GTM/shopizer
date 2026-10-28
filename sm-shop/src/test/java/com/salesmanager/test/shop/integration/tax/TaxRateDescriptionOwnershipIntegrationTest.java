package com.salesmanager.test.shop.integration.tax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.ReadableTaxRate;
import com.salesmanager.test.shop.common.TaxRateTestSupport;

@RunWith(SpringRunner.class)
public class TaxRateDescriptionOwnershipIntegrationTest extends TaxRateTestSupport {

	@Autowired
	private TaxRateService taxRateService;
	@Autowired
	private MerchantStoreService merchantStoreService;
	@Autowired
	private TaxClassService taxClassService;

	@Test
	public void createCannotMoveAnotherStoresRateOrDescription() throws Exception {
		HttpHeaders headers = getHeader();
		MerchantStore target = merchantStoreService.getByCode("DEFAULT");
		MerchantStore victimStore = new MerchantStore();
		victimStore.setCode(rate("en", "fixture").getCode());
		victimStore.setStorename("Victim store");
		victimStore.setStoreEmailAddress("victim@example.test");
		victimStore.setStorephone("5145550100");
		victimStore.setCountry(target.getCountry());
		victimStore.setCurrency(target.getCurrency());
		victimStore.setDefaultLanguage(target.getDefaultLanguage());
		merchantStoreService.saveOrUpdate(victimStore);
		assertNotEquals(target.getId(), victimStore.getId());
		TaxClass taxClass = new TaxClass("DEFAULT");
		taxClass.setMerchantStore(victimStore);
		taxClassService.create(taxClass);
		TaxRate victim = new TaxRate();
		victim.setCode("victim");
		victim.setMerchantStore(victimStore);
		victim.setCountry(victimStore.getCountry());
		victim.setTaxClass(taxClass);
		victim.setTaxRate(new BigDecimal("9.25"));
		TaxRateDescription description = new TaxRateDescription();
		description.setName("Victim");
		description.setLanguage(target.getDefaultLanguage());
		description.setTaxRate(victim);
		victim.getDescriptions().add(description);
		victim = taxRateService.saveOrUpdate(victim);
		Long victimId = victim.getId();
		Long descriptionId = victim.getDescriptions().get(0).getId();
		PersistableTaxRate request = rate("en", "Independent");
		request.setId(victimId);
		request.getDescriptions().get(0).setId(descriptionId);

		ResponseEntity<Entity> response = testRestTemplate.postForEntity("/api/v1/private/tax/rate?store=DEFAULT",
				new HttpEntity<>(request, headers), Entity.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotEquals(victimId, response.getBody().getId());
		TaxRate created = taxRateService.getById(response.getBody().getId(), target);
		assertEquals(target.getId(), created.getMerchantStore().getId());
		assertNotEquals(descriptionId, created.getDescriptions().get(0).getId());
		TaxRate untouched = taxRateService.getById(victimId, victimStore);
		assertEquals(victimStore.getId(), untouched.getMerchantStore().getId());
		assertEquals("victim", untouched.getCode());
		assertEquals(0, new BigDecimal("9.25").compareTo(untouched.getTaxRate()));
		assertEquals(descriptionId, untouched.getDescriptions().get(0).getId());
		assertEquals("Victim", untouched.getDescriptions().get(0).getName());
	}

	@Test
	public void serviceRejectsDescriptionFromAnotherRateWithoutChangingEitherRow() throws Exception {
		HttpHeaders headers = getHeader();
		Long ownerId = create(rate("en", "owner"), headers);
		Long victimId = create(rate("en", "victim"), headers);
		ReadableTaxRate before = read(victimId, "en", headers);
		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		TaxRate owner = taxRateService.getById(ownerId, store);
		TaxRate victim = taxRateService.getById(victimId, store);
		TaxRateDescription foreign = victim.getDescriptions().get(0);
		owner.setDescriptions(new ArrayList<>(Collections.singletonList(foreign)));

		try {
			taxRateService.saveOrUpdate(owner);
			fail("Service must reject a persisted description owned by another rate");
		} catch (ServiceException expected) {
			assertTrue(expected.getMessageCode().contains("cannot reference descriptions of another tax rate"));
		}
		assertEquals("owner", read(ownerId, "en", headers).getDescription().getName());
		ReadableTaxRate after = read(victimId, "en", headers);
		assertEquals(before.getDescription().getId(), after.getDescription().getId());
		assertEquals("victim", after.getDescription().getName());
	}

	@Test
	public void putAddsNewLanguageWithoutAdoptingForeignDescriptionId() {
		HttpHeaders headers = getHeader();
		Long ownerId = create(rate("en", "owner"), headers);
		Long victimId = create(rate("fr", "victim"), headers);
		ReadableTaxRate ownerBefore = read(ownerId, "en", headers);
		ReadableTaxRate victimBefore = read(victimId, "fr", headers);
		PersistableTaxRate update = rate("fr", "nouveau");
		update.setId(victimId);
		update.getDescriptions().get(0).setId(victimBefore.getDescription().getId());

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/tax/rate/" + ownerId,
				HttpMethod.PUT, new HttpEntity<>(update, headers), Void.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		ReadableTaxRate translated = read(ownerId, "fr", headers);
		assertEquals(ownerId, translated.getId());
		assertEquals("nouveau", translated.getDescription().getName());
		assertNotEquals(victimBefore.getDescription().getId(), translated.getDescription().getId());
		assertEquals(ownerBefore.getDescription().getId(), read(ownerId, "en", headers).getDescription().getId());
		assertEquals("victim", read(victimId, "fr", headers).getDescription().getName());
		assertEquals(victimBefore.getDescription().getId(), read(victimId, "fr", headers).getDescription().getId());
	}
}
