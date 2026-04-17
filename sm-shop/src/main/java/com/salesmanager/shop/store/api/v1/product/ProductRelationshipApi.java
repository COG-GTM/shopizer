package com.salesmanager.shop.store.api.v1.product;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.store.controller.product.facade.ProductFacade;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@Controller
@RequestMapping("/api/v1")
public class ProductRelationshipApi {

  @Inject private ProductFacade productFacade;

  @Inject private ProductService productService;

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductRelationshipApi.class);


  @RequestMapping(value = "/product/{id}/related", method = RequestMethod.GET)
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Get product related items. This is used for doing cross-sell and up-sell functionality on a product details page")
  @ResponseBody
  public List<ReadableProduct> getAll(
      @PathVariable final Long id,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language,
      HttpServletResponse response)
      throws Exception {

    try {
      // product exist
      Product product = productService.getById(id);

      if (product == null) {
        response.sendError(404, "Product id " + id + " does not exists");
        return null;
      }

      List<ReadableProduct> relatedItems =
          productFacade.relatedItems(merchantStore, product, language);

      return relatedItems;

    } catch (Exception e) {
      LOGGER.error("Error while getting product reviews", e);
      try {
        response.sendError(503, "Error while getting product reviews" + e.getMessage());
      } catch (Exception ignore) {
      }

      return null;
    }
  }


}
