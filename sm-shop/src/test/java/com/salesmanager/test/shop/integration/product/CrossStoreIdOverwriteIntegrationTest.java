package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOption;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.catalog.product.product.variant.PersistableProductVariant;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CrossStoreIdOverwriteIntegrationTest extends ServicesTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	public void clientIdsCannotOverwriteVariationsAcrossStores() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String storeB = createSecondStore(suffix);

		long optionB = createOption(storeB, "b-option-" + suffix);
		long valueB = createOptionValue(storeB, "b-value-" + suffix);
		long variationB = createVariation(storeB, null, "b-variation-" + suffix, optionB, valueB);
		long variationDefault = createVariation("DEFAULT", null, "d-variation-" + suffix,
				createOption("DEFAULT", "d-option-" + suffix), createOptionValue("DEFAULT", "d-value-" + suffix));

		verifyVariationIds(storeB, variationB, variationDefault, suffix);
	}

	@Test
	public void clientIdsCannotOverwriteOptionSetsAcrossStores() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String storeB = createSecondStore(suffix);
		long optionB = createOption(storeB, "b-option-" + suffix);
		long valueB = createOptionValue(storeB, "b-value-" + suffix);
		verifyOptionSetIds(storeB, optionB, valueB, suffix);
	}

	@Test
	public void clientIdsCannotOverwriteVariantGroupsAcrossStores() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String storeB = createSecondStore(suffix);
		long optionB = createOption(storeB, "b-option-" + suffix);
		long valueB = createOptionValue(storeB, "b-value-" + suffix);
		long variationB = createVariation(storeB, null, "b-variation-" + suffix, optionB, valueB);
		long variationDefault = createVariation("DEFAULT", null, "d-variation-" + suffix,
				createOption("DEFAULT", "d-option-" + suffix), createOptionValue("DEFAULT", "d-value-" + suffix));
		verifyVariantGroupIds(storeB, variationB, variationDefault, suffix);
	}

	private String createSecondStore(String suffix) {
		String storeB = "P301" + suffix.toUpperCase();
		createStore(storeB);
		return storeB;
	}

	private void verifyVariationIds(String storeB, long idB, long idDefault, String suffix) {
		long createdId = createVariation("DEFAULT", idB, "d-created-variation-" + suffix,
				createOption("DEFAULT", "d-create-option-" + suffix),
				createOptionValue("DEFAULT", "d-create-value-" + suffix));
		assertNotEquals(idB, createdId);
		assertEquals("b-variation-" + suffix, getVariation(storeB, idB).path("code").asText());
		assertEquals("d-created-variation-" + suffix, getVariation("DEFAULT", createdId).path("code").asText());

		updateVariation("DEFAULT", idDefault, idB, "d-updated-variation-" + suffix);
		assertEquals("d-updated-variation-" + suffix, getVariation("DEFAULT", idDefault).path("code").asText());
		assertEquals("b-variation-" + suffix, getVariation(storeB, idB).path("code").asText());

		long sameStoreId = createVariation("DEFAULT", idDefault, "d-same-store-variation-" + suffix,
				createOption("DEFAULT", "d-same-option-" + suffix),
				createOptionValue("DEFAULT", "d-same-value-" + suffix));
		assertNotEquals(idDefault, sameStoreId);
		assertEquals("d-updated-variation-" + suffix, getVariation("DEFAULT", idDefault).path("code").asText());
	}

	private void verifyOptionSetIds(String storeB, long optionB, long valueB, String suffix) throws Exception {
		long setB = createOptionSet(storeB, null, "b-set-" + suffix, optionB, valueB);
		long setDefault = createOptionSet("DEFAULT", null, "d-set-" + suffix,
				createOption("DEFAULT", "d-set-option-" + suffix),
				createOptionValue("DEFAULT", "d-set-value-" + suffix));
		long createdId = createOptionSet("DEFAULT", setB, "d-created-set-" + suffix,
				createOption("DEFAULT", "d-create-set-option-" + suffix),
				createOptionValue("DEFAULT", "d-create-set-value-" + suffix));
		assertNotEquals(setB, createdId);
		assertEquals("b-set-" + suffix, getOptionSet(storeB, setB).path("code").asText());
		assertEquals("d-created-set-" + suffix, getOptionSet("DEFAULT", createdId).path("code").asText());

		updateOptionSet("DEFAULT", setDefault, setB, "d-updated-set-" + suffix);
		assertEquals(setDefault, getOptionSet("DEFAULT", setDefault).path("id").asLong());
		assertEquals("b-set-" + suffix, getOptionSet(storeB, setB).path("code").asText());

		long sameStoreId = createOptionSet("DEFAULT", setDefault, "d-same-store-set-" + suffix,
				createOption("DEFAULT", "d-same-set-option-" + suffix),
				createOptionValue("DEFAULT", "d-same-set-value-" + suffix));
		assertNotEquals(setDefault, sameStoreId);
		assertEquals("d-set-" + suffix, getOptionSet("DEFAULT", setDefault).path("code").asText());
	}

	private void verifyVariantGroupIds(String storeB, long variationB, long variationDefault, String suffix)
			throws Exception {
		long productB = createProduct(storeB, "b-product-" + suffix);
		long variantB = createVariant(storeB, productB, variationB, "b-variant-" + suffix);
		long groupB = createGroup(storeB, null, "b-group-" + suffix, variantB);
		long productDefault = createProduct("DEFAULT", "d-product-" + suffix);
		long variantDefault = createVariant("DEFAULT", productDefault, variationDefault, "d-variant-" + suffix);
		long groupDefault = createGroup("DEFAULT", null, "d-group-" + suffix, variantDefault);

		long createdGroup = createGroup("DEFAULT", groupB, "d-created-group-" + suffix, variantDefault);
		assertNotEquals(groupB, createdGroup);
		assertEquals(groupB, getGroup(storeB, groupB).path("id").asLong());
		assertTrue(containsVariant(getGroup(storeB, groupB), variantB));
		assertEquals(createdGroup, getGroup("DEFAULT", createdGroup).path("id").asLong());
		assertTrue(containsVariant(getGroup("DEFAULT", createdGroup), variantDefault));

		updateGroup("DEFAULT", groupDefault, groupB, "d-updated-group-" + suffix, variantDefault);
		assertEquals(groupDefault, getGroup("DEFAULT", groupDefault).path("id").asLong());
		assertEquals(groupB, getGroup(storeB, groupB).path("id").asLong());
		assertTrue(containsVariant(getGroup(storeB, groupB), variantB));

		long sameStoreVariant = createVariant("DEFAULT", productDefault, variationDefault,
				"d-same-variant-" + suffix);
		long sameStoreGroup = createGroup("DEFAULT", groupDefault, "d-same-group-" + suffix, sameStoreVariant);
		assertNotEquals(groupDefault, sameStoreGroup);
		assertEquals(groupDefault, getGroup("DEFAULT", groupDefault).path("id").asLong());
		assertTrue(containsVariant(getGroup("DEFAULT", groupDefault), variantDefault));
	}

	private void createStore(String code) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Test Street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("CA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code.toLowerCase() + "@example.com");
		store.setName(code);
		store.setPhone("555-0100");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(OK, response.getStatusCode());
	}

	private long createOption(String store, String code) {
		PersistableProductOption option = new PersistableProductOption();
		option.setCode(code);
		option.setType("radio");
		ProductOptionDescription description = new ProductOptionDescription();
		description.setLanguage("en");
		description.setName(code);
		option.setDescriptions(Arrays.asList(description));
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product/option?store=" + store,
				new HttpEntity<>(option, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private long createOptionValue(String store, String code) {
		PersistableProductOptionValue value = new PersistableProductOptionValue();
		value.setCode(code);
		ProductOptionValueDescription description = new ProductOptionValueDescription();
		description.setLanguage("en");
		description.setName(code);
		value.setDescriptions(Arrays.asList(description));
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product/option/value?store=" + store,
				new HttpEntity<>(value, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private long createVariation(String store, Long suppliedId, String code, long option, long value) {
		PersistableProductVariation variation = new PersistableProductVariation();
		variation.setId(suppliedId);
		variation.setCode(code);
		variation.setOption(option);
		variation.setOptionValue(value);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v2/private/product/variation?store=" + store,
				new HttpEntity<>(variation, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private JsonNode getVariation(String store, long id) {
		return testRestTemplate.exchange("/api/v2/private/product/variation/" + id + "?store=" + store,
				HttpMethod.GET, new HttpEntity<>(getHeader()), JsonNode.class).getBody();
	}

	private void updateVariation(String store, long id, Long suppliedId, String code) {
		JsonNode existing = getVariation(store, id);
		PersistableProductVariation variation = new PersistableProductVariation();
		variation.setId(suppliedId);
		variation.setCode(code);
		variation.setOption(existing.path("option").path("id").asLong());
		variation.setOptionValue(existing.path("optionValue").path("id").asLong());
		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v2/private/product/variation/" + id + "?store=" + store, HttpMethod.PUT,
				new HttpEntity<>(variation, getHeader()), Void.class);
		assertEquals(OK, response.getStatusCode());
	}

	private long createOptionSet(String store, Long suppliedId, String code, long option, long value) throws Exception {
		PersistableProductOptionSet optionSet = new PersistableProductOptionSet();
		optionSet.setId(suppliedId);
		optionSet.setCode(code);
		optionSet.setOption(option);
		optionSet.setOptionValues(Arrays.asList(value));
		String path = "/api/v1/private/product/property/set?store=" + store;
		ResponseEntity<Void> response = testRestTemplate.postForEntity(path,
				new HttpEntity<>(optionSet, getHeader()), Void.class);
		assertEquals(CREATED, response.getStatusCode());
		return findOptionSetId(store, code);
	}

	private long findOptionSetId(String store, String code) throws Exception {
		JsonNode list = testRestTemplate.exchange("/api/v1/private/product/property/set?store=" + store,
				HttpMethod.GET, new HttpEntity<>(getHeader()), JsonNode.class).getBody();
		for (JsonNode item : list) {
			if (code.equals(item.path("code").asText())) {
				return item.path("id").asLong();
			}
		}
		throw new AssertionError("Option set was not listed: " + code);
	}

	private JsonNode getOptionSet(String store, long id) {
		return testRestTemplate.exchange("/api/v1/private/product/property/set/" + id + "?store=" + store,
				HttpMethod.GET, new HttpEntity<>(getHeader()), JsonNode.class).getBody();
	}

	private void updateOptionSet(String store, long id, Long suppliedId, String code) {
		JsonNode existing = getOptionSet(store, id);
		PersistableProductOptionSet optionSet = new PersistableProductOptionSet();
		optionSet.setId(suppliedId);
		optionSet.setCode(code);
		optionSet.setOption(existing.path("option").path("id").asLong());
		List<Long> values = new ArrayList<>();
		for (JsonNode value : existing.path("values")) {
			values.add(value.path("id").asLong());
		}
		optionSet.setOptionValues(values);
		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v1/private/product/property/set/" + id + "?store=" + store, HttpMethod.PUT,
				new HttpEntity<>(optionSet, getHeader()), Void.class);
		assertEquals(OK, response.getStatusCode());
	}

	private long createProduct(String store, String code) {
		PersistableCategory category = category(code + "-category");
		ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + store,
				new HttpEntity<>(category, getHeader()), PersistableCategory.class);
		assertEquals(CREATED, categoryResponse.getStatusCode());
		assertNotNull(categoryResponse.getBody());

		PersistableProduct product = product(code);
		List<Category> categories = new ArrayList<>();
		categories.add(categoryResponse.getBody());
		product.setCategories(categories);
		ProductSpecification specification = new ProductSpecification();
		String manufacturerCode = "DEFAULT";
		if (!"DEFAULT".equals(store)) {
			manufacturerCode = code + "-brand";
			PersistableManufacturer manufacturer = manufacturer(manufacturerCode);
			ResponseEntity<PersistableManufacturer> manufacturerResponse = testRestTemplate.postForEntity(
					"/api/v1/private/manufacturer?store=" + store,
					new HttpEntity<>(manufacturer, getHeader()), PersistableManufacturer.class);
			assertEquals(CREATED, manufacturerResponse.getStatusCode());
		}
		specification.setManufacturer(manufacturerCode);
		product.setProductSpecifications(specification);
		product.setAvailable(true);
		product.setPrice(BigDecimal.TEN);
		product.setSku(code);
		product.setQuantity(100);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + store, new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private long createVariant(String store, long productId, long variationId, String sku) {
		PersistableProductVariant variant = new PersistableProductVariant();
		variant.setSku(sku);
		variant.setVariation(variationId);
		variant.setAvailable(true);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v2/private/product/" + productId + "/variant?store=" + store,
				new HttpEntity<>(variant, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private long createGroup(String store, Long suppliedId, String code, long variantId) throws Exception {
		ObjectNode group = JSON.createObjectNode();
		if (suppliedId != null) {
			group.put("id", suppliedId);
		}
		group.put("code", code);
		ArrayNode variants = group.putArray("productVariants");
		variants.add(variantId);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v2/private/product/productVariantGroup?store=" + store,
				new HttpEntity<>(group, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private void updateGroup(String store, long id, Long suppliedId, String code, long variantId) {
		ObjectNode group = JSON.createObjectNode();
		if (suppliedId != null) {
			group.put("id", suppliedId);
		}
		group.put("code", code);
		group.putArray("productVariants").add(variantId);
		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v2/private/product/productVariantGroup/" + id + "?store=" + store, HttpMethod.PUT,
				new HttpEntity<>(group, getHeader()), Void.class);
		assertEquals(OK, response.getStatusCode());
	}

	private JsonNode getGroup(String store, long id) {
		return testRestTemplate.exchange("/api/v2/private/product/productVariantGroup/" + id + "?store=" + store,
				HttpMethod.GET, new HttpEntity<>(getHeader()), JsonNode.class).getBody();
	}

	private boolean containsVariant(JsonNode group, long id) {
		for (JsonNode variant : group.path("productVariants")) {
			if (variant.path("id").asLong() == id) {
				return true;
			}
		}
		return false;
	}
}
