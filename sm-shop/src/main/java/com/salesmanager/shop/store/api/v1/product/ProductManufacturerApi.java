package com.salesmanager.shop.store.api.v1.product;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.salesmanager.core.business.services.catalog.product.manufacturer.ManufacturerService;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.manufacturer.ReadableManufacturer;
import com.salesmanager.shop.model.catalog.manufacturer.ReadableManufacturerList;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.model.entity.ListCriteria;
import com.salesmanager.shop.store.controller.manufacturer.facade.ManufacturerFacade;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;

/**
 * Manufacturer management Collection, Manufacturer ...
 *
 * @author c.samson
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Manufacturer / Brand management resource (Manufacturer / Brand Management Api)")
public class ProductManufacturerApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProductManufacturerApi.class);

	@Inject
	private ManufacturerService manufacturerService;

	@Inject
	private ManufacturerFacade manufacturerFacade;

	/**
	 * Method for creating a manufacturer
	 *
	 * @param manufacturer
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/private/manufacturer", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	@ResponseBody
	@Parameters({ @Parameter(name = "store", description = "Default: DEFAULT"),
			@Parameter(name = "lang", description = "Default: en") })
	public PersistableManufacturer create(@Valid @RequestBody PersistableManufacturer manufacturer,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language, HttpServletResponse response) {

		try {
			manufacturerFacade.saveOrUpdateManufacturer(manufacturer, merchantStore, language);

			return manufacturer;

		} catch (Exception e) {
			LOGGER.error("Error while creating manufacturer", e);
			try {
				response.sendError(503, "Error while creating manufacturer " + e.getMessage());
			} catch (Exception ignore) {
			}

			return null;
		}
	}

	@RequestMapping(value = "/manufacturer/{id}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	@Parameters({ @Parameter(name = "store", description = "Default: DEFAULT"),
			@Parameter(name = "lang", description = "Default: en") })
	public ReadableManufacturer get(@PathVariable Long id, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language, HttpServletResponse response) {

		try {
			ReadableManufacturer manufacturer = manufacturerFacade.getManufacturer(id, merchantStore, language);

			if (manufacturer == null) {
				response.sendError(404, "No Manufacturer found for ID : " + id);
			}

			return manufacturer;

		} catch (Exception e) {
			LOGGER.error("Error while getting manufacturer", e);
			try {
				response.sendError(503, "Error while getting manufacturer " + e.getMessage());
			} catch (Exception ignore) {
			}
		}

		return null;
	}

	
	@RequestMapping(value = "/private/manufacturers", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	@Parameters({ @Parameter(name = "store", description = "Default: DEFAULT"),
			@Parameter(name = "lang", description = "Default: en") })
	@Operation(summary = "List manufacturers by store")
	public ReadableManufacturerList listByStore(
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language,
			@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
			@RequestParam(value = "count", required = false, defaultValue = "10") Integer count) {

		ListCriteria listCriteria = new ListCriteria();
		listCriteria.setName(name);
		return manufacturerFacade.listByStore(merchantStore, language, listCriteria, page, count);
	}
	
	
	@RequestMapping(value = "/manufacturers", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	@Parameters({ @Parameter(name = "store", description = "Default: DEFAULT"),
			@Parameter(name = "lang", description = "Default: en") })
	@Operation(summary = "List manufacturers by store")
	public ReadableManufacturerList list(@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language,
			@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
			@RequestParam(value = "count", required = false, defaultValue = "10") Integer count) {

		ListCriteria listCriteria = new ListCriteria();
		listCriteria.setName(name);
		return manufacturerFacade.getAllManufacturers(merchantStore, language, listCriteria, page, count);
	}

	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/manufacturer/unique" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@Parameters({ @Parameter(name = "store", description = "Default: DEFAULT") })
	@Operation(summary = "Check if manufacturer code already exists")
	public ResponseEntity<EntityExists> exists(@RequestParam(value = "code") String code,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		boolean exists = manufacturerFacade.manufacturerExist(merchantStore, code);
		return new ResponseEntity<EntityExists>(new EntityExists(exists), HttpStatus.OK);

	}

	@RequestMapping(value = "/private/manufacturer/{id}", method = RequestMethod.PUT)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	@Parameters({ @Parameter(name = "store", description = "Default: DEFAULT"),
			@Parameter(name = "lang", description = "Default: en") })
	public void update(@PathVariable Long id,
			@Valid @RequestBody PersistableManufacturer manufacturer, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language, HttpServletRequest request, HttpServletResponse response) {

		try {
			manufacturer.setId(id);
			manufacturerFacade.saveOrUpdateManufacturer(manufacturer, merchantStore, language);
		} catch (Exception e) {
			LOGGER.error("Error while creating manufacturer", e);
			try {
				response.sendError(503, "Error while creating manufacturer " + e.getMessage());
			} catch (Exception ignore) {
			}
		}
	}

	@RequestMapping(value = "/private/manufacturer/{id}", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	@Parameters({ @Parameter(name = "store", description = "Default: DEFAULT"),
			@Parameter(name = "lang", description = "Default: en") })
	public void delete(@PathVariable Long id, @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language,
			HttpServletResponse response) {

		try {
			Manufacturer manufacturer = manufacturerService.getById(id);

			if (manufacturer != null) {
				manufacturerFacade.deleteManufacturer(manufacturer, merchantStore, language);
			} else {
				response.sendError(404, "No Manufacturer found for ID : " + id);
			}

		} catch (Exception e) {
			LOGGER.error("Error while deleting manufacturer id " + id, e);
			try {
				response.sendError(503, "Error while deleting manufacturer id " + id + " - " + e.getMessage());
			} catch (Exception ignore) {
			}
		}
	}

	@RequestMapping(value = "/category/{id}/manufacturer", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Get all manufacturers for all items in a given category")
	@ResponseBody
	@Parameters({ @Parameter(name = "store", description = "Default: DEFAULT"),
			@Parameter(name = "lang", description = "Default: en") })
	public List<ReadableManufacturer> list(@PathVariable final Long id, // category
																					// id
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language, HttpServletResponse response)
			throws Exception {

		return manufacturerFacade.getByProductInCategory(merchantStore, language, id);

	}

}
