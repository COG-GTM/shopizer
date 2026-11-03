package com.salesmanager.test.tax;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.tax.TaxClassRepository;
import com.salesmanager.core.business.services.tax.TaxClassServiceImpl;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.tax.taxclass.TaxClass;

@RunWith(MockitoJUnitRunner.class)
public class TaxClassServiceImplTest {

	@Mock
	private TaxClassRepository repository;

	private TaxClassServiceImpl service;

	@Before
	public void setUp() {
		service = new TaxClassServiceImpl(repository);
	}

	@Test
	public void saveWithoutIdUsesSaveAndFlush() throws Exception {
		TaxClass taxClass = new TaxClass("DEFAULT");
		taxClass.setMerchantStore(store(1));
		when(repository.saveAndFlush(taxClass)).thenReturn(taxClass);

		assertSame(taxClass, service.saveOrUpdate(taxClass));

		verify(repository).saveAndFlush(taxClass);
		verify(repository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	public void updateWithMissingIdIsRejected() throws Exception {
		TaxClass taxClass = taxClass(11L, store(1));
		when(repository.findById(11L)).thenReturn(Optional.empty());

		assertServiceException(taxClass);

		verify(repository).findById(11L);
		verifyNoSave();
	}

	@Test
	public void updateFromAnotherStoreIsRejected() throws Exception {
		TaxClass taxClass = taxClass(11L, store(1));
		when(repository.findById(11L)).thenReturn(Optional.of(taxClass(11L, store(2))));

		assertServiceException(taxClass);

		verify(repository).findById(11L);
		verifyNoSave();
	}

	@Test
	public void updateWithNullStoreIsRejected() throws Exception {
		TaxClass taxClass = taxClass(11L, null);
		when(repository.findById(11L)).thenReturn(Optional.of(taxClass(11L, store(1))));

		assertServiceException(taxClass);

		verify(repository).findById(11L);
		verifyNoSave();
	}

	@Test
	public void updateInSameStoreSavesEntity() throws Exception {
		TaxClass taxClass = taxClass(11L, store(1));
		when(repository.findById(11L)).thenReturn(Optional.of(taxClass(11L, store(1))));

		assertSame(taxClass, service.saveOrUpdate(taxClass));

		verify(repository).findById(11L);
		verify(repository).saveAndFlush(taxClass);
	}

	private void assertServiceException(TaxClass taxClass) throws Exception {
		try {
			service.saveOrUpdate(taxClass);
			fail("Expected ServiceException");
		} catch (ServiceException expected) {
			// expected
		}
	}

	private void verifyNoSave() {
		verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(TaxClass.class));
		verify(repository, never()).save(org.mockito.ArgumentMatchers.any(TaxClass.class));
	}

	private static TaxClass taxClass(Long id, MerchantStore store) {
		TaxClass taxClass = new TaxClass("DEFAULT");
		taxClass.setId(id);
		taxClass.setMerchantStore(store);
		return taxClass;
	}

	private static MerchantStore store(int id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}
}
