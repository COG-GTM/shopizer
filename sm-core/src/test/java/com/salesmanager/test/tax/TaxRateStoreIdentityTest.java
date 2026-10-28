package com.salesmanager.test.tax;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.tax.TaxRateRepository;
import com.salesmanager.core.business.services.tax.TaxRateServiceImpl;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.tax.taxrate.TaxRate;

public class TaxRateStoreIdentityTest {

	@Test
	public void rejectsMissingPersistedStore() throws Exception {
		assertRejected(null, store(2));
	}

	@Test
	public void rejectsMissingTargetStore() throws Exception {
		assertRejected(store(2), null);
	}

	@Test
	public void rejectsUnpersistedStoreOnExistingRate() throws Exception {
		assertRejected(store(null), store(2));
	}

	@Test
	public void rejectsUnpersistedTargetStore() throws Exception {
		assertRejected(store(2), store(null));
	}

	private void assertRejected(MerchantStore persistedStore, MerchantStore targetStore) throws Exception {
		TaxRateRepository repository = mock(TaxRateRepository.class);
		TaxRate existing = new TaxRate();
		existing.setId(12L);
		existing.setMerchantStore(persistedStore);
		when(repository.findById(12L)).thenReturn(Optional.of(existing));
		TaxRate incoming = new TaxRate();
		incoming.setId(12L);
		incoming.setMerchantStore(targetStore);

		try {
			new TaxRateServiceImpl(repository).saveOrUpdate(incoming);
			fail("An update without both persisted store identities must be rejected");
		} catch (ServiceException expected) {
			assertTrue(expected.getMessageCode().contains("does not belong to the target store"));
		}
		verify(repository, never()).saveAndFlush(any());
	}

	private MerchantStore store(Integer id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}
}
