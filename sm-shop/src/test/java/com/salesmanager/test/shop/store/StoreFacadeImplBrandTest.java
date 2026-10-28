package com.salesmanager.test.shop.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.store.MerchantConfigEntity;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.store.api.exception.RestApiException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class StoreFacadeImplBrandTest {

	private static final String STORE_CODE = "MALLORY";

	@Mock
	private MerchantStoreService merchantStoreService;

	@Mock
	private MerchantConfigurationService merchantConfigurationService;

	@InjectMocks
	private StoreFacadeImpl storeFacade;

	private MerchantStore store;

	@Before
	public void setUp() throws Exception {
		store = new MerchantStore();
		store.setId(3);
		store.setCode(STORE_CODE);
		when(merchantStoreService.getByCode(STORE_CODE)).thenReturn(store);
	}

	@Test
	public void clientSuppliedIdIsIgnoredWhenCreating() throws Exception {
		when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_FACEBOOK_PAGE_URL, store))
				.thenReturn(null);

		storeFacade.createBrand(STORE_CODE, brand(42L, Constants.KEY_FACEBOOK_PAGE_URL, "https://facebook.com/mugs"));

		ArgumentCaptor<MerchantConfiguration> saved = ArgumentCaptor.forClass(MerchantConfiguration.class);
		verify(merchantConfigurationService).saveOrUpdate(saved.capture());
		assertNull(saved.getValue().getId());
		assertSame(store, saved.getValue().getMerchantStore());
		assertEquals(Constants.KEY_FACEBOOK_PAGE_URL, saved.getValue().getKey());
		assertEquals("https://facebook.com/mugs", saved.getValue().getValue());
		assertEquals(MerchantConfigurationType.SOCIAL, saved.getValue().getMerchantConfigurationType());
	}

	@Test
	public void clientSuppliedIdIsIgnoredWhenUpdating() throws Exception {
		MerchantConfiguration existing = socialRow(7L, Constants.KEY_INSTAGRAM_URL, "old");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_INSTAGRAM_URL, store))
				.thenReturn(existing);

		storeFacade.createBrand(STORE_CODE, brand(42L, Constants.KEY_INSTAGRAM_URL, "new"));

		ArgumentCaptor<MerchantConfiguration> saved = ArgumentCaptor.forClass(MerchantConfiguration.class);
		verify(merchantConfigurationService).saveOrUpdate(saved.capture());
		assertSame(existing, saved.getValue());
		assertEquals(Long.valueOf(7L), saved.getValue().getId());
		assertSame(store, saved.getValue().getMerchantStore());
		assertEquals("new", saved.getValue().getValue());
	}

	@Test(expected = RestApiException.class)
	public void nonSocialKeyIsRejected() throws Exception {
		try {
			storeFacade.createBrand(STORE_CODE, brand(42L, "PAYMENT_MODULES", "x"));
		} finally {
			verify(merchantConfigurationService, never()).saveOrUpdate(any());
			verify(merchantConfigurationService, never()).delete(any());
		}
	}

	@Test(expected = RestApiException.class)
	public void blankNonSocialKeyIsNotDeleted() throws Exception {
		try {
			storeFacade.createBrand(STORE_CODE, brand(null, "SHIPPING_CONFIG", ""));
		} finally {
			verify(merchantConfigurationService, never()).delete(any());
		}
	}

	@Test(expected = RestApiException.class)
	public void existingNonSocialRowWithAllowedKeyIsNotOverwritten() throws Exception {
		MerchantConfiguration existing = socialRow(7L, Constants.KEY_PINTEREST_PAGE_URL, "v");
		existing.setMerchantConfigurationType(MerchantConfigurationType.CONFIG);
		when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_PINTEREST_PAGE_URL, store))
				.thenReturn(existing);
		try {
			storeFacade.createBrand(STORE_CODE, brand(null, Constants.KEY_PINTEREST_PAGE_URL, "new"));
		} finally {
			verify(merchantConfigurationService, never()).saveOrUpdate(any());
		}
	}

	@Test
	public void blankValueDeletesOwnStoreRowResolvedByKey() throws Exception {
		MerchantConfiguration existing = socialRow(7L, Constants.KEY_TWITTER_HANDLE, "@mugs");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_TWITTER_HANDLE, store))
				.thenReturn(existing);

		storeFacade.createBrand(STORE_CODE, brand(42L, Constants.KEY_TWITTER_HANDLE, ""));

		verify(merchantConfigurationService).delete(existing);
		verify(merchantConfigurationService, never()).saveOrUpdate(any());
	}

	private PersistableBrand brand(Long id, String key, String value) {
		MerchantConfigEntity entity = new MerchantConfigEntity();
		if (id != null) {
			entity.setId(id);
		}
		entity.setKey(key);
		entity.setValue(value);
		entity.setActive(true);
		PersistableBrand brand = new PersistableBrand();
		brand.setSocialNetworks(Collections.singletonList(entity));
		return brand;
	}

	private MerchantConfiguration socialRow(Long id, String key, String value) {
		MerchantConfiguration config = new MerchantConfiguration();
		config.setId(id);
		config.setMerchantStore(store);
		config.setKey(key);
		config.setValue(value);
		config.setMerchantConfigurationType(MerchantConfigurationType.SOCIAL);
		return config;
	}
}
