package com.salesmanager.shop.store.api.v1.customer;

import java.util.List;

import javax.inject.Inject;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.customer.review.CustomerReviewService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomerReview;
import com.salesmanager.shop.model.customer.ReadableCustomerReview;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.utils.LanguageUtils;

import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v1")
public class CustomerReviewApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(CustomerReviewApi.class);

	@Inject
	private CustomerFacade customerFacade;
	
	@Inject
	private StoreFacade storeFacade;
	
	@Inject
	private LanguageUtils languageUtils;

	@Inject
	private CustomerService customerService;
	
	@Inject
	private CustomerReviewService customerReviewService;

  /**
   * Reviews made for a given customer
   *
   * @param id
   * @param review
   * @return
   * @throws Exception
   */
  @PostMapping("/private/customers/{id}/reviews")
  @ResponseStatus(HttpStatus.CREATED)
  public PersistableCustomerReview create(
      @PathVariable final Long id,
      @Valid @RequestBody PersistableCustomerReview review,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language) {
    return customerFacade.createCustomerReview(id, review, merchantStore, language);
  }

  @GetMapping("/customers/{id}/reviews")
  public List<ReadableCustomerReview> getAll(
      @PathVariable final Long id, @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {
    return customerFacade.getAllCustomerReviewsByReviewed(id, merchantStore, language);
  }

	@PutMapping("/private/customers/{id}/reviews/{reviewid}")
  public PersistableCustomerReview update(
      @PathVariable final Long id,
      @PathVariable final Long reviewId,
      @Valid @RequestBody PersistableCustomerReview review,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language) {
      return customerFacade.updateCustomerReview(id, reviewId, review, merchantStore, language);
	}

  @DeleteMapping("/private/customers/{id}/reviews/{reviewId}")
  public void delete(
      @PathVariable final Long id,
      @PathVariable final Long reviewId,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language) {
    customerFacade.deleteCustomerReview(id, reviewId, merchantStore, language);
  }
}
