package com.salesmanager.shop.store.api.v1.product;

import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.ReadableProductPrice;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.store.controller.product.facade.ProductPriceFacade;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameters;

/**
 * Use inventory
 * @author carlsamson
 *
 */

@Controller
@RequestMapping("/api/v1")
@Tag(name = "Product price api")
public class ProductPriceApi {

	@Autowired
	private ProductPriceFacade productPriceFacade;;

	private static final Logger LOGGER = LoggerFactory.getLogger(ProductApi.class);

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/{sku}/inventory/{inventoryId}/price"},
			method = RequestMethod.POST)
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	public @ResponseBody Entity save(
			@PathVariable String sku,
			@PathVariable Long inventoryId,
			@Valid @RequestBody PersistableProductPrice price,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {
		
		price.setSku(sku);
		price.setProductAvailabilityId(inventoryId);
		
		Long id = productPriceFacade.save(price, merchantStore);
		return new Entity(id);

		
	}
	
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = { "/private/product/{sku}/price"},
			method = RequestMethod.POST)
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	public @ResponseBody Entity save(
			@PathVariable String sku,
			@Valid @RequestBody PersistableProductPrice price,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {
		
		price.setSku(sku);
		
		Long id = productPriceFacade.save(price, merchantStore);
		return new Entity(id);

		
	}
	
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/{sku}/inventory/{inventoryId}/price/{priceId}"},
			method = RequestMethod.PUT)
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	public void edit(
			@PathVariable String sku,
			@PathVariable Long inventoryId,
			@PathVariable Long priceId,
			@Valid @RequestBody PersistableProductPrice price,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {
		
		
		price.setSku(sku);
		price.setProductAvailabilityId(inventoryId);
		price.setId(priceId);
		productPriceFacade.save(price, merchantStore);

		
	}
	
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/{sku}/price/{priceId}"},
			method = RequestMethod.GET)
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	public ReadableProductPrice get(
			@PathVariable String sku,
			@PathVariable Long priceId,
			@Valid @RequestBody PersistableProductPrice price,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {
		
		
		price.setSku(sku);
		price.setId(priceId);

		return productPriceFacade.get(sku, priceId, merchantStore, language);
	
	}
	
	@RequestMapping(value = { "/private/product/{sku}/inventory/{inventoryId}/price"},
			method = RequestMethod.GET)
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	public List<ReadableProductPrice> list(
			@PathVariable String sku,
			@PathVariable Long inventoryId,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {
		
		
		return productPriceFacade.list(sku, inventoryId, merchantStore, language);

		
	}
	
	
	@RequestMapping(value = { "/private/product/{sku}/prices"},
			method = RequestMethod.GET)
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	public List<ReadableProductPrice> list(
			@PathVariable String sku,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {
		
		
		return productPriceFacade.list(sku, merchantStore, language);

		
	}
	
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/{sku}/price/{priceId}"},
			method = RequestMethod.DELETE)
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	public void delete(
			@PathVariable String sku,
			@PathVariable Long priceId,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {
		
		
		productPriceFacade.delete(priceId, sku, merchantStore);
		
	}

}
