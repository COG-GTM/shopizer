package com.salesmanager.test.shop.unit.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.store.MerchantConfigEntity;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacadeImpl;

@ExtendWith(MockitoExtension.class)
public class StoreFacadeBrandTest {

	@Mock
	private MerchantStoreService merchantStoreService;

	@Mock
	private MerchantConfigurationService merchantConfigurationService;

	@InjectMocks
	private StoreFacadeImpl storeFacade;

	private MerchantStore store;

	@BeforeEach
	public void setUp() throws Exception {
		store = new MerchantStore(1, "MALLORY", "Mallory");
		when(merchantStoreService.getByCode("MALLORY")).thenReturn(store);
	}

	private PersistableBrand brand(Long id, String key, String value) {
		MerchantConfigEntity entity = new MerchantConfigEntity();
		entity.setId(id);
		entity.setKey(key);
		entity.setValue(value);
		entity.setActive(true);
		PersistableBrand brand = new PersistableBrand();
		brand.getSocialNetworks().add(entity);
		return brand;
	}

	@Test
	public void rejectsReservedKey() throws Exception {
		assertThrows(OperationNotAllowedException.class,
				() -> storeFacade.createBrand("MALLORY", brand(null, "EMAIL_CONFIG", "{\"host\":\"evil\"}")));
		verify(merchantConfigurationService, never()).saveOrUpdate(any());
	}

	@Test
	public void ignoresClientSuppliedIdForNewRow() throws Exception {
		storeFacade.createBrand("MALLORY", brand(42L, Constants.KEY_INSTAGRAM_URL, "https://instagram.com/mallory"));

		ArgumentCaptor<MerchantConfiguration> captor = ArgumentCaptor.forClass(MerchantConfiguration.class);
		verify(merchantConfigurationService).saveOrUpdate(captor.capture());
		MerchantConfiguration saved = captor.getValue();
		assertNull(saved.getId());
		assertSame(store, saved.getMerchantStore());
		assertEquals(MerchantConfigurationType.SOCIAL, saved.getMerchantConfigurationType());
	}

	@Test
	public void updatesExistingRowFoundByKeyAndStore() throws Exception {
		MerchantConfiguration existing = new MerchantConfiguration();
		existing.setId(7L);
		existing.setKey(Constants.KEY_INSTAGRAM_URL);
		existing.setMerchantStore(store);
		existing.setMerchantConfigurationType(MerchantConfigurationType.SOCIAL);
		when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_INSTAGRAM_URL, store)).thenReturn(existing);

		storeFacade.createBrand("MALLORY", brand(42L, Constants.KEY_INSTAGRAM_URL, "https://instagram.com/new"));

		ArgumentCaptor<MerchantConfiguration> captor = ArgumentCaptor.forClass(MerchantConfiguration.class);
		verify(merchantConfigurationService).saveOrUpdate(captor.capture());
		assertSame(existing, captor.getValue());
		assertEquals(Long.valueOf(7L), captor.getValue().getId());
		assertEquals("https://instagram.com/new", captor.getValue().getValue());
	}

	@Test
	public void rejectsAllowedKeyBackedByNonSocialRow() throws Exception {
		MerchantConfiguration existing = new MerchantConfiguration();
		existing.setId(8L);
		existing.setKey(Constants.KEY_GOOGLE_ANALYTICS_URL);
		existing.setMerchantStore(store);
		existing.setMerchantConfigurationType(MerchantConfigurationType.CONFIG);
		when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_GOOGLE_ANALYTICS_URL, store)).thenReturn(existing);

		assertThrows(OperationNotAllowedException.class,
				() -> storeFacade.createBrand("MALLORY", brand(null, Constants.KEY_GOOGLE_ANALYTICS_URL, "UA-1")));
		verify(merchantConfigurationService, never()).saveOrUpdate(any());
		verify(merchantConfigurationService, never()).delete(any());
	}
}
