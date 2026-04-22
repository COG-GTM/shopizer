package com.salesmanager.shop.store.api.v1.customer;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.optin.PersistableCustomerOptin;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;

/**
 * Optin a customer to newsletter
 * @author carlsamson
 *
 */
@RestController
@RequestMapping(value = "/api/v1", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Optin Customer to newsletter")
public class CustomerNewsletterApi {

	@Inject
	private CustomerFacade customerFacade;

  /** Create new optin */
  @PostMapping("/newsletter")
  @Operation(summary = "Creates a newsletter optin")
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")
  })
  public void create(
      @Valid @RequestBody PersistableCustomerOptin optin,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language) {
		customerFacade.optinCustomer(optin, merchantStore);
	}

  @PutMapping("/newsletter/{email}")
  @Operation(summary = "Updates a customer")
  public void update(
      @PathVariable String email,
      @Valid @RequestBody PersistableCustomer customer,
      HttpServletRequest request,
      HttpServletResponse response) {
    throw new UnsupportedOperationException();
  }

  @DeleteMapping("/newsletter/{email}")
  @Operation(summary = "Deletes a customer")
  public ResponseEntity<Void> delete(
      @PathVariable String email, HttpServletRequest request, HttpServletResponse response) {
    throw new UnsupportedOperationException();
  }
}
