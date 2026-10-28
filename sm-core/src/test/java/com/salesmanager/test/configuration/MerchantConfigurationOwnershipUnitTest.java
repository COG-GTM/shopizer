package com.salesmanager.test.configuration;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.system.MerchantConfigurationRepository;
import com.salesmanager.core.business.services.system.MerchantConfigurationServiceImpl;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfig;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;

@RunWith(MockitoJUnitRunner.class)
public class MerchantConfigurationOwnershipUnitTest {

	private static final Integer STORE_ID = 10;
	private static final Long CONFIGURATION_ID = 42L;

	@Mock
	private MerchantConfigurationRepository repository;

	private MerchantConfigurationServiceImpl service;

	@Before
	public void setUp() {
		service = new MerchantConfigurationServiceImpl(repository);
	}

	@Test
	public void nonPositiveIdsUseCreatePath() throws Exception {
		service.saveOrUpdate(configuration(0L, STORE_ID));
		service.saveOrUpdate(configuration(-1L, STORE_ID));

		verify(repository, times(2)).saveAndFlush(any(MerchantConfiguration.class));
		verify(repository, never()).findMerchantStoreIdById(any(Long.class));
	}

	@Test
	public void sameStoreIdAllowsUpdateWithDifferentStoreInstance() throws Exception {
		when(repository.findMerchantStoreIdById(CONFIGURATION_ID)).thenReturn(STORE_ID);
		MerchantConfiguration config = configuration(CONFIGURATION_ID, STORE_ID);

		service.saveOrUpdate(config);

		verify(repository).saveAndFlush(config);
	}

	@Test
	public void ownershipRejectionMessageContainsConfigurationId() throws Exception {
		when(repository.findMerchantStoreIdById(CONFIGURATION_ID)).thenReturn(null);

		try {
			service.saveOrUpdate(configuration(CONFIGURATION_ID, STORE_ID));
			fail("Expected ServiceException");
		} catch (ServiceException e) {
			assertTrue(e.getMessage().contains(CONFIGURATION_ID.toString()));
		}
	}

	@Test
	public void saveMerchantConfigDoesNotUseOwnershipLookup() throws Exception {
		MerchantStore store = store(STORE_ID);
		MerchantConfiguration existing = configuration(7L, STORE_ID);
		existing.setKey(MerchantConfigurationType.CONFIG.name());
		existing.setMerchantConfigurationType(MerchantConfigurationType.CONFIG);
		when(repository.findByMerchantStoreAndKey(STORE_ID, MerchantConfigurationType.CONFIG.name()))
				.thenReturn(existing);

		service.saveMerchantConfig(new MerchantConfig(), store);

		verify(repository).saveAndFlush(existing);
		verify(repository, never()).findMerchantStoreIdById(any(Long.class));
	}

	private MerchantConfiguration configuration(Long id, Integer storeId) {
		MerchantConfiguration config = new MerchantConfiguration();
		config.setId(id);
		config.setMerchantStore(store(storeId));
		config.setKey("facebook_page_url");
		config.setValue("https://facebook.com/store");
		return config;
	}

	private MerchantStore store(Integer id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}
}
