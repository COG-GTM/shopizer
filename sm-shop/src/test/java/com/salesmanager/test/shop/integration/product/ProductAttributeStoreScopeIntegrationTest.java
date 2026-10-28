package com.salesmanager.test.shop.integration.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductAttribute;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.shop.model.catalog.product.attribute.ProductPropertyOption;
import com.salesmanager.shop.model.catalog.product.attribute.api.PersistableProductOptionEntity;
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductAttributeEntity;
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductAttributeList;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class ProductAttributeStoreScopeIntegrationTest extends ServicesTestSupport {

	private String storeCode;
	private String suffix;
	private String defaultOptionCode;
	private String otherOptionCode;
	private String otherManufacturerCode;
	private String defaultValueOne;
	private String defaultValueTwo;
	private String otherValue;
	private Long otherOptionId;
	private Long otherValueId;
	private Long defaultSingleProduct;
	private Long defaultBulkProduct;
	private Long defaultBodyProduct;
	private Long defaultIdorProduct;
	private Long defaultCrudProduct;
	private Long defaultUpdateProduct;
	private Long otherSingleProduct;
	private Long otherBulkProduct;
	private Long otherUpdateProduct;
	private Long otherBodyProduct;
	private Long otherStoreId;
	private Long defaultCategoryId;
	private Long otherCategoryId;
	private Long updateAttributeId;
	private BigDecimal originalUpdatePrice = new BigDecimal("5.00");

	@BeforeAll
	void createStoreAndFixtures() {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		storeCode = "scope" + suffix;
		defaultOptionCode = "color" + suffix;
		otherOptionCode = "size" + suffix;
		otherManufacturerCode = "brand" + suffix;
		defaultValueOne = "red" + suffix;
		defaultValueTwo = "blue" + suffix;
		otherValue = "large" + suffix;

		createStore();
		createManufacturer(otherManufacturerCode, storeCode);
		defaultCategoryId = createCategory(storeCode + "DefaultCategory", "DEFAULT");
		otherCategoryId = createCategory(storeCode + "OtherCategory", storeCode);
		Long defaultOptionId = createOption(defaultOptionCode, "DEFAULT");
		createOptionValue(defaultValueOne, "DEFAULT");
		createOptionValue(defaultValueTwo, "DEFAULT");
		otherOptionId = createOption(otherOptionCode, storeCode);
		otherValueId = createOptionValue(otherValue, storeCode);

		defaultSingleProduct = createProduct(storeCode + "D1", defaultCategoryId, "DEFAULT");
		defaultBulkProduct = createProduct(storeCode + "D2", defaultCategoryId, "DEFAULT");
		defaultBodyProduct = createProduct(storeCode + "D3", defaultCategoryId, "DEFAULT");
		defaultIdorProduct = createProduct(storeCode + "D4", defaultCategoryId, "DEFAULT");
		defaultCrudProduct = createProduct(storeCode + "D5", defaultCategoryId, "DEFAULT");
		defaultUpdateProduct = createProduct(storeCode + "D6", defaultCategoryId, "DEFAULT");
		otherSingleProduct = createProduct(storeCode + "B1", otherCategoryId, storeCode);
		otherBulkProduct = createProduct(storeCode + "B2", otherCategoryId, storeCode);
		otherUpdateProduct = createProduct(storeCode + "B3", otherCategoryId, storeCode);
		otherBodyProduct = createProduct(storeCode + "B4", otherCategoryId, storeCode);
		assertNotNull(defaultOptionId);
	}

	@Test
	@Order(1)
	void singleAttributeCannotBePostedToAnotherStoresProduct() {
		ResponseEntity<Entity> response = postAttribute(otherSingleProduct, "DEFAULT",
				attribute(defaultOptionCode, defaultValueOne, null, new BigDecimal("1.25")));
		ReadableProductAttributeList otherAttributes = attributes(otherSingleProduct, storeCode);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertEquals(0, otherAttributes.getAttributes().size());
	}

	@Test
	@Order(2)
	void bulkAttributesCannotBePostedToAnotherStoresProduct() {
		List<PersistableProductAttribute> request = Arrays.asList(
				attribute(defaultOptionCode, defaultValueOne, null, BigDecimal.ONE),
				attribute(defaultOptionCode, defaultValueTwo, null, BigDecimal.TEN));
		ResponseEntity<String> response = exchange(
				"/api/v1/private/product/" + otherBulkProduct + "/attributes?store=DEFAULT",
				HttpMethod.POST, request, String.class);
		ReadableProductAttributeList otherAttributes = attributes(otherBulkProduct, storeCode);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertEquals(0, otherAttributes.getAttributes().size());
	}

	@Test
	@Order(3)
	void foreignStoreCannotUpdateAttributeOrTargetItThroughAnotherProduct() {
		ResponseEntity<Entity> created = postAttribute(otherUpdateProduct, storeCode,
				attribute(otherOptionCode, otherValue, null, originalUpdatePrice));
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		updateAttributeId = created.getBody().getId();

		ResponseEntity<Void> update = exchange(
				"/api/v1/private/product/" + otherUpdateProduct + "/attribute/" + updateAttributeId
						+ "?store=DEFAULT",
				HttpMethod.PUT, attribute(defaultOptionCode, defaultValueOne, null, new BigDecimal("99.00")),
				Void.class);
		ReadableProductAttributeEntity persisted = getAttribute(otherUpdateProduct, updateAttributeId, storeCode);
		ResponseEntity<Void> crossProductUpdate = exchange(
				"/api/v1/private/product/" + defaultUpdateProduct + "/attribute/" + updateAttributeId
						+ "?store=DEFAULT",
				HttpMethod.PUT, attribute(defaultOptionCode, defaultValueOne, null, new BigDecimal("88.00")),
				Void.class);
		ReadableProductAttributeEntity persistedAfterCrossProduct =
				getAttribute(otherUpdateProduct, updateAttributeId, storeCode);

		assertEquals(HttpStatus.NOT_FOUND, update.getStatusCode());
		assertEquals(0, new BigDecimal(persisted.getProductAttributeUnformattedPrice())
				.compareTo(originalUpdatePrice));
		assertEquals(otherOptionCode, persisted.getOption().getCode());
		assertEquals(HttpStatus.NOT_FOUND, crossProductUpdate.getStatusCode());
		assertEquals(0, new BigDecimal(persistedAfterCrossProduct.getProductAttributeUnformattedPrice())
				.compareTo(originalUpdatePrice));
	}

	@Test
	@Order(4)
	void singleAttributeIgnoresBodyProductIdAndUsesUrlProduct() {
		ReadableProductAttributeList otherBefore = attributes(otherBodyProduct, storeCode);
		ResponseEntity<Entity> response = postAttribute(defaultBodyProduct, "DEFAULT",
				attribute(defaultOptionCode, defaultValueOne, otherBodyProduct, BigDecimal.ONE));
		ReadableProductAttributeList defaultAfter = attributes(defaultBodyProduct, "DEFAULT");
		ReadableProductAttributeList otherAfter = attributes(otherBodyProduct, storeCode);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertEquals(1, defaultAfter.getAttributes().size());
		assertEquals(otherBefore.getAttributes().size(), otherAfter.getAttributes().size());
	}

	@Test
	@Order(5)
	void optionIdFromAnotherStoreIsRejected() {
		PersistableProductAttribute request = attribute(defaultOptionCode, defaultValueOne, null, BigDecimal.ONE);
		request.getOption().setCode("");
		request.getOption().setId(otherOptionId);

		ResponseEntity<Entity> response = postAttribute(defaultIdorProduct, "DEFAULT", request);
		ReadableProductAttributeList persisted = attributes(defaultIdorProduct, "DEFAULT");

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(0, persisted.getAttributes().size());
		assertNotNull(otherValueId);
	}

	@Test
	@Order(6)
	void ownStoreAttributeSupportsCreateReadUpdateDelete() {
		ResponseEntity<Entity> created = postAttribute(defaultCrudProduct, "DEFAULT",
				attribute(defaultOptionCode, defaultValueOne, null, new BigDecimal("2.00")));
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		Long attributeId = created.getBody().getId();

		ResponseEntity<ReadableProductAttributeEntity> read =
				exchange("/api/v1/private/product/" + defaultCrudProduct + "/attribute/" + attributeId
						+ "?store=DEFAULT", HttpMethod.GET, null, ReadableProductAttributeEntity.class);
		ResponseEntity<Void> updated = exchange(
				"/api/v1/private/product/" + defaultCrudProduct + "/attribute/" + attributeId + "?store=DEFAULT",
				HttpMethod.PUT, attribute(defaultOptionCode, defaultValueOne, null, new BigDecimal("3.00")),
				Void.class);
		ReadableProductAttributeEntity updatedValue =
				getAttribute(defaultCrudProduct, attributeId, "DEFAULT");
		ResponseEntity<Void> deleted = exchange(
				"/api/v1/private/product/" + defaultCrudProduct + "/attribute/" + attributeId + "?store=DEFAULT",
				HttpMethod.DELETE, null, Void.class);
		ResponseEntity<String> afterDelete = exchange(
				"/api/v1/private/product/" + defaultCrudProduct + "/attribute/" + attributeId + "?store=DEFAULT",
				HttpMethod.GET, null, String.class);

		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(0, new BigDecimal(read.getBody().getProductAttributeUnformattedPrice())
				.compareTo(new BigDecimal("2.00")));
		assertEquals(HttpStatus.OK, updated.getStatusCode());
		assertEquals(0, new BigDecimal(updatedValue.getProductAttributeUnformattedPrice())
				.compareTo(new BigDecimal("3.00")));
		assertEquals(HttpStatus.OK, deleted.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, afterDelete.getStatusCode());
	}

	@Test
	@Order(7)
	void ownStoreBulkAttributeCreatePersistsAttributes() {
		List<PersistableProductAttribute> request = Arrays.asList(
				attribute(defaultOptionCode, defaultValueOne, null, BigDecimal.ONE),
				attribute(defaultOptionCode, defaultValueTwo, null, BigDecimal.TEN));
		exchange(
				"/api/v1/private/product/" + defaultBulkProduct + "/attributes?store=DEFAULT",
				HttpMethod.POST, request, String.class);
		ReadableProductAttributeList persisted = attributes(defaultBulkProduct, "DEFAULT");

		assertEquals(2, persisted.getAttributes().size());
		assertTrue(persisted.getAttributes().stream().allMatch(attribute ->
				attribute.getId() != null && defaultOptionCode.equals(attribute.getOption().getCode())));
	}

	private void createStore() {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("12 test address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(storeCode);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail(storeCode + "@example.com");
		store.setName(storeCode);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		ResponseEntity<Void> response =
				testRestTemplate.postForEntity("/api/v1/private/store", new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private void createManufacturer(String code, String store) {
		PersistableManufacturer manufacturer = manufacturer(code);
		ResponseEntity<PersistableManufacturer> response = testRestTemplate.postForEntity(
				"/api/v1/private/manufacturer?store=" + store,
				new HttpEntity<>(manufacturer, getHeader()), PersistableManufacturer.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
	}

	private Long createCategory(String code, String store) {
		ResponseEntity<PersistableCategory> response = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + store,
				new HttpEntity<>(category(code), getHeader()), PersistableCategory.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		return response.getBody().getId();
	}

	private Long createOption(String code, String store) {
		PersistableProductOptionEntity option = new PersistableProductOptionEntity();
		option.setCode(code);
		option.setType("select");
		ProductOptionDescription description = new ProductOptionDescription();
		description.setLanguage("en");
		description.setName(code);
		option.getDescriptions().add(description);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product/option?store=" + store,
				new HttpEntity<>(option, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		return response.getBody().getId();
	}

	private Long createOptionValue(String code, String store) {
		PersistableProductOptionValue optionValue = new PersistableProductOptionValue();
		optionValue.setCode(code);
		ProductOptionValueDescription description = new ProductOptionValueDescription();
		description.setLanguage("en");
		description.setName(code);
		optionValue.getDescriptions().add(description);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product/option/value?store=" + store,
				new HttpEntity<>(optionValue, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		return response.getBody().getId();
	}

	private Long createProduct(String code, Long categoryId, String store) {
		PersistableProduct product = product(code);
		PersistableCategory category = new PersistableCategory();
		category.setId(categoryId);
		List<Category> categories = new ArrayList<>();
		categories.add(category);
		product.setCategories(categories);
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer("DEFAULT".equals(store)
				? Manufacturer.DEFAULT_MANUFACTURER : otherManufacturerCode);
		product.setProductSpecifications(specifications);
		product.setAvailable(true);
		product.setPrice(BigDecimal.TEN);
		product.setSku(code);
		product.setQuantity(100);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + store,
				new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.toString());
		return response.getBody().getId();
	}

	private ResponseEntity<Entity> postAttribute(Long productId, String store, PersistableProductAttribute attribute) {
		return exchange("/api/v1/private/product/" + productId + "/attribute?store=" + store,
				HttpMethod.POST, attribute, Entity.class);
	}

	private ReadableProductAttributeList attributes(Long productId, String store) {
		return exchange("/api/v1/private/product/" + productId + "/attributes?store=" + store,
				HttpMethod.GET, null, ReadableProductAttributeList.class).getBody();
	}

	private ReadableProductAttributeEntity getAttribute(Long productId, Long attributeId, String store) {
		return exchange("/api/v1/private/product/" + productId + "/attribute/" + attributeId + "?store=" + store,
				HttpMethod.GET, null, ReadableProductAttributeEntity.class).getBody();
	}

	private <T> ResponseEntity<T> exchange(String path, HttpMethod method, Object body, Class<T> responseType) {
		return testRestTemplate.exchange(path, method, new HttpEntity<>(body, getHeader()), responseType);
	}

	private static PersistableProductAttribute attribute(String optionCode, String valueCode, Long productId,
			BigDecimal price) {
		PersistableProductAttribute attribute = new PersistableProductAttribute();
		attribute.setProductId(productId);
		attribute.setProductAttributePrice(price);
		ProductPropertyOption option = new ProductPropertyOption();
		option.setCode(optionCode);
		attribute.setOption(option);
		PersistableProductOptionValue optionValue = new PersistableProductOptionValue();
		optionValue.setCode(valueCode);
		attribute.setOptionValue(optionValue);
		return attribute;
	}
}
