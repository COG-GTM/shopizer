package com.salesmanager.shop.store.api.v1.store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.collect.ImmutableMap;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.merchant.MerchantStoreCriteria;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableBrand;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStoreList;
import com.salesmanager.shop.store.api.exception.RestApiException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;
import com.salesmanager.shop.utils.ServiceRequestCriteriaBuilderUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;


@RestController
@RequestMapping("/api/v1")
@Tag(name = "API and stores")
public class MerchantStoreApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(MerchantStoreApi.class);

	private static final Map<String, String> MAPPING_FIELDS = ImmutableMap.<String, String>builder()
			.put("name", "name").put("readableAudit.user", "auditSection.modifiedBy").build();

	@Inject
	private StoreFacade storeFacade;

	@Inject
	private UserFacade userFacade;

	@GetMapping(value = { "/store/{code}" })
	@Operation(summary = "Get merchant store", description = "")
	public ReadableMerchantStore store(@PathVariable String code,
			@RequestParam(value = "lang", required = false) String lang) {
		//return storeFacade.getByCode(code, lang);
		ReadableMerchantStore readable =  storeFacade.getByCode(code, lang);
		return readable;
	}

	@GetMapping(value = { "/private/store/{code}" })
	@Operation(summary = "Get merchant store full details", description = "")
public ReadableMerchantStore storeFull(
			@PathVariable String code,
			@Parameter(hidden = true) Language language) {

		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}

		userFacade.authorizedGroup(authenticatedUser, Stream.of("SUPERADMIN", "ADMIN_RETAILER").collect(Collectors.toList()));
		return storeFacade.getFullByCode(code, language);
	}

	@GetMapping(value = { "/private/merchant/{code}/stores" })
	@Operation(summary = "Get retailer child stores", description = "Merchant (retailer) can have multiple stores")
public ReadableMerchantStoreList list(@PathVariable String code, @Parameter(hidden = true) Language language,
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
			@RequestParam(value = "count", required = false, defaultValue = "10") Integer count) {

		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}

		userFacade.authorizedGroup(authenticatedUser, Stream.of("SUPERADMIN", "ADMIN_RETAILER").collect(Collectors.toList()));

		//ADMIN_RETAILER only see pertaining stores
		
		
		return storeFacade.getChildStores(language, code, page, count);
	}

	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/stores" })
	@Operation(summary = "Get list of stores. Returns all retailers and stores. If superadmin everything is returned, else only retailer and child stores.", description = "")
public ReadableMerchantStoreList get(
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language,
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
			@RequestParam(value = "count", required = false, defaultValue = "10") Integer count,
			HttpServletRequest request) {

		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}

		// requires superadmin, admin and admin retail to see all
		userFacade.authorizedGroup(authenticatedUser,
				Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL)
						.collect(Collectors.toList()));

		MerchantStoreCriteria criteria = createMerchantStoreCriteria(request);
		
		if (userFacade.userInRoles(authenticatedUser, Arrays.asList(Constants.GROUP_SUPERADMIN))) {
			criteria.setStoreCode(null);
		} else {
			criteria.setStoreCode(merchantStore.getCode());
		}

		//return storeFacade.findAll(criteria, language, page, count);
		ReadableMerchantStoreList readable = storeFacade.findAll(criteria, language, page, count);
		return readable;
	}
	


	/**
	 * List of store names
	 * @param merchantStore
	 * @param request
	 * @return
	 */
	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/stores/names" })
	@Operation(summary = "Get list of store names. Returns all retailers and stores", description = "")
	public List<ReadableMerchantStore> list(
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language,
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
			@RequestParam(value = "count", required = false, defaultValue = "10") Integer count,
			HttpServletRequest request
			) {
		
		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}

		// requires superadmin, admin and admin retail to see all
		userFacade.authorizedGroup(authenticatedUser,
				Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL)
						.collect(Collectors.toList()));

		MerchantStoreCriteria criteria = createMerchantStoreCriteria(request);
		
		if (userFacade.userInRoles(authenticatedUser, Arrays.asList(Constants.GROUP_SUPERADMIN))) {
			criteria.setStoreCode(null);
		} else {
			criteria.setStoreCode(merchantStore.getCode());
		}

		ReadableMerchantStoreList list = storeFacade.findAll(criteria, language, page, count);
		return list.getData();

	}
	
	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/store/languages" })
	@Operation(summary = "Get list of store supported languages.", description = "")
	public List<Language> supportedLanguages(
			@Parameter(hidden = true) MerchantStore merchantStore,
			HttpServletRequest request) {

		return storeFacade.supportedLanguages(merchantStore);
	}

	@ResponseStatus(HttpStatus.OK)
	@PostMapping(value = { "/private/store" })
	@Operation(summary = "Creates a new store", description = "")
	public void create(@Valid @RequestBody PersistableMerchantStore store) {
		
		
		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}
		
		userFacade.authorizedGroup(authenticatedUser, Stream.of("SUPERADMIN", "ADMIN_RETAILER").collect(Collectors.toList()));

		
		storeFacade.create(store);
	}

	@ResponseStatus(HttpStatus.OK)
	@PutMapping(value = { "/private/store/{code}" })
	@Operation(summary = "Updates a store", description = "")
	public void update(@PathVariable String code, @Valid @RequestBody PersistableMerchantStore store,
			HttpServletRequest request) {

		String userName = getUserFromRequest(request);
		validateUserPermission(userName, code);
		store.setCode(code);
		storeFacade.update(store);
	}

	private String getUserFromRequest(HttpServletRequest request) {
		// user doing action must be attached to the store being modified
		Principal principal = request.getUserPrincipal();
		return principal.getName();
	}

	private void validateUserPermission(String userName, String code) {
		// TODO reviewed Spring Security should be used
		if (!userFacade.authorizedStore(userName, code)) {
			throw new UnauthorizedException("User " + userName + " not authorized");
		}
	}

	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/store/{code}/marketing" })
	@Operation(summary = "Get store branding and marketing details", description = "")
	public ReadableBrand getStoreMarketing(@PathVariable String code, HttpServletRequest request) {
		String userName = getUserFromRequest(request);
		validateUserPermission(userName, code);
		return storeFacade.getBrand(code);
	}

	/**
	 * List child stores
	 * 
	 * @param code
	 * @param request
	 * @return
	 */
	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/merchant/{code}/children" })
	@Operation(summary = "Get child stores", description = "")
