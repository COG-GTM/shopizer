package com.salesmanager.shop.store.api.v2.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProductPrice;
import com.salesmanager.shop.model.catalog.product.attribute.ReadableProductVariant;
import com.salesmanager.shop.model.catalog.product.attribute.ReadableProductVariantValue;
import com.salesmanager.shop.model.catalog.product.attribute.ReadableSelectedProductVariant;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.model.catalog.product.variation.ReadableProductVariation;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.model.entity.ReadableEntityList;
import com.salesmanager.shop.populator.catalog.ReadableFinalPricePopulator;
import com.salesmanager.shop.store.controller.category.facade.CategoryFacade;
import com.salesmanager.shop.store.controller.product.facade.ProductVariationFacade;
import com.salesmanager.shop.utils.ImageFilePath;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
/**
 * API to manage product variant
 * 
 * The flow is the following
 * 
 * - create a product definition
 * - create a product variant
 *
 * @author Carl Samson
 */
@Controller
@RequestMapping("/api/v2")
@Tag(name = "API")
public class ProductVariationApi {

  @Inject private PricingService pricingService;

  @Inject private ProductService productService;
  
  @Inject private CategoryFacade categoryFacade;
  
  @Inject private ProductVariationFacade productVariationFacade;
	

  @Inject
  @Qualifier("img")
  private ImageFilePath imageUtils;

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductVariationApi.class);

  /**
   * Calculates the price based on selected options if any
   * @param id
   * @param options
   * @param merchantStore
   * @param language
   * @param response
   * @return
   * @throws Exception
   */
  @RequestMapping(value = "/product/{id}/variation", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Get product price variation based on selected product")
  @ResponseBody
    public ReadableProductPrice calculateVariant(
      @PathVariable final Long id,
      @RequestBody ReadableSelectedProductVariant options,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language,
      HttpServletResponse response)
      throws Exception {

    Product product = productService.getById(id);

    if (product == null) {
      response.sendError(404, "Product not fount for id " + id);
      return null;
    }

    List<ReadableProductVariantValue> ids = options.getOptions();

    if (CollectionUtils.isEmpty(ids)) {
      return null;
    }
    
    List<ReadableProductVariantValue> variants = options.getOptions();
    List<ProductAttribute> attributes = new ArrayList<ProductAttribute>();
    
    Set<ProductAttribute> productAttributes = product.getAttributes();
    for(ProductAttribute attribute : productAttributes) {
      Long option = attribute.getProductOption().getId();
      Long optionValue = attribute.getProductOptionValue().getId();
      for(ReadableProductVariantValue v : variants) {
        if(v.getOption().longValue() == option.longValue()
            && v.getValue().longValue() == optionValue.longValue()) {
          attributes.add(attribute);
        }
      }
      
    }

    FinalPrice price = pricingService.calculateProductPrice(product, attributes);
    ReadableProductPrice readablePrice = new ReadableProductPrice();
    ReadableFinalPricePopulator populator = new ReadableFinalPricePopulator();
    populator.setPricingService(pricingService);
    populator.populate(price, readablePrice, merchantStore, language);
    return readablePrice;
  }

  
  @RequestMapping(value = "/category/{id}/variations", method = RequestMethod.GET)
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Get all variation for all items in a given category")
  @ResponseBody
    public List<ReadableProductVariant> categoryVariantList(
      @PathVariable final Long id, //category id
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language,
      HttpServletResponse response)
      throws Exception {
    
    return categoryFacade.categoryProductVariants(id, merchantStore, language);
    
  }

	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = { "/private/product/variation" }, method = RequestMethod.POST)
	@Operation(summary = "Creates a new product variant")
	public @ResponseBody Entity create(
			@Valid @RequestBody PersistableProductVariation variation, 
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		Long variantId = productVariationFacade.create(variation, merchantStore, language);
		return new Entity(variantId);

	}

	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/product/variation/unique" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Check if option set code already exists")
	public ResponseEntity<EntityExists> exists(
			@RequestParam(value = "code") String code,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {

		boolean isOptionExist = productVariationFacade.exists(code, merchantStore);
		return new ResponseEntity<EntityExists>(new EntityExists(isOptionExist), HttpStatus.OK);
	}

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/variation/{variationId}" }, method = RequestMethod.GET)
	@ResponseBody
	public ReadableProductVariation get(
			@PathVariable Long variationId, 
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		return productVariationFacade.get(variationId, merchantStore, language);

	}

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/variation/{variationId}" }, method = RequestMethod.PUT)
	public void update(
			@Valid @RequestBody PersistableProductVariation variation, 
			@PathVariable Long variationId,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {
		
		variation.setId(variationId);
		productVariationFacade.update(variationId, variation, merchantStore, language);

	}

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/variation/{variationId}" }, method = RequestMethod.DELETE)
	public void delete(
			@PathVariable Long variationId,
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		productVariationFacade.delete(variationId, merchantStore);

	}
	

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/variations" }, method = RequestMethod.GET)
	public @ResponseBody ReadableEntityList<ReadableProductVariation> list(
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language,
			@RequestParam(value = "page", required = false, defaultValue="0") Integer page,
		    @RequestParam(value = "count", required = false, defaultValue="10") Integer count) {

		return productVariationFacade.list(merchantStore, language, page, count);

		
	}
  
}
