package com.salesmanager.shop.store.controller.store.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
public class StoreFacadeImplBrandEdgeCasesTest {

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
    store.setCode("MALLORY");
    when(merchantStoreService.getByCode("MALLORY")).thenReturn(store);
  }

  @Test
  public void rejectsNullEntryBeforeConfigurationServiceCalls() {
    PersistableBrand brand = new PersistableBrand();
    brand.getSocialNetworks().add(null);

    OperationNotAllowedException exception = assertThrows(
        OperationNotAllowedException.class, () -> storeFacade.createBrand("MALLORY", brand));

    assertEquals("304", exception.getErrorCode());
    verifyNoInteractions(merchantConfigurationService);
  }

  @Test
  public void emptyListMakesNoConfigurationServiceCalls() {
    storeFacade.createBrand("MALLORY", new PersistableBrand());

    verifyNoInteractions(merchantConfigurationService);
  }

  @Test
  public void nullSocialNetworksMakesNoConfigurationServiceCalls() {
    PersistableBrand brand = new PersistableBrand();
    brand.setSocialNetworks(null);

    storeFacade.createBrand("MALLORY", brand);

    verifyNoInteractions(merchantConfigurationService);
  }

  @Test
  public void rejectsCaseAndWhitespaceVariantsOfAllowedKey() {
    for (String key : new String[] {"FACEBOOK_PAGE_URL", " facebook_page_url"}) {
      PersistableBrand brand = brand(key, "https://example.test");

      OperationNotAllowedException exception = assertThrows(
          OperationNotAllowedException.class, () -> storeFacade.createBrand("MALLORY", brand));

      assertEquals("304", exception.getErrorCode());
    }
    verifyNoInteractions(merchantConfigurationService);
  }

  @Test
  public void blankValueDeletesExistingSocialRow() throws Exception {
    MerchantConfiguration existing = row();
    when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_FACEBOOK_PAGE_URL, store))
        .thenReturn(existing);

    storeFacade.createBrand("MALLORY", brand(Constants.KEY_FACEBOOK_PAGE_URL, "  "));

    verify(merchantConfigurationService).delete(existing);
    verify(merchantConfigurationService, never()).saveOrUpdate(any());
  }

  @Test
  public void blankValueWithNoExistingRowDoesNotWriteOrDelete() throws Exception {
    when(merchantConfigurationService.getMerchantConfiguration(Constants.KEY_FACEBOOK_PAGE_URL, store))
        .thenReturn(null);

    storeFacade.createBrand("MALLORY", brand(Constants.KEY_FACEBOOK_PAGE_URL, ""));

    verify(merchantConfigurationService, never()).delete(any());
    verify(merchantConfigurationService, never()).saveOrUpdate(any());
  }

  private MerchantConfiguration row() {
    MerchantConfiguration configuration = new MerchantConfiguration();
    configuration.setKey(Constants.KEY_FACEBOOK_PAGE_URL);
    configuration.setMerchantStore(store);
    configuration.setMerchantConfigurationType(MerchantConfigurationType.SOCIAL);
    configuration.setValue("https://facebook.com/old");
    return configuration;
  }

  private static PersistableBrand brand(String key, String value) {
    PersistableBrand brand = new PersistableBrand();
    MerchantConfigEntity entity = new MerchantConfigEntity();
    entity.setKey(key);
    entity.setValue(value);
    brand.setSocialNetworks(new ArrayList<>());
    brand.getSocialNetworks().add(entity);
    return brand;
  }
}
