package com.salesmanager.test.tax;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.tax.TaxRateRepository;
import com.salesmanager.core.business.services.tax.TaxRateServiceImpl;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.core.model.tax.taxrate.TaxRateDescription;

public class TaxRateServiceImplTest {

	private TaxRateRepository repository;
	private TaxRateServiceImpl service;

	@Before
	public void setUp() {
		repository = mock(TaxRateRepository.class);
		service = new TaxRateServiceImpl(repository);
	}

	private static MerchantStore store(int id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}

	private static TaxRate rate(Long id, MerchantStore store) {
		TaxRate rate = new TaxRate();
		rate.setId(id);
		rate.setMerchantStore(store);
		return rate;
	}

	@Test
	public void updateOfAnotherStoresTaxRateIsRejected() {
		when(repository.findById(12L)).thenReturn(Optional.of(rate(12L, store(1))));

		try {
			service.saveOrUpdate(rate(12L, store(2)));
			fail("expected ServiceException");
		} catch (ServiceException expected) {
		}
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void updateOfUnknownTaxRateIsRejected() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		try {
			service.saveOrUpdate(rate(99L, store(2)));
			fail("expected ServiceException");
		} catch (ServiceException expected) {
		}
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void updateOfOwnTaxRateIsSaved() throws Exception {
		when(repository.findById(12L)).thenReturn(Optional.of(rate(12L, store(2))));
		TaxRate rate = rate(12L, store(2));

		service.saveOrUpdate(rate);

		verify(repository).saveAndFlush(rate);
	}

	@Test
	public void updateReferencingForeignDescriptionIsRejected() {
		when(repository.findById(12L)).thenReturn(Optional.of(rate(12L, store(2))));
		when(repository.findDescriptionIds(12L)).thenReturn(Arrays.asList(3L));
		TaxRate rate = rate(12L, store(2));
		TaxRateDescription own = new TaxRateDescription();
		own.setId(3L);
		TaxRateDescription foreign = new TaxRateDescription();
		foreign.setId(7L);
		rate.getDescriptions().add(own);
		rate.getDescriptions().add(foreign);

		try {
			service.saveOrUpdate(rate);
			fail("expected ServiceException");
		} catch (ServiceException expected) {
		}
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void updateKeepingOwnDescriptionsIsSaved() throws Exception {
		when(repository.findById(12L)).thenReturn(Optional.of(rate(12L, store(2))));
		when(repository.findDescriptionIds(12L)).thenReturn(Arrays.asList(3L));
		TaxRate rate = rate(12L, store(2));
		TaxRateDescription own = new TaxRateDescription();
		own.setId(3L);
		rate.getDescriptions().add(own);
		rate.getDescriptions().add(new TaxRateDescription());

		service.saveOrUpdate(rate);

		verify(repository).saveAndFlush(rate);
	}

	@Test
	public void newTaxRateReferencingExistingDescriptionIsRejected() {
		TaxRate rate = rate(null, store(2));
		TaxRateDescription description = new TaxRateDescription();
		description.setId(7L);
		rate.getDescriptions().add(description);

		try {
			service.saveOrUpdate(rate);
			fail("expected ServiceException");
		} catch (ServiceException expected) {
		}
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void newTaxRateIsInserted() throws Exception {
		TaxRate rate = rate(null, store(2));
		rate.getDescriptions().add(new TaxRateDescription());
		when(repository.saveAndFlush(rate)).thenReturn(rate);

		assertSame(rate, service.saveOrUpdate(rate));
		verify(repository, never()).findById(any());
	}
}
