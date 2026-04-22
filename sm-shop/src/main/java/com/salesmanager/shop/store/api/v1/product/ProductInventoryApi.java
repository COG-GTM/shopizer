package com.salesmanager.shop.store.api.v1.product;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.inventory.PersistableInventory;
import com.salesmanager.shop.model.catalog.product.inventory.ReadableInventory;
import com.salesmanager.shop.model.entity.ReadableEntityList;
import com.salesmanager.shop.store.api.exception.RestApiException;
import com.salesmanager.shop.store.controller.product.facade.ProductInventoryFacade;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
@Controller
@RequestMapping("/api/v1")
@Tag(name = "API")
public class ProductInventoryApi {

	@Autowired
	private ProductInventoryFacade productInventoryFacade;

	private static final Logger LOGGER = LoggerFactory.getLogger(ProductInventoryApi.class);

	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = { "/private/product/{productId}/inventory" }, method = RequestMethod.POST)
	public @ResponseBody ReadableInventory create(@PathVariable Long productId,
			@Valid @RequestBody PersistableInventory inventory, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {
		inventory.setProductId(productId);
		return productInventoryFacade.add(inventory, merchantStore, language);
	}

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/{productId}/inventory/{id}" }, method = RequestMethod.PUT)
	public void update(
			@PathVariable Long productId, 
			@PathVariable Long id,
			@Valid @RequestBody PersistableInventory inventory, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {
		inventory.setId(id);
		inventory.setProductId(inventory.getProductId());
		inventory.setVariant(inventory.getVariant());
		inventory.setProductId(productId);
		productInventoryFacade.update(inventory, merchantStore, language);

	}

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/{productId}/inventory/{id}" }, method = RequestMethod.DELETE)
	public void delete(
			@PathVariable Long productId, 
			@PathVariable Long id, 
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {

		productInventoryFacade.delete(productId, id, merchantStore);

	}

	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/product/{sku}/inventory" })
	public @ResponseBody ReadableEntityList<ReadableInventory> getBySku(
			@PathVariable String sku,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language,
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
			@RequestParam(value = "count", required = false, defaultValue = "10") Integer count) {

		return productInventoryFacade.get(sku, merchantStore, language, page, count);

	}
	
	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/product/inventory" })
	public @ResponseBody ReadableEntityList<ReadableInventory> getByProductId(
			@RequestParam Long productId,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language,
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
			@RequestParam(value = "count", required = false, defaultValue = "10") Integer count) {
		
		if(productId == null) {
			throw new RestApiException("Requires request parameter product id [/product/inventoty?productId");
		}

		return productInventoryFacade.get(productId, merchantStore, language, page, count);

	}

}
