package com.salesmanager.test.business.tax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.tax.TaxClassRepository;
import com.salesmanager.core.business.repositories.tax.TaxRateRepository;
import com.salesmanager.core.business.services.tax.TaxClassServiceImpl;
import com.salesmanager.core.business.services.tax.TaxRateServiceImpl;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.tax.taxclass.TaxClass;
import com.salesmanager.core.model.tax.taxrate.TaxRate;

public class TaxServiceStoreIsolationTest {

	private TaxClassRepository taxClassRepository;
	private TaxRateRepository taxRateRepository;
	private TaxClassServiceImpl taxClassService;
	private TaxRateServiceImpl taxRateService;

	private MerchantStore acme;
	private MerchantStore globex;

	@Before
	public void setUp() {
		taxClassRepository = mock(TaxClassRepository.class);
		taxRateRepository = mock(TaxRateRepository.class);
		taxClassService = new TaxClassServiceImpl(taxClassRepository);
		taxRateService = new TaxRateServiceImpl(taxRateRepository);
		acme = store(1, "ACME");
		globex = store(2, "GLOBEX");
	}

	@Test
	public void taxClassUpdateOfAnotherStoresRowIsRejected() {
		TaxClass victim = taxClass(7L, globex);
		when(taxClassRepository.findById(7L)).thenReturn(Optional.of(victim));

		try {
			taxClassService.saveOrUpdate(taxClass(7L, acme));
			fail("expected ServiceException");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		verify(taxClassRepository, never()).saveAndFlush(any());
	}

	@Test
	public void taxClassUpdateOfUnknownIdIsRejected() {
		when(taxClassRepository.findById(42L)).thenReturn(Optional.empty());

		try {
			taxClassService.saveOrUpdate(taxClass(42L, acme));
			fail("expected ServiceException");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		verify(taxClassRepository, never()).saveAndFlush(any());
	}

	@Test
	public void taxClassUpdateOfOwnRowIsSaved() throws Exception {
		TaxClass own = taxClass(5L, acme);
		when(taxClassRepository.findById(5L)).thenReturn(Optional.of(taxClass(5L, store(1, "ACME"))));

		taxClassService.saveOrUpdate(own);

		verify(taxClassRepository).saveAndFlush(own);
	}

	@Test
	public void taxClassWithoutIdIsInserted() throws Exception {
		TaxClass created = taxClass(null, acme);
		TaxClass persisted = taxClass(11L, acme);
		when(taxClassRepository.saveAndFlush(created)).thenReturn(persisted);

		assertSame(persisted, taxClassService.saveOrUpdate(created));
		verify(taxClassRepository, never()).findById(any());
	}

	@Test
	public void taxRateUpdateOfAnotherStoresRowIsRejected() {
		when(taxRateRepository.findByStoreAndId(acme.getId(), 9L)).thenReturn(null);

		try {
			taxRateService.saveOrUpdate(taxRate(9L, acme));
			fail("expected ServiceException");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		verify(taxRateRepository, never()).saveAndFlush(any());
	}

	@Test
	public void taxRateUpdateOfOwnRowIsSaved() throws Exception {
		TaxRate own = taxRate(9L, acme);
		when(taxRateRepository.findByStoreAndId(acme.getId(), 9L)).thenReturn(taxRate(9L, acme));

		taxRateService.saveOrUpdate(own);

		verify(taxRateRepository).saveAndFlush(own);
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	private static TaxClass taxClass(Long id, MerchantStore store) {
		TaxClass taxClass = new TaxClass();
		taxClass.setId(id);
		taxClass.setCode("X");
		taxClass.setMerchantStore(store);
		return taxClass;
	}

	private static TaxRate taxRate(Long id, MerchantStore store) {
		TaxRate taxRate = new TaxRate();
		taxRate.setId(id);
		taxRate.setCode("R");
		taxRate.setMerchantStore(store);
		return taxRate;
	}
}
