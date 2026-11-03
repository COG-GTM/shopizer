package com.salesmanager.test.tax;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.tax.TaxRateRepository;
import com.salesmanager.core.business.services.tax.TaxRateServiceImpl;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.tax.taxrate.TaxRate;

@RunWith(MockitoJUnitRunner.class)
public class TaxRateServiceImplTest {

	@Mock
	private TaxRateRepository repository;

	private TaxRateServiceImpl service;

	@Before
	public void setUp() {
		service = new TaxRateServiceImpl(repository);
	}

	@Test
	public void saveWithoutIdUsesSaveAndFlush() throws Exception {
		TaxRate taxRate = new TaxRate();
		taxRate.setMerchantStore(store(1));
		when(repository.saveAndFlush(taxRate)).thenReturn(taxRate);

		assertSame(taxRate, service.saveOrUpdate(taxRate));

		verify(repository).saveAndFlush(taxRate);
		verify(repository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	public void updateWithMissingIdIsRejected() throws Exception {
		TaxRate taxRate = taxRate(11L, store(1));
		when(repository.findById(11L)).thenReturn(Optional.empty());

		assertServiceException(taxRate);

		verify(repository).findById(11L);
		verifyNoSave();
	}

	@Test
	public void updateFromAnotherStoreIsRejected() throws Exception {
		TaxRate taxRate = taxRate(11L, store(1));
		when(repository.findById(11L)).thenReturn(Optional.of(taxRate(11L, store(2))));

		assertServiceException(taxRate);

		verify(repository).findById(11L);
		verifyNoSave();
	}

	@Test
	public void updateWithNullStoreIsRejected() throws Exception {
		TaxRate taxRate = taxRate(11L, null);
		when(repository.findById(11L)).thenReturn(Optional.of(taxRate(11L, store(1))));

		assertServiceException(taxRate);

		verify(repository).findById(11L);
		verifyNoSave();
	}

	@Test
	public void updateInSameStoreSavesEntity() throws Exception {
		TaxRate taxRate = taxRate(11L, store(1));
		when(repository.findById(11L)).thenReturn(Optional.of(taxRate(11L, store(1))));

		assertSame(taxRate, service.saveOrUpdate(taxRate));

		verify(repository).findById(11L);
		verify(repository).saveAndFlush(taxRate);
	}

	private void assertServiceException(TaxRate taxRate) throws Exception {
		try {
			service.saveOrUpdate(taxRate);
			fail("Expected ServiceException");
		} catch (ServiceException expected) {
			// expected
		}
	}

	private void verifyNoSave() {
		verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(TaxRate.class));
		verify(repository, never()).save(org.mockito.ArgumentMatchers.any(TaxRate.class));
	}

	private static TaxRate taxRate(Long id, MerchantStore store) {
		TaxRate taxRate = new TaxRate();
		taxRate.setId(id);
		taxRate.setMerchantStore(store);
		return taxRate;
	}

	private static MerchantStore store(int id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}
}
