package com.salesmanager.test.shop.integration.product;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.group.ProductGroup;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Product group write endpoints must go through the admin JWT chain
 * (/api/v1/private/**) and be scoped to the requested store.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductGroupSecurityIntegrationTest extends ServicesTestSupport {

	private static final String OTHER_STORE = "grpsec2";
	private static final String OTHER_ADMIN = "grpsec2admin@test.com";
	private static final String OTHER_ADMIN_PASSWORD = "Password1";

	@Test
	public void anonymousDeleteOnPrivatePathIsRejectedAndGroupSurvives() {
		createGroup("SECGRP_ANON");

		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/products/group/SECGRP_ANON?store=" + Constants.DEFAULT_STORE, HttpMethod.DELETE,
				new HttpEntity<>(jsonHeaders()), String.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertTrue(groupCodes(Constants.DEFAULT_STORE).contains("SECGRP_ANON"));
	}

	@Test
	public void deleteWithInvalidTokenIsRejectedAndGroupSurvives() {
		createGroup("SECGRP_BADTOKEN");
		HttpHeaders headers = jsonHeaders();
		headers.add("Authorization", "Bearer not.a.valid.jwt");

		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/products/group/SECGRP_BADTOKEN?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(headers), String.class);

		// AuthenticationTokenFilter wraps JWT parse errors in a ServletException (500) on base and head
		assertFalse("invalid token must not delete, got " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful());
		assertTrue(groupCodes(Constants.DEFAULT_STORE).contains("SECGRP_BADTOKEN"));
	}

	@Test
	public void legacyPublicDeletePathNoLongerDeletesGroup() {
		createGroup("SECGRP_LEGACY");

		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/products/group/SECGRP_LEGACY?store=" + Constants.DEFAULT_STORE, HttpMethod.DELETE,
				new HttpEntity<>(jsonHeaders()), String.class);

		assertFalse("legacy DELETE must not succeed, got " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful());
		assertTrue(groupCodes(Constants.DEFAULT_STORE).contains("SECGRP_LEGACY"));
	}

	@Test
	public void adminCanDeleteGroupOnPrivatePath() {
		createGroup("SECGRP_ADMIN");
		assertTrue(groupCodes(Constants.DEFAULT_STORE).contains("SECGRP_ADMIN"));

		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/products/group/SECGRP_ADMIN?store=" + Constants.DEFAULT_STORE, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertFalse(groupCodes(Constants.DEFAULT_STORE).contains("SECGRP_ADMIN"));
	}

	@Test
	public void publicGetByGroupStillAnonymous() {
		createGroup("SECGRP_GET");

		ResponseEntity<ReadableProductList> response = testRestTemplate.exchange(
				"/api/v1/products/group/SECGRP_GET?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(jsonHeaders()), ReadableProductList.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
	}

	@Test
	public void adminOfAnotherStoreCannotDeleteDefaultStoreGroup() {
		ensureOtherStoreAndAdmin();
		createGroup("SECGRP_XSTORE");

		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/products/group/SECGRP_XSTORE?store=" + Constants.DEFAULT_STORE, HttpMethod.DELETE,
				new HttpEntity<>(getHeader(OTHER_ADMIN, OTHER_ADMIN_PASSWORD)), String.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertTrue(groupCodes(Constants.DEFAULT_STORE).contains("SECGRP_XSTORE"));
	}

	@Test
	public void productGroupMembershipIsScopedToRequestedStore() {
		ensureOtherStoreAndAdmin();
		ReadableProduct product = sampleProduct("grpsecprod");
		createGroup("SECGRP_ITEMS");

		// same store: product can be added and is visible publicly
		ResponseEntity<ReadableProductList> added = testRestTemplate.exchange(
				"/api/v1/private/products/" + product.getId() + "/group/SECGRP_ITEMS?store="
						+ Constants.DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(getHeader()), ReadableProductList.class);
		assertEquals(HttpStatus.CREATED, added.getStatusCode());
		assertTrue(groupProductIds(Constants.DEFAULT_STORE, "SECGRP_ITEMS").contains(product.getId()));

		// other store: DEFAULT product is not found, even for a superadmin
		ResponseEntity<String> crossAdd = testRestTemplate.exchange(
				"/api/v1/private/products/" + product.getId() + "/group/SECGRP_ITEMS?store=" + OTHER_STORE,
				HttpMethod.POST, new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.NOT_FOUND, crossAdd.getStatusCode());

		ResponseEntity<String> crossRemove = testRestTemplate.exchange(
				"/api/v1/private/products/" + product.getId() + "/group/SECGRP_ITEMS?store=" + OTHER_STORE,
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.NOT_FOUND, crossRemove.getStatusCode());
		assertTrue(groupProductIds(Constants.DEFAULT_STORE, "SECGRP_ITEMS").contains(product.getId()));

		// same store remove still works
		ResponseEntity<ReadableProductList> removed = testRestTemplate.exchange(
				"/api/v1/private/products/" + product.getId() + "/group/SECGRP_ITEMS?store="
						+ Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), ReadableProductList.class);
		assertEquals(HttpStatus.OK, removed.getStatusCode());
		assertFalse(groupProductIds(Constants.DEFAULT_STORE, "SECGRP_ITEMS").contains(product.getId()));
	}

	private void createGroup(String code) {
		ProductGroup group = new ProductGroup();
		group.setCode(code);
		group.setActive(true);
		ResponseEntity<ProductGroup> response = testRestTemplate.exchange(
				"/api/v1/private/products/group?store=" + Constants.DEFAULT_STORE, HttpMethod.POST,
				new HttpEntity<>(group, getHeader()), ProductGroup.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
	}

	private List<String> groupCodes(String store) {
		ResponseEntity<List<ProductGroup>> response = testRestTemplate.exchange(
				"/api/v1/private/product/groups?store=" + store, HttpMethod.GET, new HttpEntity<>(getHeader()),
				new ParameterizedTypeReference<List<ProductGroup>>() {
				});
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		return response.getBody().stream().map(ProductGroup::getCode).collect(Collectors.toList());
	}

	private List<Long> groupProductIds(String store, String code) {
		ResponseEntity<ReadableProductList> response = testRestTemplate.exchange(
				"/api/v1/products/group/" + code + "?store=" + store, HttpMethod.GET,
				new HttpEntity<>(jsonHeaders()), ReadableProductList.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		return response.getBody().getProducts().stream().map(ReadableProduct::getId).collect(Collectors.toList());
	}

	private synchronized void ensureOtherStoreAndAdmin() {
		ResponseEntity<String> existing = testRestTemplate.exchange("/api/v1/store/" + OTHER_STORE, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);
		if (existing.getStatusCode() == HttpStatus.OK) {
			return;
		}

		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 group street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(OTHER_STORE);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("grpsec2@test.com");
		store.setName(OTHER_STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		ResponseEntity<Void> storeResponse = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertThat(storeResponse.getStatusCode(), is(HttpStatus.OK));

		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(OTHER_ADMIN);
		user.setFirstName("Other");
		user.setLastName("Admin");
		user.setUserName(OTHER_ADMIN);
		user.setPassword(OTHER_ADMIN_PASSWORD);
		user.setRepeatPassword(OTHER_ADMIN_PASSWORD);
		user.setStore(OTHER_STORE);
		user.setActive(true);
		PersistableGroup g = new PersistableGroup();
		g.setName("ADMIN");
		user.getGroups().add(g);
		ResponseEntity<ReadableUser> userResponse = testRestTemplate.exchange(
				"/api/v1/private/user/?store=" + OTHER_STORE, HttpMethod.POST, new HttpEntity<>(user, getHeader()),
				ReadableUser.class);
		assertThat(userResponse.getStatusCode(), is(HttpStatus.OK));
		assertEquals(OTHER_STORE, userResponse.getBody().getMerchant());
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
