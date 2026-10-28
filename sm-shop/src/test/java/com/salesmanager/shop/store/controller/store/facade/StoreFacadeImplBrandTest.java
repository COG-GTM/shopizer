package com.salesmanager.shop.store.controller.store.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;

@RunWith(MockitoJUnitRunner.class)
public class StoreFacadeImplBrandTest {

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
    store.setId(2);
    store.setCode("MALLORY");
    when(merchantStoreService.getByCode("MALLORY")).thenReturn(store);
  }

  @Test
  public void rejectsReservedKeys() throws Exception {
    for (String key : new String[] {"EMAIL_CONFIG", "CONFIG", "SHIPPING_CONFIG", "PAYMENT_MODULES", "paypal-express-checkout", null}) {
      try {
        storeFacade.createBrand("MALLORY", brand(key, "{\"host\":\"smtp.attacker.test\",\"port\":\"2525\"}", null));
        fail("key should be rejected: " + key);
      } catch (OperationNotAllowedException expected) {
        // expected
      }
    }
    verify(merchantConfigurationService, never()).saveOrUpdate(any());
    verify(merchantConfigurationService, never()).delete(any());
  }

  @Test
  public void rejectsWholeBatchWhenAnyKeyIsReserved() throws Exception {
    PersistableBrand brand = brand(Constants.KEY_FACEBOOK_PAGE_URL, "https://facebook.com/mugs", null);
    brand.getSocialNetworks().add(entity("EMAIL_CONFIG", "{\"host\":\"smtp.attacker.test\"}", null));
    try {
      storeFacade.createBrand("MALLORY", brand);
      fail("expected rejection");
    } catch (OperationNotAllowedException expected) {
      // expected
    }
    verify(merchantConfigurationService, never()).saveOrUpdate(any());
  }

  @Test
  public void savesAllowedKeyAsSocialAndIgnoresClientId() throws Exception {
    storeFacade.createBrand("MALLORY", brand(Constants.KEY_FACEBOOK_PAGE_URL, "https://facebook.com/mugs", 1L));

    ArgumentCaptor<MerchantConfiguration> captor = ArgumentCaptor.forClass(MerchantConfiguration.class);
    verify(merchantConfigurationService).saveOrUpdate(captor.capture());
    MerchantConfiguration saved = captor.getValue();
    assertNull(saved.getId());
    assertEquals(Constants.KEY_FACEBOOK_PAGE_URL, saved.getKey());
    assertEquals(MerchantConfigurationType.SOCIAL, saved.getMerchantConfigurationType());
    assertSame(store, saved.getMerchantStore());
    assertEquals("https://facebook.com/mugs", saved.getValue());
  }

  @Test
  public void updatesExistingSocialRowOfTheSameStore() throws Exception {
    MerchantConfiguration existing = row(Constants.KEY_INSTAGRAM_URL, MerchantConfigurationType.SOCIAL);
    existing.setId(42L);
    when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_INSTAGRAM_URL, store)).thenReturn(existing);

    storeFacade.createBrand("MALLORY", brand(Constants.KEY_INSTAGRAM_URL, "https://instagram.com/mugs", 7L));

    ArgumentCaptor<MerchantConfiguration> captor = ArgumentCaptor.forClass(MerchantConfiguration.class);
    verify(merchantConfigurationService).saveOrUpdate(captor.capture());
    assertSame(existing, captor.getValue());
    assertEquals(Long.valueOf(42L), captor.getValue().getId());
    assertEquals("https://instagram.com/mugs", captor.getValue().getValue());
  }

  @Test
  public void refusesToOverwriteNonSocialRowWithAllowedKey() throws Exception {
    when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_GOOGLE_API_KEY, store))
        .thenReturn(row(Constants.KEY_GOOGLE_API_KEY, MerchantConfigurationType.INTEGRATION));
    try {
      storeFacade.createBrand("MALLORY", brand(Constants.KEY_GOOGLE_API_KEY, "", null));
      fail("expected rejection");
    } catch (OperationNotAllowedException expected) {
      // expected
    }
    verify(merchantConfigurationService, never()).delete(any());
    verify(merchantConfigurationService, never()).saveOrUpdate(any());
  }

  @Test
  public void reservedExistingRowLaterInBatchPreventsEarlierWrites() throws Exception {
    when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_GOOGLE_API_KEY, store))
        .thenReturn(row(Constants.KEY_GOOGLE_API_KEY, MerchantConfigurationType.INTEGRATION));
    PersistableBrand brand = brand(Constants.KEY_FACEBOOK_PAGE_URL, "https://facebook.com/mugs", null);
    brand.getSocialNetworks().add(entity(Constants.KEY_GOOGLE_API_KEY, "abc", null));
    try {
      storeFacade.createBrand("MALLORY", brand);
      fail("expected rejection");
    } catch (OperationNotAllowedException expected) {
      // expected
    }
    verify(merchantConfigurationService, never()).saveOrUpdate(any());
    verify(merchantConfigurationService, never()).delete(any());
  }

  @Test
  public void blankValueDeletesExistingSocialRow() throws Exception {
    MerchantConfiguration existing = row(Constants.KEY_TWITTER_HANDLE, MerchantConfigurationType.SOCIAL);
    when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_TWITTER_HANDLE, store)).thenReturn(existing);

    storeFacade.createBrand("MALLORY", brand(Constants.KEY_TWITTER_HANDLE, "", null));

    verify(merchantConfigurationService).delete(existing);
    verify(merchantConfigurationService, never()).saveOrUpdate(any());
  }

  @Test
  public void allowlistMatchesStorefrontKeys() {
    assertTrue(StoreFacadeImpl.BRAND_CONFIGURATION_KEYS.contains(Constants.KEY_FACEBOOK_PAGE_URL));
    assertTrue(StoreFacadeImpl.BRAND_CONFIGURATION_KEYS.contains(Constants.KEY_PINTEREST_PAGE_URL));
    assertTrue(StoreFacadeImpl.BRAND_CONFIGURATION_KEYS.contains(Constants.KEY_GOOGLE_ANALYTICS_URL));
    assertTrue(StoreFacadeImpl.BRAND_CONFIGURATION_KEYS.contains(Constants.KEY_INSTAGRAM_URL));
    assertEquals(6, StoreFacadeImpl.BRAND_CONFIGURATION_KEYS.size());
  }

  private MerchantConfiguration row(String key, MerchantConfigurationType type) {
    MerchantConfiguration configuration = new MerchantConfiguration();
    configuration.setKey(key);
    configuration.setMerchantStore(store);
    configuration.setMerchantConfigurationType(type);
    configuration.setValue("x");
    return configuration;
  }

  private static PersistableBrand brand(String key, String value, Long id) {
    PersistableBrand brand = new PersistableBrand();
    brand.getSocialNetworks().add(entity(key, value, id));
    return brand;
  }

  private static MerchantConfigEntity entity(String key, String value, Long id) {
    MerchantConfigEntity entity = new MerchantConfigEntity();
    if (id != null) {
      entity.setId(id);
    }
    entity.setKey(key);
    entity.setValue(value);
    entity.setActive(true);
    return entity;
  }
}
