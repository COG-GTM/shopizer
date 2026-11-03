package com.salesmanager.test.business.system;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

public class MerchantConfigurationServiceStoreScopeTest {

	private MerchantConfigurationRepository repository;
	private MerchantConfigurationServiceImpl service;

	@BeforeEach
	public void setUp() {
		repository = mock(MerchantConfigurationRepository.class);
		service = new MerchantConfigurationServiceImpl(repository);
	}

	private MerchantConfiguration configuration(Long id, MerchantStore store) {
		MerchantConfiguration configuration = new MerchantConfiguration();
		configuration.setId(id);
		configuration.setKey("KEY");
		configuration.setMerchantStore(store);
		return configuration;
	}

	private MerchantStore store(Integer id) {
		return new MerchantStore(id, "STORE" + id, "Store " + id);
	}

	@Test
	public void saveOrUpdateWithoutIdCreatesWithoutLookup() throws Exception {
		MerchantConfiguration created = configuration(null, store(1));

		service.saveOrUpdate(created);

		verify(repository, never()).findById(anyLong());
		verify(repository).saveAndFlush(created);
	}

	@Test
	public void saveOrUpdateWithZeroIdIsTreatedAsCreate() throws Exception {
		MerchantConfiguration created = configuration(0L, store(1));

		service.saveOrUpdate(created);

		verify(repository, never()).findById(anyLong());
		verify(repository).saveAndFlush(created);
	}

	@Test
	public void saveOrUpdateRejectsWhenCallerStoreIsMissing() {
		when(repository.findById(10L)).thenReturn(Optional.of(configuration(10L, store(1))));

		assertThrows(ServiceException.class, () -> service.saveOrUpdate(configuration(10L, null)));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void saveOrUpdateRejectsWhenExistingRowHasNoStore() {
		when(repository.findById(10L)).thenReturn(Optional.of(configuration(10L, null)));

		assertThrows(ServiceException.class, () -> service.saveOrUpdate(configuration(10L, store(1))));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	public void saveOrUpdateComparesStoresByIdNotInstance() throws Exception {
		when(repository.findById(10L)).thenReturn(Optional.of(configuration(10L, store(7))));
		MerchantConfiguration update = configuration(10L, store(7));

		service.saveOrUpdate(update);

		verify(repository).saveAndFlush(update);
	}
}
