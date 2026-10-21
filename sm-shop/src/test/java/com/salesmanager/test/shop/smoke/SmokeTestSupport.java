package com.salesmanager.test.shop.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.ProductDescription;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProductInventory;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.security.ReadableGroup;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public abstract class SmokeTestSupport {

	@Autowired
	protected TestRestTemplate testRestTemplate;

	protected String uniqueCode(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	protected String adminToken() {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(200, response.getStatusCodeValue());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getToken());
		return response.getBody().getToken();
	}

	protected CustomerSession registerCustomer() {
		String email = uniqueCode("customer") + "@example.test";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Smoke");
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
		return new CustomerSession(email, login.getBody().getToken());
	}

	protected ProductFixture createProduct(String prefix) {
		String code = uniqueCode(prefix);
		String token = adminToken();
		PersistableCategory category = new PersistableCategory();
		category.setCode(code);
		category.setSortOrder(1);
		category.setVisible(true);
		category.setDepth(4);
		category.setParent(new Category());
		CategoryDescription categoryDescription = new CategoryDescription();
		categoryDescription.setLanguage("en");
		categoryDescription.setName(code);
		categoryDescription.setFriendlyUrl(code);
		categoryDescription.setTitle(code);
		category.setDescriptions(Collections.singletonList(categoryDescription));

		ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(category, bearerHeaders(token)), PersistableCategory.class);
		assertEquals(201, categoryResponse.getStatusCodeValue(), categoryResponse.toString());
		assertNotNull(categoryResponse.getBody());
		assertNotNull(categoryResponse.getBody().getId());

		PersistableProduct product = product(code, code, BigDecimal.TEN);
		product.setCategories(new ArrayList<>(Collections.singletonList(categoryResponse.getBody())));
		ResponseEntity<Entity> productResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(product, bearerHeaders(token)), Entity.class);
		assertEquals(201, productResponse.getStatusCodeValue(), productResponse.toString());
		assertNotNull(productResponse.getBody());
		assertNotNull(productResponse.getBody().getId());
		return new ProductFixture(product, productResponse.getBody().getId(), token);
	}

	protected PersistableProduct product(String sku, String name, BigDecimal price) {
		PersistableProduct product = new PersistableProduct();
		product.setSku(sku);
		product.setPrice(price);
		product.setQuantity(100);
		product.setAvailable(true);
		ProductSpecification specification = new ProductSpecification();
		specification.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specification);

		ProductDescription description = new ProductDescription();
		description.setName(name);
		description.setLanguage("en");
		description.setFriendlyUrl(sku);
		product.getDescriptions().add(description);

		PersistableProductPrice productPrice = new PersistableProductPrice();
		productPrice.setDefaultPrice(true);
		productPrice.setPrice(price);
		productPrice.setDiscountedPrice(price);
		PersistableProductInventory inventory = new PersistableProductInventory();
		inventory.setSku(sku);
		inventory.setQuantity(100);
		inventory.setPrice(productPrice);
		product.setInventory(inventory);
		return product;
	}

	protected HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

	protected HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		return headers;
	}

	protected static final class CustomerSession {
		final String email;
		final String token;

		CustomerSession(String email, String token) {
			this.email = email;
			this.token = token;
		}
	}

	protected static final class ProductFixture {
		final PersistableProduct product;
		final Long id;
		final String adminToken;

		ProductFixture(PersistableProduct product, Long id, String adminToken) {
			this.product = product;
			this.id = id;
			this.adminToken = adminToken;
		}
	}
}
