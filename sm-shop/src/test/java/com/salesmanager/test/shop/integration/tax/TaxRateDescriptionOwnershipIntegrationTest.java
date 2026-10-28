package com.salesmanager.test.shop.integration.tax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import com.salesmanager.core.business.services.tax.TaxRateService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.core.model.tax.taxrate.TaxRateDescription;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.ReadableTaxRate;
import com.salesmanager.test.shop.common.TaxRateTestSupport;

@RunWith(SpringRunner.class)
public class TaxRateDescriptionOwnershipIntegrationTest extends TaxRateTestSupport {

	@Autowired
	private TaxRateService taxRateService;
	@Autowired
	private MerchantStoreService merchantStoreService;

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
