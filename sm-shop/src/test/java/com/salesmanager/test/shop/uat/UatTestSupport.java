package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.smoke.SmokeTestSupport;

abstract class UatTestSupport extends SmokeTestSupport {

	protected UatCustomer registerAndLoginCustomer() {
		String email = uniqueCode("uatcustomer") + "@example.test";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertEquals(200, registration.getStatusCodeValue(), registration.getBody());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, customer.getPassword()), jsonHeaders()),
				AuthenticationResponse.class);
		assertEquals(200, login.getStatusCodeValue());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		return new UatCustomer(email, customer.getPassword(), login.getBody().getToken());
	}

	protected UatProduct createUatProduct(String prefix) {
		String sku = uniqueCode(prefix);
		String token = adminToken();
		PersistableCategory category = new PersistableCategory();
		category.setCode(sku);
		category.setSortOrder(1);
		category.setVisible(true);
		category.setDepth(4);
		category.setParent(new Category());
		CategoryDescription description = new CategoryDescription();
		description.setLanguage("en");
		description.setName(sku);
		description.setFriendlyUrl(sku);
		description.setTitle(sku);
		category.setDescriptions(Collections.singletonList(description));

		ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(category, bearerHeaders(token)), PersistableCategory.class);
		assertEquals(201, categoryResponse.getStatusCodeValue(), categoryResponse.toString());
		assertNotNull(categoryResponse.getBody());

		PersistableProduct product = product(sku, sku, BigDecimal.TEN);
		product.setCategories(new ArrayList<>(Collections.singletonList(categoryResponse.getBody())));
		ResponseEntity<Entity> productResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(product, bearerHeaders(token)), Entity.class);
		assertEquals(201, productResponse.getStatusCodeValue(), productResponse.toString());
		assertNotNull(productResponse.getBody());
		assertNotNull(productResponse.getBody().getId());
		return new UatProduct(sku, productResponse.getBody().getId(), token);
	}

	protected PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	protected void enableMoneyOrder(String token) {
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/modules/payment",
				HttpMethod.POST,
				new HttpEntity<>("{\"code\":\"moneyorder\",\"active\":true,"
						+ "\"defaultSelected\":true,\"integrationKeys\":{\"address\":\"UAT test address\","
						+ "\"transaction\":\"AUTHORIZECAPTURE\"}}", bearerHeaders(token)),
				Void.class);
		assertEquals(200, response.getStatusCodeValue(), response.toString());
	}

	protected static final class UatCustomer {
		final String email;
		final String password;
		final String token;

		UatCustomer(String email, String password, String token) {
			this.email = email;
			this.password = password;
			this.token = token;
		}
	}

	protected static final class UatProduct {
		final String sku;
		final Long id;
		final String adminToken;

		UatProduct(String sku, Long id, String adminToken) {
			this.sku = sku;
			this.id = id;
			this.adminToken = adminToken;
		}
	}
}
