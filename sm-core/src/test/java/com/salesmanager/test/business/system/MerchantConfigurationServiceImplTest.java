package com.salesmanager.test.business.system;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.system.MerchantConfigurationRepository;
import com.salesmanager.core.business.services.system.MerchantConfigurationServiceImpl;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;

public class MerchantConfigurationServiceImplTest {

	private MerchantConfigurationRepository repository;
	private MerchantConfigurationServiceImpl service;

	@BeforeEach
	public void setUp() {
		repository = mock(MerchantConfigurationRepository.class);
		service = new MerchantConfigurationServiceImpl(repository);
	}

	private MerchantConfiguration configuration(Long id, Integer storeId) {
		MerchantConfiguration configuration = new MerchantConfiguration();
		configuration.setId(id);
		configuration.setKey("KEY");
		configuration.setMerchantStore(new MerchantStore(storeId, "STORE" + storeId, "Store " + storeId));
		return configuration;
	}

	@Test
	public void saveOrUpdateRejectsRowOfAnotherStore() {
		when(repository.findById(10L)).thenReturn(Optional.of(configuration(10L, 2)));

		assertThrows(ServiceException.class, () -> service.saveOrUpdate(configuration(10L, 1)));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void saveOrUpdateRejectsUnknownId() {
		when(repository.findById(10L)).thenReturn(Optional.empty());

		assertThrows(ServiceException.class, () -> service.saveOrUpdate(configuration(10L, 1)));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void saveOrUpdateUpdatesRowOfSameStore() throws Exception {
		when(repository.findById(10L)).thenReturn(Optional.of(configuration(10L, 1)));
		MerchantConfiguration update = configuration(10L, 1);

		service.saveOrUpdate(update);

		verify(repository).saveAndFlush(update);
	}
}
