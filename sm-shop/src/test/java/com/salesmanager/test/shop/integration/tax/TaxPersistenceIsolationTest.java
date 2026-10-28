package com.salesmanager.test.shop.integration.tax;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import javax.persistence.EntityManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.tax.TaxClassService;
import com.salesmanager.core.business.services.tax.TaxRateService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.tax.taxclass.TaxClass;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.shop.model.tax.PersistableTaxClass;
import com.salesmanager.shop.store.controller.tax.facade.TaxFacade;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@Transactional
public class TaxPersistenceIsolationTest extends ServicesTestSupport {

	@Autowired private MerchantStoreService stores;
	@Autowired private TaxClassService classes;
	@Autowired private TaxRateService rates;
	@Autowired private CountryService countries;
	@Autowired private TaxFacade facade;
	@Autowired private EntityManager entityManager;

	@Test
	public void detachedClassCannotBeMovedToAnotherStore() throws Exception {
		MerchantStore owner = stores.getByCode("DEFAULT");
		TaxClass victim = classes.saveOrUpdate(taxClass("ISOVICTIM", owner));
		MerchantStore foreign = foreignStore(owner);
		Long id = victim.getId();
		entityManager.clear();
		TaxClass request = taxClass("REPLACED", foreign);
		request.setId(id);

		try {
			classes.saveOrUpdate(request);
			fail("A detached row must not be reassigned to another store");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		entityManager.clear();
		TaxClass unchanged = classes.getById(id);
		assertEquals("ISOVICTIM", unchanged.getCode());
		assertEquals(owner.getId(), unchanged.getMerchantStore().getId());
	}

	@Test
	public void detachedRateCannotBeMovedToAnotherStore() throws Exception {
		MerchantStore owner = stores.getByCode("DEFAULT");
		TaxRate victim = new TaxRate();
		victim.setCode("ISORATE");
		victim.setMerchantStore(owner);
		victim.setTaxClass(classes.saveOrUpdate(taxClass("ISORCLASS", owner)));
		victim.setCountry(countries.getByCode("US"));
		victim.setTaxRate(new BigDecimal("5"));
		victim = rates.saveOrUpdate(victim);
		MerchantStore foreign = foreignStore(owner);
		Long id = victim.getId();
		entityManager.clear();
		TaxRate request = new TaxRate();
		request.setId(id);
		request.setCode("REPLACED");
		request.setMerchantStore(foreign);
		request.setTaxClass(victim.getTaxClass());
		request.setCountry(countries.getByCode("US"));
		request.setTaxRate(new BigDecimal("9"));

		try {
			rates.saveOrUpdate(request);
			fail("A detached row must not be reassigned to another store");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		entityManager.clear();
		TaxRate unchanged = rates.getById(id, owner);
		assertNotNull(unchanged);
		assertEquals("ISORATE", unchanged.getCode());
		assertEquals(0, new BigDecimal("5").compareTo(unchanged.getTaxRate()));
	}

	@Test
	public void classUpdateRetainsIdentityAndExistingRateAssociation() throws Exception {
		MerchantStore owner = stores.getByCode("DEFAULT");
		TaxClass target = classes.saveOrUpdate(taxClass("ISOTARGET", owner));
		TaxClass other = classes.saveOrUpdate(taxClass("ISOOTHER", owner));
		TaxRate linked = new TaxRate();
		linked.setCode("ISOLINK");
		linked.setMerchantStore(owner);
		linked.setTaxClass(target);
		linked.setCountry(countries.getByCode("US"));
		linked.setTaxRate(BigDecimal.ONE);
		linked = rates.saveOrUpdate(linked);
		Long targetId = target.getId();
		Long otherId = other.getId();
		Long rateId = linked.getId();
		entityManager.clear();
		PersistableTaxClass request = new PersistableTaxClass();
		request.setId(otherId);
		request.setCode("ISOEDITED");
		request.setName("Edited class");

		facade.updateTaxClass(targetId, request, owner, owner.getDefaultLanguage());
		entityManager.flush();
		entityManager.clear();

		assertEquals("ISOEDITED", classes.getById(targetId).getCode());
		assertEquals("ISOOTHER", classes.getById(otherId).getCode());
		assertEquals(targetId, rates.getById(rateId, owner).getTaxClass().getId());
	}

	private MerchantStore foreignStore(MerchantStore owner) throws Exception {
		MerchantStore foreign = new MerchantStore();
		foreign.setCode("ISOFOREIGN");
		foreign.setStorename("Other store");
		foreign.setCountry(owner.getCountry());
		foreign.setCurrency(owner.getCurrency());
		foreign.setDefaultLanguage(owner.getDefaultLanguage());
		foreign.getLanguages().add(owner.getDefaultLanguage());
		foreign.setStoreEmailAddress("isolation@example.com");
		stores.saveOrUpdate(foreign);
		return foreign;
	}

	private TaxClass taxClass(String code, MerchantStore store) {
		TaxClass result = new TaxClass(code);
		result.setMerchantStore(store);
		return result;
	}
}
