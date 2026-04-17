package com.salesmanager.shop.store.api.v1.user;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.security.Principal;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.UserCriteria;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.model.entity.UniqueEntity;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.ReadableUserList;
import com.salesmanager.shop.model.user.UserPassword;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameters;

/** Api for managing admin users */
@RestController
@RequestMapping(value = "/api/v1")
@Tag(name = "User management resource (User Management Api)")
public class UserApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserApi.class);

	@Inject
	private UserFacade userFacade;

	/**
	 * Get userName by merchant code and userName
	 *
	 * @param storeCode
	 * @param name
	 * @param request
	 * @return
	 */
	@ResponseStatus(HttpStatus.OK)
	@GetMapping({ "/private/users/{id}" })
	@Operation(summary = "Get a specific user profile by user id")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "400", description = "Error while getting User"),
			@ApiResponse(responseCode = "401", description = "Login required") })
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	public ReadableUser get(@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language, @PathVariable Long id,
			HttpServletRequest request) {

		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}
		// only admin and superadmin allowed
		userFacade.authorizedGroup(authenticatedUser, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()));

		return userFacade.findById(id, merchantStore, language);
	}

	/**
	 * Creates a new user
	 *
	 * @param store
	 * @param user
	 * @return
	 */
	@ResponseStatus(HttpStatus.OK)
	@PostMapping(value = { "/private/user/" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Creates a new user")
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	public ReadableUser create(
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language,
			@Valid @RequestBody PersistableUser user, HttpServletRequest request) {
		/** Must be superadmin or admin */
		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}
		// only admin and superadmin allowed
		userFacade.authorizedGroup(authenticatedUser, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()));

		/** if user is admin, user must be in that store */
		if (!userFacade.userInRoles(authenticatedUser, Arrays.asList(Constants.GROUP_SUPERADMIN))) {
			if (!userFacade.authorizedStore(authenticatedUser, merchantStore.getCode())) {
				throw new UnauthorizedException("Operation unauthorized for user [" + authenticatedUser
						+ "] and store [" + merchantStore.getCode() + "]");
			}
		}

		return userFacade.create(user, merchantStore);
	}

	@ResponseStatus(HttpStatus.OK)
	@PutMapping(value = { "/private/user/{id}" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	@Operation(summary = "Updates a user")
	public ReadableUser update(@Valid @RequestBody PersistableUser user, @PathVariable Long id,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language

	) {

		String authenticatedUser = userFacade.authenticatedUser();// requires
																	// user
																	// doing
																	// action

		userFacade.authorizedGroups(authenticatedUser, user);

		return userFacade.update(id, authenticatedUser, merchantStore, user);
	}

	@ResponseStatus(HttpStatus.OK)
	@PatchMapping(value = { "/private/user/{id}/password" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Updates a user password")
	public void password(@Valid @RequestBody UserPassword password, @PathVariable Long id) {

		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}
		userFacade.changePassword(id, authenticatedUser, password);
	}

	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = { "/private/users" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get list of user")
	@Parameters({ 
		@Parameter(name = "store", description = "store", example = "DEFAULT"),
		@Parameter(name = "lang", description = "lang", example = "en") })
	public ReadableUserList list(
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language,
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
			@RequestParam(value = "count", required = false, defaultValue = "20") Integer count,
			@RequestParam(value = "emailAddress", required = false) String emailAddress) {

		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}

		UserCriteria criteria = new UserCriteria();
		if(!StringUtils.isBlank(emailAddress)) {
			criteria.setAdminEmail(emailAddress);
		}
		
		criteria.setStoreCode(merchantStore.getCode());

		if (!userFacade.userInRoles(authenticatedUser, Arrays.asList(Constants.GROUP_SUPERADMIN))) {
			if (!userFacade.authorizedStore(authenticatedUser, merchantStore.getCode())) {
				throw new UnauthorizedException("Operation unauthorized for user [" + authenticatedUser
						+ "] and store [" + merchantStore + "]");
			}
		}

		userFacade.authorizedGroup(authenticatedUser, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()));
		return userFacade.listByCriteria(criteria, page, count, language);
	}
	
	@PatchMapping(value = "/private/user/{id}/enabled", produces = { APPLICATION_JSON_VALUE })
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT") })
	public void updateEnabled(
			@PathVariable Long id, 
			@Valid @RequestBody PersistableUser user,
			@Parameter(hidden = true) MerchantStore merchantStore
			) {
		
		// superadmin, admin and retail_admin
		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}

		userFacade.authorizedGroup(authenticatedUser, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()));

		user.setId(id);
		userFacade.updateEnabled(merchantStore, user);
	}

	@ResponseStatus(HttpStatus.OK)
	@DeleteMapping(value = { "/private/user/{id}" })
	@Operation(summary = "Deletes a user")
	@Parameters({ @Parameter(name = "store", description = "store", example = "DEFAULT"),
			@Parameter(name = "lang", description = "lang", example = "en") })
	public void delete(@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language, @PathVariable Long id,
			HttpServletRequest request) {

		/** Must be superadmin or admin */
		String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}

		if (!userFacade.userInRoles(authenticatedUser, Arrays.asList(Constants.GROUP_SUPERADMIN))) {
			userFacade.authorizedStore(authenticatedUser, merchantStore.getCode());
		}

		userFacade.authorizedGroup(authenticatedUser, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()));

		userFacade.delete(id, merchantStore.getCode());
	}

	@ResponseStatus(HttpStatus.OK)
	@PostMapping(value = { "/private/user/unique" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Check if username already exists")
	public ResponseEntity<EntityExists> exists(@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language,
			@RequestBody UniqueEntity userName) {

		boolean isUserExist = true;// default user exist
		try {
			// will throw an exception if not fount
			userFacade.findByUserName(userName.getUnique(), userName.getMerchant(), language);

		} catch (ResourceNotFoundException e) {
			isUserExist = false;
		}
		return new ResponseEntity<EntityExists>(new EntityExists(isUserExist), HttpStatus.OK);
	}

	/**
	 * Get logged in customer profile
	 * 
	 * @param merchantStore
	 * @param language
	 * @param request
	 * @return
	 */
	@GetMapping("/private/user/profile")
	@Parameters({ @Parameter(name = "lang", description = "lang", example = "en") })
	public ReadableUser getAuthUser(@Parameter(hidden = true) Language language, HttpServletRequest request) {
		Principal principal = request.getUserPrincipal();
		String userName = principal.getName();
		ReadableUser user = userFacade.findByUserName(userName, null, language);

		if (!user.isActive()) {
			throw new UnauthorizedException("User " + userName + " not not active");
		}

		return user;

	}
}
