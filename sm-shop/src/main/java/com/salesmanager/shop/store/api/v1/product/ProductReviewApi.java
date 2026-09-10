package com.salesmanager.shop.store.api.v1.product;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.review.ProductReviewService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;

@RestController
@RequestMapping("/api/v1")
public class ProductReviewApi {

  @Inject private ProductCommonFacade productCommonFacade;

  @Inject private ProductService productService;

  @Inject private ProductReviewService productReviewService;

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductReviewApi.class);

  @RequestMapping(
      value = {
        "/private/products/{id}/reviews",
        "/auth/products/{id}/reviews",
        "/auth/products/{id}/reviews",
        "/auth/products/{id}/reviews"
      },
      method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.CREATED)
  @ResponseBody
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")
  })
  public PersistableProductReview create(
      @PathVariable final Long id,
      @Valid @RequestBody PersistableProductReview review,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language,
      HttpServletRequest request,
      HttpServletResponse response) {

    try {
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

      review.setProductId(id);

      productCommonFacade.saveOrUpdateReview(review, merchantStore, language);

      return review;

    } catch (Exception e) {
      LOGGER.error("Error while saving product review", e);
      try {
        response.sendError(503, "Error while saving product review" + e.getMessage());
      } catch (Exception ignore) {
      }

      return null;
    }
  }

  @RequestMapping(value = "/product/{id}/reviews", method = RequestMethod.GET)
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")
  })
  public List<ReadableProductReview> getAll(
      @PathVariable final Long id,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language,
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

  @RequestMapping(
      value = {
        "/private/products/{id}/reviews/{reviewid}",
        "/auth/products/{id}/reviews/{reviewid}"
      },
      method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")
  })
  public PersistableProductReview update(
      @PathVariable final Long id,
      @PathVariable final Long reviewId,
      @Valid @RequestBody PersistableProductReview review,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language,
      HttpServletRequest request,
      HttpServletResponse response) {

    try {
      ProductReview prodReview = productReviewService.getById(reviewId);
      if (prodReview == null) {
        response.sendError(404, "Product review with id " + reviewId + " does not exist");
        return null;
      }

      if (prodReview.getCustomer().getId().longValue() != review.getCustomerId().longValue()) {
        response.sendError(404, "Product review with id " + reviewId + " does not exist");
        return null;
      }

      // rating maximum 5
      if (review.getRating() > Constants.MAX_REVIEW_RATING_SCORE) {
        response.sendError(503, "Maximum rating score is " + Constants.MAX_REVIEW_RATING_SCORE);
        return null;
      }

      review.setProductId(id);

      productCommonFacade.saveOrUpdateReview(review, merchantStore, language);

      return review;

    } catch (Exception e) {
      LOGGER.error("Error while saving product review", e);
      try {
        response.sendError(503, "Error while saving product review" + e.getMessage());
      } catch (Exception ignore) {
      }

      return null;
    }
  }

  @RequestMapping(
      value = {
        "/private/products/{id}/reviews/{reviewid}",
        "/auth/products/{id}/reviews/{reviewid}"
      },
      method = RequestMethod.DELETE)
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")
  })
  public void delete(
      @PathVariable final Long id,
      @PathVariable final Long reviewId,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language,
      HttpServletResponse response) {

    try {
      ProductReview prodReview = productReviewService.getById(reviewId);
      if (prodReview == null) {
        response.sendError(404, "Product review with id " + reviewId + " does not exist");
        return;
      }

      if (prodReview.getProduct().getId().longValue() != id.longValue()) {
        response.sendError(404, "Product review with id " + reviewId + " does not exist");
        return;
      }

      productCommonFacade.deleteReview(prodReview, merchantStore, language);

    } catch (Exception e) {
      LOGGER.error("Error while deleting product review", e);
      try {
        response.sendError(503, "Error while deleting product review" + e.getMessage());
      } catch (Exception ignore) {
      }

      return;
    }
  }
}
