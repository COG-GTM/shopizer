package com.salesmanager.test.shop.integration.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.catalog.PersistableCatalog;
import com.salesmanager.shop.model.catalog.catalog.PersistableCatalogCategoryEntry;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.entity.ReadableEntityList;
import com.salesmanager.shop.model.catalog.catalog.ReadableCatalog;
import com.salesmanager.shop.model.catalog.catalog.ReadableCatalogCategoryEntry;
import com.salesmanager.shop.model.catalog.manufacturer.ReadableManufacturer;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ZeroIdCreateApiIntegrationTest extends ServicesTestSupport {

	@Test
	public void createsAndReadsZeroIdEntitiesThroughAdminApi() {
		String suffix = Long.toString(System.nanoTime());
		HttpEntity<?> adminRequest = new HttpEntity<>(getHeader());

		PersistableCategory category = category("zero-cat-" + suffix);
		category.setId(0L);
		ResponseEntity<PersistableCategory> firstCategory = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=DEFAULT", new HttpEntity<>(category, getHeader()),
				PersistableCategory.class);
		assertEquals(CREATED, firstCategory.getStatusCode());
		assertNotNull(firstCategory.getBody());
		assertTrue(firstCategory.getBody().getId() > 0);

		PersistableCategory secondCategoryPayload = category("zero-cat-second-" + suffix);
		secondCategoryPayload.setId(0L);
		ResponseEntity<PersistableCategory> secondCategory = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=DEFAULT", new HttpEntity<>(secondCategoryPayload, getHeader()),
				PersistableCategory.class);
		assertEquals(CREATED, secondCategory.getStatusCode());
		assertNotNull(secondCategory.getBody());
		assertTrue(secondCategory.getBody().getId() > 0);
		assertNotEquals(firstCategory.getBody().getId(), secondCategory.getBody().getId());
		JsonNode categories = testRestTemplate.exchange("/api/v1/category?store=DEFAULT", GET,
				adminRequest, JsonNode.class).getBody();
		assertNotNull(categories);
		assertTrue(categories.toString().contains(category.getCode()));
		assertTrue(categories.toString().contains(secondCategoryPayload.getCode()));

		String sku = "zero-sku-" + suffix;
		PersistableProduct product = product(sku);
		product.setId(0L);
		product.setSku(sku);
		product.setAvailable(true);
		product.setPrice(java.math.BigDecimal.TEN);
		product.setQuantity(5);
		product.setCategories(new ArrayList<Category>());
		ProductSpecification specification = new ProductSpecification();
		specification.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specification);
		ResponseEntity<Entity> createdProduct = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=DEFAULT", new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(CREATED, createdProduct.getStatusCode());
		assertNotNull(createdProduct.getBody());
		assertTrue(createdProduct.getBody().getId() > 0);
		assertEquals(OK, testRestTemplate.exchange("/api/v2/private/product/" + createdProduct.getBody().getId(),
				GET, adminRequest, ReadableProduct.class).getStatusCode());
		assertEquals(OK, testRestTemplate.exchange("/api/v2/product/" + sku, GET, adminRequest,
				ReadableProduct.class).getStatusCode());

		PersistableCustomer customer = new PersistableCustomer();
		customer.setId(0L);
		customer.setEmailAddress("zero-customer-" + suffix + "@example.com");
		customer.setPassword("clear123");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		customer.setFirstName("Zero");
		customer.setLastName("Customer");
		Address billing = new Address();
		billing.setFirstName("Zero");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<ReadableCustomer> createdCustomer = testRestTemplate.postForEntity(
				"/api/v1/private/customer?store=DEFAULT", new HttpEntity<>(customer, getHeader()),
				ReadableCustomer.class);
		assertEquals(OK, createdCustomer.getStatusCode());
		assertNotNull(createdCustomer.getBody());
		assertTrue(createdCustomer.getBody().getId() > 0);
		assertEquals(OK, testRestTemplate.exchange(
				"/api/v1/private/customer/" + createdCustomer.getBody().getId() + "?store=DEFAULT",
				GET, adminRequest, ReadableCustomer.class).getStatusCode());

		PersistableManufacturer manufacturer = manufacturer("zero-manufacturer-" + suffix);
		manufacturer.setId(0L);
		ResponseEntity<PersistableManufacturer> createdManufacturer = testRestTemplate.postForEntity(
				"/api/v1/private/manufacturer?store=DEFAULT", new HttpEntity<>(manufacturer, getHeader()),
				PersistableManufacturer.class);
		assertEquals(CREATED, createdManufacturer.getStatusCode());
		assertNotNull(createdManufacturer.getBody());
		assertTrue(createdManufacturer.getBody().getId() > 0);
		assertEquals(OK, testRestTemplate.exchange(
				"/api/v1/manufacturer/" + createdManufacturer.getBody().getId() + "?store=DEFAULT",
				GET, adminRequest, ReadableManufacturer.class).getStatusCode());

		PersistableCatalog catalog = new PersistableCatalog();
		catalog.setId(0L);
		catalog.setCode("zero-catalog-" + suffix);
		catalog.setVisible(true);
		ResponseEntity<ReadableCatalog> createdCatalog = testRestTemplate.postForEntity(
				"/api/v1/private/catalog?store=DEFAULT", new HttpEntity<>(catalog, getHeader()),
				ReadableCatalog.class);
		assertEquals(OK, createdCatalog.getStatusCode());
		assertNotNull(createdCatalog.getBody());
		assertTrue(createdCatalog.getBody().getId() > 0);
		assertEquals(OK, testRestTemplate.exchange("/api/v1/private/catalog/" + createdCatalog.getBody().getId()
				+ "?store=DEFAULT", GET, adminRequest, ReadableCatalog.class).getStatusCode());

		PersistableCatalogCategoryEntry entry = new PersistableCatalogCategoryEntry();
		entry.setId(0L);
		entry.setCategoryCode(category.getCode());
		entry.setProductCode(sku);
		entry.setVisible(true);
		ResponseEntity<ReadableCatalogCategoryEntry> createdEntry = testRestTemplate.postForEntity(
				"/api/v1/private/catalog/" + createdCatalog.getBody().getId() + "?store=DEFAULT",
				new HttpEntity<>(entry, getHeader()), ReadableCatalogCategoryEntry.class);
		assertEquals(OK, createdEntry.getStatusCode());
		assertNotNull(createdEntry.getBody());
		assertTrue(createdEntry.getBody().getId() > 0);
		ResponseEntity<JsonNode> entries = testRestTemplate.exchange(
				"/api/v1/private/catalog/" + createdCatalog.getBody().getId() + "/entry?store=DEFAULT",
				GET, adminRequest, JsonNode.class);
		assertEquals(OK, entries.getStatusCode());
		assertNotNull(entries.getBody());
		assertTrue(entries.getBody().toString().contains(category.getCode()));
	}
}
