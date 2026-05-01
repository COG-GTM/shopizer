package com.salesmanager.shop.store.api.v1.shipping;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.references.ReadableAddress;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.system.IntegrationModuleSummaryEntity;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.shipping.facade.ShippingFacade;
import com.salesmanager.shop.utils.AuthorizationUtils;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Shipping configuration resource", description = "Shipping configuration resource (Shipping Management Api)")
public class ShippingConfigurationApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(ShippingConfigurationApi.class);

	@Autowired
	private AuthorizationUtils authorizationUtils;

	@Autowired
	private ShippingFacade shippingFacade;

	@Autowired
	private ShippingService shippingService;

	@Operation(summary = "Get shipping origin for a specific merchant store")
	@RequestMapping(value = { "/private/shipping/origin" }, method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	public ReadableAddress shippingOrigin(@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		String user = authorizationUtils.authenticatedUser();
		authorizationUtils.authorizeUser(user, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_SHIPPING, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()), merchantStore);

		return shippingFacade.getShippingOrigin(merchantStore);

	}

	@RequestMapping(value = { "/private/shipping/origin" }, method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.OK)
	public void saveShippingOrigin(@RequestBody PersistableAddress address, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		String user = authorizationUtils.authenticatedUser();
		authorizationUtils.authorizeUser(user, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_SHIPPING, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()), merchantStore);

		shippingFacade.saveShippingOrigin(address, merchantStore);

	}

	// list packaging
	@Operation(summary = "Get list of configured packages types for a specific merchant store")
	@RequestMapping(value = { "/private/shipping/packages" }, method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public List<PackageDetails> listPackages(@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		String user = authorizationUtils.authenticatedUser();
		authorizationUtils.authorizeUser(user, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_SHIPPING, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()), merchantStore);

		return shippingFacade.listPackages(merchantStore);

	}

	// get packaging
	@Operation(summary = "Get package details")
	@RequestMapping(value = { "/private/shipping/package/{code}" }, method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public PackageDetails getPackage(@PathVariable String code, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		String user = authorizationUtils.authenticatedUser();
		authorizationUtils.authorizeUser(user, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_SHIPPING, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()), merchantStore);

		return shippingFacade.getPackage(code, merchantStore);

	}

	// create packaging
	@Operation(summary = "Create new package specification")
	@RequestMapping(value = { "/private/shipping/package" }, method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.OK)
	public void createPackage(@RequestBody PackageDetails details, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		String user = authorizationUtils.authenticatedUser();
		authorizationUtils.authorizeUser(user, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_SHIPPING, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()), merchantStore);

		shippingFacade.createPackage(details, merchantStore);

	}

	// edit packaging
	@Operation(summary = "Edit package specification")
	@RequestMapping(value = { "/private/shipping/package/{code}" }, method = RequestMethod.PUT)
	@ResponseStatus(HttpStatus.OK)
	public void updatePackage(@PathVariable String code, @RequestBody PackageDetails details,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		String user = authorizationUtils.authenticatedUser();
		authorizationUtils.authorizeUser(user, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_SHIPPING, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()), merchantStore);

		shippingFacade.updatePackage(code, details, merchantStore);

	}

	// delete packaging
	@Operation(summary = "Delete a package specification")
	@RequestMapping(value = { "/private/shipping/package/{code}" }, method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void deletePackage(@PathVariable String code, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		String user = authorizationUtils.authenticatedUser();
		authorizationUtils.authorizeUser(user, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_SHIPPING, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()), merchantStore);

		shippingFacade.deletePackage(code, merchantStore);

	}

	/**
	 * Get available shipping modules
	 * 
	 * @param merchantStore
	 * @param language
	 * @return
	 */
	@GetMapping("/private/modules/shipping")
	@Operation(summary = "List list of shipping modules")
	public List<IntegrationModuleSummaryEntity> shippingModules(@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		try {
			List<IntegrationModule> modules = shippingService.getShippingMethods(merchantStore);

			// configured modules
			Map<String, IntegrationConfiguration> configuredModules = shippingService
					.getShippingModulesConfigured(merchantStore);
			return modules.stream().map(m -> integrationModule(m, configuredModules)).collect(Collectors.toList());

		} catch (ServiceException e) {
			LOGGER.error("Error getting shipping modules", e);
			throw new ServiceRuntimeException("Error getting shipping modules", e);
		}

	}

	/**
	 * Get merchant shipping module details
	 * 
	 * @param code
	 * @param merchantStore
	 * @param language
	 * @return
	 */
	@GetMapping("/private/modules/shipping/{code}")
	@Operation(summary = "Shipping module by code")
	public IntegrationConfiguration shippingModule(@PathVariable String code,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		try {

			// configured modules
			List<IntegrationModule> modules = 
					shippingService
					.getShippingMethods(merchantStore);
			
			//check if exist
			Optional<IntegrationModule> checkIfExist = modules.stream().filter(m -> m.getCode().equals(code)).findAny();
			
			if(!checkIfExist.isPresent()) {
				throw new ResourceNotFoundException("Shipping module [" + code + "] not found");
			}
			
			IntegrationConfiguration config = shippingService.getShippingConfiguration(code, merchantStore);
			if (config == null) {
				config = new IntegrationConfiguration();
			}

			/**
			 * Build return object for now this is a read copy
			 */

			config.setActive(config.isActive());
			config.setDefaultSelected(config.isDefaultSelected());
			config.setIntegrationKeys(config.getIntegrationKeys());
			config.setIntegrationOptions(config.getIntegrationOptions());

			return config;

		} catch (ServiceException e) {
			LOGGER.error("Error getting shipping module [" + code + "]", e);
			throw new ServiceRuntimeException("Error getting shipping module [" + code + "]", e);
		}

	}

	@PostMapping(value = "/private/modules/shipping")
	public void configure(@RequestBody IntegrationModuleConfiguration configuration,
			@Parameter(hidden = true) MerchantStore merchantStore) {

		try {

			List<IntegrationModule> modules = shippingService.getShippingMethods(merchantStore);

			Map<String, IntegrationModule> map = modules.stream()
					.collect(Collectors.toMap(IntegrationModule::getCode, module -> module));

			IntegrationModule config = map.get(configuration.getCode());

			if (config == null) {
				throw new ResourceNotFoundException("Shipping module [" + configuration.getCode() + "] not found");
			}

			Map<String, IntegrationConfiguration> configuredModules = shippingService
					.getShippingModulesConfigured(merchantStore);

			IntegrationConfiguration integrationConfiguration = configuredModules.get(configuration.getCode());

			if (integrationConfiguration == null) {
				integrationConfiguration = new IntegrationConfiguration();
			}

			/**
			 * Build return object for now this is a read copy
			 */

			integrationConfiguration.setActive(configuration.isActive());
			integrationConfiguration.setDefaultSelected(configuration.isDefaultSelected());
			integrationConfiguration.setIntegrationKeys(configuration.getIntegrationKeys());
			integrationConfiguration.setIntegrationOptions(configuration.getIntegrationOptions());

			shippingService.saveShippingQuoteModuleConfiguration(integrationConfiguration, merchantStore);

		} catch (ServiceException e) {
			LOGGER.error("Error saving shipping modules", e);
			throw new ServiceRuntimeException("Error saving shipping module", e);
		}

	}

	private IntegrationModuleSummaryEntity integrationModule(IntegrationModule module,
			Map<String, IntegrationConfiguration> configuredModules) {

		IntegrationModuleSummaryEntity readable = null;
		readable = new IntegrationModuleSummaryEntity();

		readable.setCode(module.getCode());
		readable.setImage(module.getImage());
		if (configuredModules.containsKey(module.getCode())) {
			IntegrationConfiguration conf = configuredModules.get(module.getCode());
			readable.setConfigured(true);
			if(conf.isActive()) {
				readable.setActive(true);
			}
		}
		return readable;

	}

	// Module configuration
	/**
	 * private String moduleCode; private boolean active; private boolean
	 * defaultSelected; private Map<String, String> integrationKeys = new
	 * HashMap<String, String>(); private Map<String, List<String>>
	 * integrationOptions = new HashMap<String, List<String>>(); private String
	 * environment;
	 * 
	 * moduleCode:CODE, active:true, defaultSelected:false, environment: "TEST",
	 * integrationKeys { "key":"value", "anotherkey":"anothervalue"... }
	 */

}
