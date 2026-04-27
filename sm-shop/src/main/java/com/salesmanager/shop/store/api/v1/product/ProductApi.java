package com.salesmanager.shop.store.api.v1.product;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.salesmanager.core.business.services.catalog.category.CategoryService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.ProductCriteria;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.LightPersistableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;
import com.salesmanager.shop.store.controller.product.facade.ProductFacade;
import com.salesmanager.shop.utils.ImageFilePath;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * API to create, read, update and delete a Product API.
 *
 * @author Carl Samson
 */
@Controller
@RequestMapping("/api/v1")
@Tag(name = "Product definition resource (Create udtate and delete product definition. Serves api v1 and v2 with backward compatibility)")
public class ProductApi {

	@Inject
	private CategoryService categoryService;

	@Inject
	private ProductService productService;

	@Autowired
	private ProductFacade productFacade;

	@Inject
	private ProductCommonFacade productCommonFacade;

	@Inject
	@Qualifier("img")
	private ImageFilePath imageUtils;

	private static final Logger LOGGER = LoggerFactory.getLogger(ProductApi.class);

	/**
	 * Create product
	 * @param product
	 * @param merchantStore
	 * @param language
	 * @return Entity
	 */
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = { "/private/product", "/auth/products" }, // private
																			// for
			// adding
			// products
			method = RequestMethod.POST)
	public @ResponseBody Entity create(@Valid @RequestBody PersistableProduct product,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {
	
		Long id = productCommonFacade.saveProduct(merchantStore, product, language);
		Entity returnEntity = new Entity();
		returnEntity.setId(id);
		return returnEntity;

	}

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/{id}", "/auth/product/{id}" }, method = RequestMethod.PUT)
	@Operation(summary = "Update product")
	public void update(@PathVariable Long id,
			@Valid @RequestBody PersistableProduct product, @Parameter(hidden = true) MerchantStore merchantStore,
			HttpServletRequest request, HttpServletResponse response) {

		try {
			// Make sure we have consistency in this request
			if (!id.equals(product.getId())) {
				response.sendError(400, "Error url id does not match object id");
			}

			productCommonFacade.saveProduct(merchantStore, product,
					merchantStore.getDefaultLanguage());
		} catch (Exception e) {
			LOGGER.error("Error while updating product", e);
			try {
				response.sendError(503, "Error while updating product " + e.getMessage());
			} catch (Exception ignore) {
			}

		}
	}

	/** updates price quantity **/
	@ResponseStatus(HttpStatus.OK)
	@PatchMapping(value = "/private/product/{id}", produces = { APPLICATION_JSON_VALUE })
	@Operation(summary = "Update product inventory")
	public void update(
			@PathVariable Long id,
			@Valid @RequestBody
			LightPersistableProduct product,
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {
		productCommonFacade.update(id, product, merchantStore, language);
		return;

	}

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/{id}", "/auth/product/{id}" }, method = RequestMethod.DELETE)
	public void delete(@PathVariable Long id, @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		productCommonFacade.deleteProduct(id, merchantStore);
	}

	/**
	 * List products
	 * Filtering product lists based on product option and option value ?category=1
	 * &manufacturer=2 &type=... &lang=en|fr NOT REQUIRED, will use request language
	 * &start=0 NOT REQUIRED, can be used for pagination &count=10 NOT REQUIRED, can
	 * be used to limit item count
	 *
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/products", method = RequestMethod.GET)
	@ResponseBody
	public ReadableProductList list(
			@RequestParam(value = "lang", required = false) String lang,
			@RequestParam(value = "category", required = false) Long category,
			@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "sku", required = false) String sku,
			@RequestParam(value = "manufacturer", required = false) Long manufacturer,
			@RequestParam(value = "optionValues", required = false) List<Long> optionValueIds,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "owner", required = false) Long owner,
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page, // current
			@RequestParam(value = "origin", required = false, defaultValue = ProductCriteria.ORIGIN_SHOP) String origin,
			// page
			// 0
			// ..
			// n
			// allowing
			// navigation
			@RequestParam(value = "count", required = false, defaultValue = "100") Integer count, // count
			@RequestParam(value = "slug", required = false) String slug, // category slug
			@RequestParam(value = "available", required = false) Boolean available,
			// per
			// page
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language, HttpServletRequest request,
			HttpServletResponse response) throws Exception {

		ProductCriteria criteria = new ProductCriteria();

		criteria.setOrigin(origin);

		// do not use legacy pagination anymore
		if (lang != null) {
			criteria.setLanguage(lang);
		} else {
			criteria.setLanguage(language.getCode());
		}
		if (!StringUtils.isBlank(status)) {
			criteria.setStatus(status);
		}
		// Start Category handling
		List<Long> categoryIds = new ArrayList<Long>();
		if (slug != null) {
			Category categoryBySlug = categoryService.getBySeUrl(merchantStore, slug, language);
			categoryIds.add(categoryBySlug.getId());
		}
		if (category != null) {
			categoryIds.add(category);
		}
		if (categoryIds.size() > 0) {
			criteria.setCategoryIds(categoryIds);
		}
		// End Category handling

		if (available != null && available) {
			criteria.setAvailable(available);
		}

		if (manufacturer != null) {
			criteria.setManufacturerId(manufacturer);
		}

		if (CollectionUtils.isNotEmpty(optionValueIds)) {
			criteria.setOptionValueIds(optionValueIds);
		}

		if (owner != null) {
			criteria.setOwnerId(owner);
		}

		if (page != null) {
			criteria.setStartPage(page);
		}

		if (count != null) {
			criteria.setMaxCount(count);
		}

		if (!StringUtils.isBlank(name)) {
			criteria.setProductName(name);
		}

		if (!StringUtils.isBlank(sku)) {
			criteria.setCode(sku);
		}

		// TODO
		// RENTAL add filter by owner
		// REPOSITORY to use the new filters

		try {
			return productFacade.getProductListsByCriterias(merchantStore, language, criteria);

		} catch (Exception e) {

			LOGGER.error("Error while filtering products product", e);
			try {
				response.sendError(503, "Error while filtering products " + e.getMessage());
			} catch (Exception ignore) {
			}

			return null;
		}
	}

	/**
	 * API for getting a product
	 * Removed in 3.2.5 in favor of /product/sku
	 *
	 * @param id
	 * @param lang     ?lang=fr|en|...
	 * @param response
	 * @return ReadableProduct
	 * @throws Exception
	 *                   <p>
	 *                   /api/product/123
	 */
    /**
	@RequestMapping(value = {"/product/{id}","/products/{id}"}, method = RequestMethod.GET)
	@Operation(summary = "Get a product by id")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Single product found") })
	@ResponseBody
	public ReadableProduct get(@PathVariable final Long id, @RequestParam(value = "lang", required = false) String lang,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language, HttpServletResponse response)
			throws Exception {
		ReadableProduct product = productCommonFacade.getProduct(merchantStore, id, language);

		if (product == null) {
			response.sendError(404, "Product not fount for id " + id);
			return null;
		}

		return product;
	}
	**/

	/**
	 * Price calculation
	 * @param id
	 * @param variants
	 * @param merchantStore
	 * @param language
	 * @return
	 */
	/**
	@RequestMapping(value = "/product/{id}/price", method = RequestMethod.POST)
	@Operation(summary = "Calculate product price with variants")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Price calculated") })
	@ResponseBody
	public ReadableProductPrice price(@PathVariable final Long id,
			@RequestBody ProductPriceRequest variants,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		return productFacade.getProductPrice(id, variants, merchantStore, language);

	}
	**/

	/**
	 * API for getting a product
	 *
	 * @param friendlyUrl
	 * @param lang        ?lang=fr|en
	 * @param response
	 * @return ReadableProduct
	 * @throws Exception
	 *                   <p>
	 *                   /api/product/123
	 */
	@RequestMapping(value = { "/product/{friendlyUrl}",
			"/product/friendly/{friendlyUrl}" }, method = RequestMethod.GET)
	@Operation(summary = "Get a product by friendlyUrl (slug)")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Single product found") })
	@ResponseBody
	public ReadableProduct getByfriendlyUrl(@PathVariable final String friendlyUrl,
			@RequestParam(value = "lang", required = false) String lang, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language, HttpServletResponse response) throws Exception {
		ReadableProduct product = productFacade.getProductBySeUrl(merchantStore, friendlyUrl, language);

		if (product == null) {
			response.sendError(404, "Product not fount for id " + friendlyUrl);
			return null;
		}

		return product;
	}

	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/product/unique" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Check if product code already exists")
	public ResponseEntity<EntityExists> exists(@RequestParam(value = "code") String code,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		boolean exists = productCommonFacade.exists(code, merchantStore);
		return new ResponseEntity<EntityExists>(new EntityExists(exists), HttpStatus.OK);

	}

	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = { "/private/product/{productId}/category/{categoryId}"}, method = RequestMethod.POST)
	public void addProductToCategory(@PathVariable Long productId,
			@PathVariable Long categoryId, @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language,
			HttpServletResponse response) throws Exception {

		try {
			// get the product
			Product product = productService.getById(productId);

			if (product == null) {
				throw new ResourceNotFoundException("Product id [" + productId + "] is not found");
			}

			if (product.getMerchantStore().getId().intValue() != merchantStore.getId().intValue()) {
				throw new UnauthorizedException(
						"Product id [" + productId + "] does not belong to store [" + merchantStore.getCode() + "]");
			}

			Category category = categoryService.getById(categoryId);

			if (category == null) {
				throw new ResourceNotFoundException("Category id [" + categoryId + "] is not found");
			}

			if (category.getMerchantStore().getId().intValue() != merchantStore.getId().intValue()) {
				throw new UnauthorizedException(
						"Category id [" + categoryId + "] does not belong to store [" + merchantStore.getCode() + "]");
			}

			productCommonFacade.addProductToCategory(category, product, language);

		} catch (Exception e) {
			throw new ServiceRuntimeException(e);
		}
	}

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/{productId}/category/{categoryId}" }, method = RequestMethod.DELETE)
	public void removeProductFromCategory(@PathVariable Long productId,
			@PathVariable Long categoryId, @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		try {
			Product product = productService.getById(productId);

			if (product == null) {
				throw new ResourceNotFoundException("Product id [" + productId + "] is not found");
			}

			if (product.getMerchantStore().getId().intValue() != merchantStore.getId().intValue()) {
				throw new UnauthorizedException(
						"Product id [" + productId + "] does not belong to store [" + merchantStore.getCode() + "]");
			}

			Category category = categoryService.getById(categoryId);

			if (category == null) {
				throw new ResourceNotFoundException("Category id [" + categoryId + "] is not found");
			}

			if (category.getMerchantStore().getId().intValue() != merchantStore.getId().intValue()) {
				throw new UnauthorizedException(
						"Category id [" + categoryId + "] does not belong to store [" + merchantStore.getCode() + "]");
			}

			productCommonFacade.removeProductFromCategory(category, product, language);

		} catch (Exception e) {
			throw new ServiceRuntimeException(e);
		}
	}

	/**
	 * Change product sort order
	 *
	 * @param id
	 * @param position
	 * @param merchantStore
	 * @param language
	 * @throws IOException
	 */

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { "/private/product/{id}", "/auth/product/{id}" }, method = RequestMethod.PATCH)
	@Operation(summary = "Patch product sort order")
	public void changeProductOrder(@PathVariable Long id,
			@RequestParam(value = "order", required = false, defaultValue = "0") Integer position,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) throws IOException {

		try {

			Product p = productService.getById(id);

			if (p == null) {
				throw new ResourceNotFoundException(
						"Product [" + id + "] not found for merchant [" + merchantStore.getCode() + "]");
			}

			if (p.getMerchantStore().getId() != merchantStore.getId()) {
				throw new ResourceNotFoundException(
						"Product [" + id + "] not found for merchant [" + merchantStore.getCode() + "]");
			}

			/**
			 * Change order
			 */
			p.setSortOrder(position);

		} catch (Exception e) {
			LOGGER.error("Error while updating Product position", e);
			throw new ServiceRuntimeException("Product [" + id + "] cannot be edited");
		}
	}

}
