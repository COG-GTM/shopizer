package com.salesmanager.shop.store.api.v1.product;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.review.ProductReviewService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import springfox.documentation.annotations.ApiIgnore;

@Controller
@RequestMapping("/api/v1")
public class ProductReviewApi {

  @Inject private ProductCommonFacade productCommonFacade;

  @Inject private ProductService productService;

  @Inject private ProductReviewService productReviewService;

  @Inject private CustomerService customerService;

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductReviewApi.class);

  @RequestMapping(value = "/private/products/{id}/reviews", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.CREATED)
  @ResponseBody
  @ApiImplicitParams({
      @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
      @ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en")
  })
  public PersistableProductReview create(
      @PathVariable final Long id,
      @Valid @RequestBody PersistableProductReview review,
      @ApiIgnore MerchantStore merchantStore,
      @ApiIgnore Language language,
      HttpServletRequest request,
      HttpServletResponse response) {
    return saveNewReview(id, review, merchantStore, language, response);
  }

  @RequestMapping(value = "/auth/products/{id}/reviews", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.CREATED)
  @ResponseBody
  @ApiImplicitParams({
      @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
      @ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en")
  })
  public PersistableProductReview createAsCustomer(
      @PathVariable final Long id,
      @Valid @RequestBody PersistableProductReview review,
      @ApiIgnore MerchantStore merchantStore,
      @ApiIgnore Language language,
      HttpServletRequest request,
      HttpServletResponse response) {

    try {
      Customer customer = authenticatedCustomer(request, merchantStore);
      if (customer == null) {
        response.sendError(401, "Customer not authorized");
        return null;
      }
      review.setCustomerId(customer.getId());
    } catch (Exception e) {
      LOGGER.error("Error while saving product review", e);
      sendError(response, 503, "Error while saving product review");
      return null;
    }

    return saveNewReview(id, review, merchantStore, language, response);
  }

  private PersistableProductReview saveNewReview(
      Long id,
      PersistableProductReview review,
      MerchantStore merchantStore,
      Language language,
      HttpServletResponse response) {

    try {
      review.setId(null);
      review.setProductId(id);

      // rating already exist
      ProductReview prodReview =
          productReviewService.getByProductAndCustomer(
              review.getProductId(), review.getCustomerId());
      if (prodReview != null) {
        response.sendError(500, "A review already exist for this customer and product");
        return null;
      }

      // rating maximum 5
      if (review.getRating() > Constants.MAX_REVIEW_RATING_SCORE) {
        response.sendError(503, "Maximum rating score is " + Constants.MAX_REVIEW_RATING_SCORE);
        return null;
      }

      productCommonFacade.saveOrUpdateReview(review, merchantStore, language);

      return review;

    } catch (Exception e) {
      LOGGER.error("Error while saving product review", e);
      sendError(response, 503, "Error while saving product review" + e.getMessage());
      return null;
    }
  }

  @RequestMapping(value = "/product/{id}/reviews", method = RequestMethod.GET)
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  @ApiImplicitParams({
      @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
      @ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en")
  })
  public List<ReadableProductReview> getAll(
      @PathVariable final Long id,
      @ApiIgnore MerchantStore merchantStore,
      @ApiIgnore Language language,
      HttpServletResponse response) {

    try {
      // product exist
      Product product = productService.getById(id);

      if (product == null) {
        response.sendError(404, "Product id " + id + " does not exists");
        return null;
      }

      List<ReadableProductReview> reviews =
    		  productCommonFacade.getProductReviews(product, merchantStore, language);

      return reviews;

    } catch (Exception e) {
      LOGGER.error("Error while getting product reviews", e);
      try {
        response.sendError(503, "Error while getting product reviews" + e.getMessage());
      } catch (Exception ignore) {
      }

      return null;
    }
  }

  @RequestMapping(value = "/private/products/{id}/reviews/{reviewId}", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  @ApiImplicitParams({
      @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
      @ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en")
  })
  public PersistableProductReview update(
      @PathVariable final Long id,
      @PathVariable final Long reviewId,
      @Valid @RequestBody PersistableProductReview review,
      @ApiIgnore MerchantStore merchantStore,
      @ApiIgnore Language language,
      HttpServletRequest request,
      HttpServletResponse response) {

    try {
      ProductReview prodReview = findStoreReview(id, reviewId, merchantStore);
      if (prodReview == null) {
        sendReviewNotFound(response, reviewId);
        return null;
      }

      // the store administrator edits the review but cannot reassign its author
      review.setCustomerId(prodReview.getCustomer().getId());
      return updateReview(id, reviewId, review, merchantStore, language, response);

    } catch (Exception e) {
      LOGGER.error("Error while saving product review", e);
      sendError(response, 503, "Error while saving product review" + e.getMessage());
      return null;
    }
  }

  @RequestMapping(value = "/auth/products/{id}/reviews/{reviewId}", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  @ApiImplicitParams({
      @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
      @ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en")
  })
  public PersistableProductReview updateAsCustomer(
      @PathVariable final Long id,
      @PathVariable final Long reviewId,
      @Valid @RequestBody PersistableProductReview review,
      @ApiIgnore MerchantStore merchantStore,
      @ApiIgnore Language language,
      HttpServletRequest request,
      HttpServletResponse response) {

    try {
      Customer customer = authenticatedCustomer(request, merchantStore);
      if (customer == null) {
        response.sendError(401, "Customer not authorized");
        return null;
      }

      ProductReview prodReview = findCustomerReview(id, reviewId, merchantStore, customer);
      if (prodReview == null) {
        sendReviewNotFound(response, reviewId);
        return null;
      }

      review.setCustomerId(customer.getId());
      return updateReview(id, reviewId, review, merchantStore, language, response);

    } catch (Exception e) {
      LOGGER.error("Error while saving product review", e);
      sendError(response, 503, "Error while saving product review" + e.getMessage());
      return null;
    }
  }

  private PersistableProductReview updateReview(
      Long id,
      Long reviewId,
      PersistableProductReview review,
      MerchantStore merchantStore,
      Language language,
      HttpServletResponse response)
      throws Exception {

    // rating maximum 5
    if (review.getRating() > Constants.MAX_REVIEW_RATING_SCORE) {
      response.sendError(503, "Maximum rating score is " + Constants.MAX_REVIEW_RATING_SCORE);
      return null;
    }

    review.setId(reviewId);
    review.setProductId(id);

    productCommonFacade.saveOrUpdateReview(review, merchantStore, language);

    return review;
  }

  @RequestMapping(value = "/private/products/{id}/reviews/{reviewId}", method = RequestMethod.DELETE)
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  @ApiImplicitParams({
      @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
      @ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en")
  })
  public void delete(
      @PathVariable final Long id,
      @PathVariable final Long reviewId,
      @ApiIgnore MerchantStore merchantStore,
      @ApiIgnore Language language,
      HttpServletResponse response) {

    try {
      ProductReview prodReview = findStoreReview(id, reviewId, merchantStore);
      if (prodReview == null) {
        sendReviewNotFound(response, reviewId);
        return;
      }

      productCommonFacade.deleteReview(prodReview, merchantStore, language);

    } catch (Exception e) {
      LOGGER.error("Error while deleting product review", e);
      sendError(response, 503, "Error while deleting product review" + e.getMessage());
    }
  }

  @RequestMapping(value = "/auth/products/{id}/reviews/{reviewId}", method = RequestMethod.DELETE)
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  @ApiImplicitParams({
      @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
      @ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en")
  })
  public void deleteAsCustomer(
      @PathVariable final Long id,
      @PathVariable final Long reviewId,
      @ApiIgnore MerchantStore merchantStore,
      @ApiIgnore Language language,
      HttpServletRequest request,
      HttpServletResponse response) {

    try {
      Customer customer = authenticatedCustomer(request, merchantStore);
      if (customer == null) {
        response.sendError(401, "Customer not authorized");
        return;
      }

      ProductReview prodReview = findCustomerReview(id, reviewId, merchantStore, customer);
      if (prodReview == null) {
        sendReviewNotFound(response, reviewId);
        return;
      }

      productCommonFacade.deleteReview(prodReview, merchantStore, language);

    } catch (Exception e) {
      LOGGER.error("Error while deleting product review", e);
      sendError(response, 503, "Error while deleting product review" + e.getMessage());
    }
  }

  private Customer authenticatedCustomer(HttpServletRequest request, MerchantStore merchantStore) {
    Principal principal = request.getUserPrincipal();
    if (principal == null || principal.getName() == null) {
      return null;
    }
    return customerService.getByNick(principal.getName(), merchantStore.getId());
  }

  /** Review identified by the path that belongs to a product of the current store, or null. */
  private ProductReview findStoreReview(Long productId, Long reviewId, MerchantStore merchantStore) {
    ProductReview prodReview = productReviewService.getById(reviewId);
    if (prodReview == null || prodReview.getProduct() == null) {
      return null;
    }
    Product product = prodReview.getProduct();
    if (product.getId() == null || !product.getId().equals(productId)) {
      return null;
    }
    if (product.getMerchantStore() == null
        || !product.getMerchantStore().getId().equals(merchantStore.getId())) {
      return null;
    }
    return prodReview;
  }

  /** Review identified by the path that is owned by the given customer, or null. */
  private ProductReview findCustomerReview(
      Long productId, Long reviewId, MerchantStore merchantStore, Customer customer) {
    ProductReview prodReview = findStoreReview(productId, reviewId, merchantStore);
    if (prodReview == null
        || prodReview.getCustomer() == null
        || !prodReview.getCustomer().getId().equals(customer.getId())) {
      return null;
    }
    return prodReview;
  }

  private void sendReviewNotFound(HttpServletResponse response, Long reviewId) throws IOException {
    response.sendError(404, "Product review with id " + reviewId + " does not exist");
  }

  private void sendError(HttpServletResponse response, int status, String message) {
    try {
      response.sendError(status, message);
    } catch (Exception ignore) {
    }
  }
}
