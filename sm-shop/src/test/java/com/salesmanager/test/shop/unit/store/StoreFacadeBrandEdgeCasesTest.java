package com.salesmanager.test.shop.unit.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

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
public class StoreFacadeBrandEdgeCasesTest {

	@Mock
	private MerchantStoreService merchantStoreService;

	@Mock
	private MerchantConfigurationService merchantConfigurationService;

	@InjectMocks
	private StoreFacadeImpl storeFacade;

	private MerchantStore store;

	@BeforeEach
	public void setUp() throws Exception {
		store = new MerchantStore(1, "ALICE", "Alice");
		when(merchantStoreService.getByCode("ALICE")).thenReturn(store);
	}

	private MerchantConfigEntity entity(String key, String value) {
		MerchantConfigEntity entity = new MerchantConfigEntity();
		entity.setKey(key);
		entity.setValue(value);
		entity.setActive(true);
		return entity;
	}

	private PersistableBrand brand(MerchantConfigEntity... entities) {
		PersistableBrand brand = new PersistableBrand();
		brand.getSocialNetworks().addAll(Arrays.asList(entities));
		return brand;
	}

	private MerchantConfiguration existing(String key, MerchantConfigurationType type) {
		MerchantConfiguration configuration = new MerchantConfiguration();
		configuration.setId(11L);
		configuration.setKey(key);
		configuration.setMerchantStore(store);
		configuration.setMerchantConfigurationType(type);
		configuration.setValue("old");
		return configuration;
	}

	@Test
	public void acceptsEverySocialNetworkKey() throws Exception {
		storeFacade.createBrand("ALICE", brand(
				entity(Constants.KEY_FACEBOOK_PAGE_URL, "https://facebook.com/alice"),
				entity(Constants.KEY_PINTEREST_PAGE_URL, "https://pinterest.com/alice"),
				entity(Constants.KEY_INSTAGRAM_URL, "https://instagram.com/alice"),
				entity(Constants.KEY_TWITTER_HANDLE, "@alice"),
				entity(Constants.KEY_GOOGLE_ANALYTICS_URL, "UA-1")));

		ArgumentCaptor<MerchantConfiguration> captor = ArgumentCaptor.forClass(MerchantConfiguration.class);
		verify(merchantConfigurationService, times(5)).saveOrUpdate(captor.capture());
		List<MerchantConfiguration> saved = captor.getAllValues();
		for (MerchantConfiguration configuration : saved) {
			assertEquals(MerchantConfigurationType.SOCIAL, configuration.getMerchantConfigurationType());
			assertEquals(store, configuration.getMerchantStore());
		}
	}

	@Test
	public void rejectsWholeRequestBeforeAnyWriteWhenOneKeyIsNotSocial() throws Exception {
		assertThrows(OperationNotAllowedException.class, () -> storeFacade.createBrand("ALICE", brand(
				entity(Constants.KEY_INSTAGRAM_URL, "https://instagram.com/alice"),
				entity("SHIPPING_CONFIG", "x"))));

		verify(merchantConfigurationService, never()).getMerchantConfiguration(anyString(), any());
		verify(merchantConfigurationService, never()).saveOrUpdate(any());
		verify(merchantConfigurationService, never()).delete(any());
	}

	@Test
	public void rejectsNullEntryAndNullKey() throws Exception {
		assertThrows(OperationNotAllowedException.class,
				() -> storeFacade.createBrand("ALICE", brand((MerchantConfigEntity) null)));
		assertThrows(OperationNotAllowedException.class,
				() -> storeFacade.createBrand("ALICE", brand(entity(null, "x"))));

		verify(merchantConfigurationService, never()).saveOrUpdate(any());
	}

	@Test
	public void keyMatchingIsCaseSensitive() throws Exception {
		assertThrows(OperationNotAllowedException.class,
				() -> storeFacade.createBrand("ALICE", brand(entity("INSTAGRAM", "https://instagram.com/alice"))));

		verify(merchantConfigurationService, never()).saveOrUpdate(any());
	}

	@Test
	public void nullSocialNetworksIsANoOp() throws Exception {
		PersistableBrand brand = new PersistableBrand();
		brand.setSocialNetworks(null);

		storeFacade.createBrand("ALICE", brand);

		verify(merchantConfigurationService, never()).saveOrUpdate(any());
		verify(merchantConfigurationService, never()).delete(any());
	}

	@Test
	public void blankValueDeletesExistingSocialRow() throws Exception {
		MerchantConfiguration existing = existing(Constants.KEY_TWITTER_HANDLE, MerchantConfigurationType.SOCIAL);
		when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_TWITTER_HANDLE, store)).thenReturn(existing);

		storeFacade.createBrand("ALICE", brand(entity(Constants.KEY_TWITTER_HANDLE, "")));

		verify(merchantConfigurationService).delete(existing);
		verify(merchantConfigurationService, never()).saveOrUpdate(any());
	}

	@Test
	public void blankValueWithoutExistingRowWritesNothing() throws Exception {
		storeFacade.createBrand("ALICE", brand(entity(Constants.KEY_TWITTER_HANDLE, null)));

		verify(merchantConfigurationService, never()).delete(any());
		verify(merchantConfigurationService, never()).saveOrUpdate(any());
	}

	@Test
	public void blankValueDoesNotDeleteNonSocialRow() throws Exception {
		MerchantConfiguration existing = existing(Constants.KEY_FACEBOOK_PAGE_URL, MerchantConfigurationType.INTEGRATION);
		when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_FACEBOOK_PAGE_URL, store)).thenReturn(existing);

		assertThrows(OperationNotAllowedException.class,
				() -> storeFacade.createBrand("ALICE", brand(entity(Constants.KEY_FACEBOOK_PAGE_URL, ""))));

		verify(merchantConfigurationService, never()).delete(any());
		verify(merchantConfigurationService, never()).saveOrUpdate(any());
	}
}