public ReadableMerchantStoreList children(@PathVariable String code, @Parameter(hidden = true) Language language,
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
			@RequestParam(value = "count", required = false, defaultValue = "10") Integer count,
			HttpServletRequest request) {

		String userName = getUserFromRequest(request);
		validateUserPermission(userName, code);
		return storeFacade.getChildStores(language, code, page, count);

	}

	@Deprecated
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping(value = { "/private/store/{code}/marketing" })
	@Operation(summary = "Create or save store branding and marketing details", description = "")
	public void saveStoreMarketing(@PathVariable String code, @RequestBody PersistableBrand brand,
			HttpServletRequest request) {
		String userName = getUserFromRequest(request);
		validateUserPermission(userName, code);
		storeFacade.createBrand(code, brand);
	}

	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping(value = { "/private/store/{code}/marketing/logo" })
	@Operation(summary = "Add store logo", description = "")
	public void addLogo(@PathVariable String code, @RequestParam("file") MultipartFile uploadfile,
			HttpServletRequest request) {

		// user doing action must be attached to the store being modified
		String userName = getUserFromRequest(request);

		validateUserPermission(userName, code);

		if (uploadfile.isEmpty()) {
			throw new RestApiException("Upload file is empty");
		}

		InputContentFile cmsContentImage = createInputContentFile(uploadfile);
		storeFacade.addStoreLogo(code, cmsContentImage);
	}

	private InputContentFile createInputContentFile(MultipartFile image) {

		InputContentFile cmsContentImage = null;

		try {

			InputStream input = new ByteArrayInputStream(image.getBytes());
			cmsContentImage = new InputContentFile();
			cmsContentImage.setFileName(image.getOriginalFilename());
			cmsContentImage.setMimeType(image.getContentType());
			cmsContentImage.setFileContentType(FileContentType.LOGO);
			cmsContentImage.setFile(input);

		} catch (IOException ioe) {
			throw new RestApiException(ioe);
		}

		return cmsContentImage;
	}

	@ResponseStatus(HttpStatus.OK)
	@DeleteMapping(value = { "/private/store/{code}/marketing/logo" })
	@Operation(summary = "Delete store logo", description = "")
	public void deleteStoreLogo(@PathVariable String code, HttpServletRequest request) {

		// user doing action must be attached to the store being modified
		String userName = getUserFromRequest(request);
		validateUserPermission(userName, code);

		// delete store logo
		storeFacade.deleteLogo(code);
	}

	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/store/unique","/private/store/unique" })
	@Operation(summary = "Check if store code already exists", description = "")
	public ResponseEntity<EntityExists> exists(@RequestParam(value = "code") String code) {
		boolean isStoreExist = storeFacade.existByCode(code);
		return new ResponseEntity<EntityExists>(new EntityExists(isStoreExist), HttpStatus.OK);
	}



	@ResponseStatus(HttpStatus.OK)
	@DeleteMapping(value = { "/private/store/{code}" })
	@Operation(summary = "Deletes a store", description = "")
	public void delete(@PathVariable String code, HttpServletRequest request) {
		String userName = getUserFromRequest(request);
		validateUserPermission(userName, code);
		storeFacade.delete(code);
	}

	
	private MerchantStoreCriteria createMerchantStoreCriteria(HttpServletRequest request) {
		try {
			return (MerchantStoreCriteria)ServiceRequestCriteriaBuilderUtils.buildRequestCriterias(new MerchantStoreCriteria(), MAPPING_FIELDS,
					request);
		} catch (Exception e) {
			throw new RestApiException("Error while binding request parameters");
		}

	}


}
