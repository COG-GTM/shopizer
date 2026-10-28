package com.salesmanager.test.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.system.MerchantConfigurationRepository;
import com.salesmanager.core.business.services.system.MerchantConfigurationServiceImpl;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;

public class MerchantConfigurationServiceImplTest {

	private static final Integer STORE_A = 1;
	private static final Integer STORE_B = 2;

	private MerchantConfigurationRepository repository;
	private MerchantConfigurationServiceImpl service;

	@Before
	public void setUp() {
		repository = mock(MerchantConfigurationRepository.class);
		service = new MerchantConfigurationServiceImpl(repository);
	}

	@Test
	public void saveOrUpdateRejectsRowOwnedByAnotherStore() {
		when(repository.findMerchantStoreIdById(42L)).thenReturn(STORE_B);

		try {
			service.saveOrUpdate(configuration(42L, STORE_A));
			fail("Expected ServiceException");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void saveOrUpdateRejectsUnknownId() {
		when(repository.findMerchantStoreIdById(42L)).thenReturn(null);

		try {
			service.saveOrUpdate(configuration(42L, STORE_A));
			fail("Expected ServiceException");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void saveOrUpdateRejectsMissingStore() {
		when(repository.findMerchantStoreIdById(42L)).thenReturn(STORE_A);
		MerchantConfiguration config = configuration(42L, STORE_A);
		config.setMerchantStore(null);

		try {
			service.saveOrUpdate(config);
			fail("Expected ServiceException");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void saveOrUpdateUpdatesRowOwnedBySameStore() throws ServiceException {
		when(repository.findMerchantStoreIdById(42L)).thenReturn(STORE_A);
		MerchantConfiguration config = configuration(42L, STORE_A);

		service.saveOrUpdate(config);

		verify(repository).saveAndFlush(config);
	}

	@Test
	public void saveOrUpdateCreatesNewRowWithoutOwnershipLookup() throws ServiceException {
		MerchantConfiguration config = configuration(null, STORE_A);

		service.saveOrUpdate(config);

		verify(repository, never()).findMerchantStoreIdById(any());
		verify(repository).saveAndFlush(config);
	}

	private MerchantConfiguration configuration(Long id, Integer storeId) {
		MerchantStore store = new MerchantStore();
		store.setId(storeId);
		MerchantConfiguration config = new MerchantConfiguration();
		config.setId(id);
		config.setMerchantStore(store);
		config.setKey("facebook_page_url");
		config.setValue("https://facebook.com/store");
		return config;
	}
}
