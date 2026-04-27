package com.salesmanager.shop.store.api.v1.system;

import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.system.Configs;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.system.MerchantConfigurationFacade;
import com.salesmanager.shop.utils.LanguageUtils;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1")
public class PublicConfigsApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(PublicConfigsApi.class);

  @Inject private StoreFacade storeFacade;

  @Inject private LanguageUtils languageUtils;

  @Inject private MerchantConfigurationFacade configurationFacade;

  /**
   * Get public set of merchant configuration --- allow online purchase --- social links
   *
   * @return
   */
  @GetMapping("/config")
  @Operation(summary = "Get public configuration for a given merchant store")
  public Configs getConfig(@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {
    return configurationFacade.getMerchantConfig(merchantStore, language);
  }
}
