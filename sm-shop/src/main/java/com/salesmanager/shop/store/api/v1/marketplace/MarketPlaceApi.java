package com.salesmanager.shop.store.api.v1.marketplace;

import javax.inject.Inject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.marketplace.ReadableMarketPlace;
import com.salesmanager.shop.store.api.exception.ErrorEntity;
import com.salesmanager.shop.store.controller.marketplace.facade.MarketPlaceFacade;
import com.salesmanager.shop.utils.LanguageUtils;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/v1")
public class MarketPlaceApi {

	static final String SIGNUP_NOT_IMPLEMENTED_CODE = "501";
	static final String SIGNUP_NOT_IMPLEMENTED_MESSAGE = "Store signup is not available";

	@Autowired
	private MarketPlaceFacade marketPlaceFacade;

	@Inject
	private LanguageUtils languageUtils;

	/**
	 * Get a marketplace from storeCode returns market place details and
	 * merchant store
	 */
	@GetMapping("/private/marketplace/{store}")
	@ApiOperation(httpMethod = "GET", value = "Get market place meta-data", notes = "", produces = "application/json", response = ReadableMarketPlace.class)
	public ReadableMarketPlace marketPlace(@PathVariable String store,
			@RequestParam(value = "lang", required = false) String lang) {

		Language language = languageUtils.getServiceLanguage(lang);
		return marketPlaceFacade.get(store, language);
	}

	/**
	 * Merchant signup is not implemented. Every request gets the same 501 and no
	 * user or store lookup is done, so the route cannot enumerate accounts.
	 */
	@PostMapping(value = "/store/signup", produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(httpMethod = "POST", value = "Signup store (not implemented)", notes = "Always returns 501", produces = "application/json", response = ErrorEntity.class)
	public ResponseEntity<ErrorEntity> signup() {
		return signupNotImplemented();
	}

	@GetMapping(value = { "/store/{store}/signup/{token}" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(httpMethod = "GET", value = "Validate store signup token (not implemented)", notes = "Always returns 501", response = ErrorEntity.class)
	public ResponseEntity<ErrorEntity> storeSignupVerify() {
		return signupNotImplemented();
	}

	private static ResponseEntity<ErrorEntity> signupNotImplemented() {
		ErrorEntity error = new ErrorEntity();
		error.setErrorCode(SIGNUP_NOT_IMPLEMENTED_CODE);
		error.setMessage(SIGNUP_NOT_IMPLEMENTED_MESSAGE);
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).contentType(MediaType.APPLICATION_JSON).body(error);
	}
}
