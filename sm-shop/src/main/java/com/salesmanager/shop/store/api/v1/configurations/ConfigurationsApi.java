package com.salesmanager.shop.store.api.v1.configurations;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.configuration.ReadableConfiguration;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/api/v1")
@Tag(name = "Configurations management", description = "Configurations management")
public class ConfigurationsApi {
	
	
	  /** Configurations of modules */
	  @PostMapping("/private/configurations/payment")
	  @Operation(summary = "Manages payment configurations")
	  public Void create(
	      @Parameter(hidden = true) MerchantStore merchantStore,
	      @Parameter(hidden = true) Language language) {
	      //return customerFacade.create(customer, merchantStore, language);
		  return null;

	  }
	  
	  
	  /** Configurations of payment modules */
	  @GetMapping("/private/configurations/payment")
	  @Operation(summary = "List payment configurations summary")
	  public List<ReadableConfiguration> listPaymentConfigurations(
	      @Parameter(hidden = true) MerchantStore merchantStore,
	      @Parameter(hidden = true) Language language) {
	      //return customerFacade.create(customer, merchantStore, language);
		  return null;

	  }
	  
	  
	  
	  
	  /** Configurations of shipping modules */
	  @GetMapping("/private/configurations/shipping")
	  @Operation(summary = "List shipping configurations summary")
	  public List<ReadableConfiguration> listShippingConfigurations(
	      @Parameter(hidden = true) MerchantStore merchantStore,
	      @Parameter(hidden = true) Language language) {
	      //return customerFacade.create(customer, merchantStore, language);
		  return null;

	  }
	
	
	
	
	

}
