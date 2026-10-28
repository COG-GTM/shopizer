package com.salesmanager.test.shop.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.store.MerchantConfigEntity;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.store.api.exception.RestApiException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class StoreFacadeImplBrandValidationTest {

	private static final String STORE_CODE = "OWNERSHIP";

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
	public void nullAndEmptyNetworksAreNoOps() {
		PersistableBrand nullNetworks = new PersistableBrand();
		nullNetworks.setSocialNetworks(null);
		storeFacade.createBrand(STORE_CODE, nullNetworks);
		storeFacade.createBrand(STORE_CODE, new PersistableBrand());

		verifyNoInteractions(merchantConfigurationService);
	}

	@Test
	public void nullNetworkEntryIsRejectedBeforeLookup() throws Exception {
		PersistableBrand brand = new PersistableBrand();
		brand.setSocialNetworks(Collections.singletonList(null));

		try {
			storeFacade.createBrand(STORE_CODE, brand);
		} catch (RestApiException expected) {
			verify(merchantConfigurationService, never()).getMerchantConfiguration(any(), any());
			return;
		}
		throw new AssertionError("Expected RestApiException");
	}

	@Test
	public void unsupportedKeyIsRejectedBeforeAnyLookup() throws Exception {
		PersistableBrand brand = new PersistableBrand();
		brand.setSocialNetworks(Arrays.asList(network("unsupported", "x"), network(Constants.KEY_FACEBOOK_PAGE_URL,
				"https://facebook.com/valid")));

		try {
			storeFacade.createBrand(STORE_CODE, brand);
		} catch (RestApiException expected) {
			verify(merchantConfigurationService, never()).getMerchantConfiguration(any(), any());
			return;
		}
		throw new AssertionError("Expected RestApiException");
	}

	@Test
	public void socialKeyIsCaseSensitive() throws Exception {
		PersistableBrand brand = brand(network("Facebook_Page_Url", "x"));

		try {
			storeFacade.createBrand(STORE_CODE, brand);
		} catch (RestApiException expected) {
			verify(merchantConfigurationService, never()).getMerchantConfiguration(any(), any());
			return;
		}
		throw new AssertionError("Expected RestApiException");
	}

	@Test
	public void savesEveryAllowedKeyAsSocialConfiguration() throws Exception {
		List<String> keys = Arrays.asList(Constants.KEY_FACEBOOK_PAGE_URL, Constants.KEY_TWITTER_HANDLE,
				Constants.KEY_INSTAGRAM_URL, Constants.KEY_PINTEREST_PAGE_URL);
		PersistableBrand brand = new PersistableBrand();
		brand.setSocialNetworks(Arrays.asList(network(keys.get(0), "facebook"), network(keys.get(1), "twitter"),
				network(keys.get(2), "instagram"), network(keys.get(3), "pinterest")));

		storeFacade.createBrand(STORE_CODE, brand);

		ArgumentCaptor<MerchantConfiguration> saved = ArgumentCaptor.forClass(MerchantConfiguration.class);
		verify(merchantConfigurationService, times(4)).saveOrUpdate(saved.capture());
		for (int i = 0; i < keys.size(); i++) {
			assertEquals(keys.get(i), saved.getAllValues().get(i).getKey());
			assertSame(store, saved.getAllValues().get(i).getMerchantStore());
			assertEquals(MerchantConfigurationType.SOCIAL,
					saved.getAllValues().get(i).getMerchantConfigurationType());
		}
	}

	@Test
	public void updateKeepsExistingIdAndInactiveState() throws Exception {
		MerchantConfiguration existing = socialRow(7L, Constants.KEY_INSTAGRAM_URL);
		when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_INSTAGRAM_URL, store))
				.thenReturn(existing);
		MerchantConfigEntity requested = network(Constants.KEY_INSTAGRAM_URL, "updated");
		requested.setId(999L);
		requested.setActive(false);

		storeFacade.createBrand(STORE_CODE, brand(requested));

		ArgumentCaptor<MerchantConfiguration> saved = ArgumentCaptor.forClass(MerchantConfiguration.class);
		verify(merchantConfigurationService).saveOrUpdate(saved.capture());
		assertEquals(Long.valueOf(7L), saved.getValue().getId());
		assertFalse(saved.getValue().getActive());
	}

	@Test
	public void serviceFailureIsWrappedAsServiceRuntimeException() throws Exception {
		when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_FACEBOOK_PAGE_URL, store))
				.thenReturn(null);
		org.mockito.Mockito.doThrow(new ServiceException("save failed")).when(merchantConfigurationService)
				.saveOrUpdate(any(MerchantConfiguration.class));

		try {
			storeFacade.createBrand(STORE_CODE, brand(network(Constants.KEY_FACEBOOK_PAGE_URL, "facebook")));
		} catch (ServiceRuntimeException expected) {
			return;
		}
		throw new AssertionError("Expected ServiceRuntimeException");
	}

	private PersistableBrand brand(MerchantConfigEntity... networks) {
		PersistableBrand brand = new PersistableBrand();
		brand.setSocialNetworks(Arrays.asList(networks));
		return brand;
	}

	private MerchantConfigEntity network(String key, String value) {
		MerchantConfigEntity network = new MerchantConfigEntity();
		network.setKey(key);
		network.setValue(value);
		network.setActive(true);
		return network;
	}

	private MerchantConfiguration socialRow(Long id, String key) {
		MerchantConfiguration config = new MerchantConfiguration();
		config.setId(id);
		config.setMerchantStore(store);
		config.setKey(key);
		config.setValue("old");
		config.setMerchantConfigurationType(MerchantConfigurationType.SOCIAL);
		return config;
	}
}
