package com.salesmanager.shop.store.api.v1.product;

import java.util.List;

import jakarta.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.ReadableProductOptionSet;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.store.controller.product.facade.ProductOptionSetFacade;

import io.swagger.v3.oas.annotations.tags.Tag;


import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.Parameter;

@Controller
@RequestMapping("/api/v1")
@Tag(name = "Product property set regroupment management resource (Product Options Set Management Api)")
public class ProductPropertySetApi {

	@Autowired
	private ProductOptionSetFacade productOptionSetFacade;

	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = { "/private/product/property/set" }, method = RequestMethod.POST)
	public void create(
			@Valid @RequestBody PersistableProductOptionSet optionSet, 
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		productOptionSetFacade.create(optionSet, merchantStore, language);

	}

	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/product/property/set/unique" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Check if option set code already exists")
	public ResponseEntity<EntityExists> exists(
			@RequestParam(value = "code") String code,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {

		boolean isOptionExist = productOptionSetFacade.exists(code, merchantStore);
		return new ResponseEntity<EntityExists>(new EntityExists(isOptionExist), HttpStatus.OK);
	}


	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/property/set/{id}" }, method = RequestMethod.GET)
	@ResponseBody
	public ReadableProductOptionSet get(
			@PathVariable Long id, 
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		return productOptionSetFacade.get(id, merchantStore, language);

	}


	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/property/set/{id}" }, method = RequestMethod.PUT)
	public void update(
			@Valid @RequestBody PersistableProductOptionSet option, 
			@PathVariable Long id,
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {
		
		option.setId(id);
		productOptionSetFacade.update(id, option, merchantStore, language);

	}


	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/property/set/{id}" }, method = RequestMethod.DELETE)
	public void delete(
			@PathVariable Long id,
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		productOptionSetFacade.delete(id, merchantStore);

	}

	/**
	 * Get property set by store
	 * filter by product type
	 * @param merchantStore
	 * @param language
	 * @return
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/property/set" }, method = RequestMethod.GET)
	public @ResponseBody List<ReadableProductOptionSet> list(
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language,
			@RequestParam(value = "productType", required = false) String type) {

		if(!StringUtils.isBlank(type)) {
			return productOptionSetFacade.list(merchantStore, language, type);
		} else {
			return productOptionSetFacade.list(merchantStore, language);
		}
		
		
	}
	

}