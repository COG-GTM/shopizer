package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.Arrays;
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
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOption;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CatalogAdminVariationJourneyUatTest extends ServicesTestSupport {

	@Test
	public void catalogOptionVariationAndPropertySetJourney() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		long optionId = createOption("uat-option-" + suffix);
		long valueId = createOptionValue("uat-value-" + suffix);

		String variationCode = "uat-variation-" + suffix;
		long variationId = createVariation(variationCode, optionId, valueId);
		JsonNode variation = get("/api/v2/private/product/variation/" + variationId + "?store=DEFAULT");
		assertEquals(variationId, variation.path("id").asLong());
		assertEquals(variationCode, variation.path("code").asText());
		assertTrue(variation.has("option"));
		assertTrue(variation.has("optionValue"));
		assertEquals(optionId, variation.path("option").path("id").asLong());
		assertEquals(valueId, variation.path("optionValue").path("id").asLong());
		assertTrue(get("/api/v2/private/product/variation/unique?code=" + variationCode + "&store=DEFAULT")
				.path("exists").asBoolean());

		PersistableProductVariation changed = variation(variationId, "uat-variation-updated-" + suffix,
				optionId, valueId);
		ResponseEntity<Void> variationUpdate = testRestTemplate.exchange(
				"/api/v2/private/product/variation/" + variationId + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(changed, getHeader()), Void.class);
		assertEquals(OK, variationUpdate.getStatusCode());
		JsonNode variations = get("/api/v2/private/product/variations?store=DEFAULT&count=100");
		assertTrue(containsCode(variations.path("items"), changed.getCode()));
		ResponseEntity<Void> variationDelete = testRestTemplate.exchange(
				"/api/v2/private/product/variation/" + variationId + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), Void.class);
		assertEquals(OK, variationDelete.getStatusCode());
		assertFalse(get("/api/v2/private/product/variation/unique?code=" + changed.getCode() + "&store=DEFAULT")
				.path("exists").asBoolean());

		String setCode = "uat-property-set-" + suffix;
		PersistableProductOptionSet optionSet = new PersistableProductOptionSet();
		optionSet.setCode(setCode);
		optionSet.setOption(optionId);
		optionSet.setOptionValues(Arrays.asList(valueId));
		ResponseEntity<Void> setCreate = testRestTemplate.postForEntity(
				"/api/v1/private/product/property/set?store=DEFAULT",
				new HttpEntity<>(optionSet, getHeader()), Void.class);
		assertEquals(CREATED, setCreate.getStatusCode());
		long setId = findOptionSetId(setCode);
		JsonNode set = get("/api/v1/private/product/property/set/" + setId + "?store=DEFAULT");
		assertEquals(setId, set.path("id").asLong());
		assertEquals(setCode, set.path("code").asText());
		assertTrue(set.has("option"));
		assertTrue(set.has("values"));
		assertEquals(optionId, set.path("option").path("id").asLong());
		assertEquals(valueId, set.path("values").get(0).path("id").asLong());
		assertTrue(get("/api/v1/private/product/property/set/unique?code=" + setCode + "&store=DEFAULT")
				.path("exists").asBoolean());

		optionSet.setId(17L);
		optionSet.setCode("uat-property-set-updated-" + suffix);
		ResponseEntity<Void> setUpdate = testRestTemplate.exchange(
				"/api/v1/private/product/property/set/" + setId + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(optionSet, getHeader()), Void.class);
		assertEquals(OK, setUpdate.getStatusCode());
		JsonNode sets = get("/api/v1/private/product/property/set?store=DEFAULT");
		assertTrue(containsCode(sets, setCode));
		assertEquals(setId, get("/api/v1/private/product/property/set/" + setId + "?store=DEFAULT")
				.path("id").asLong());

		ResponseEntity<Void> setDelete = testRestTemplate.exchange(
				"/api/v1/private/product/property/set/" + setId + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), Void.class);
		assertEquals(OK, setDelete.getStatusCode());
		assertFalse(get("/api/v1/private/product/property/set/unique?code=" + setCode + "&store=DEFAULT")
				.path("exists").asBoolean());
	}

	private long createOption(String code) {
		PersistableProductOption option = new PersistableProductOption();
		option.setCode(code);
		option.setType("radio");
		ProductOptionDescription description = new ProductOptionDescription();
		description.setLanguage("en");
		description.setName(code);
		option.setDescriptions(Arrays.asList(description));
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product/option?store=DEFAULT",
				new HttpEntity<>(option, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private long createOptionValue(String code) {
		PersistableProductOptionValue value = new PersistableProductOptionValue();
		value.setCode(code);
		ProductOptionValueDescription description = new ProductOptionValueDescription();
		description.setLanguage("en");
		description.setName(code);
		value.setDescriptions(Arrays.asList(description));
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product/option/value?store=DEFAULT",
				new HttpEntity<>(value, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private long createVariation(String code, long optionId, long valueId) {
		PersistableProductVariation variation = variation(null, code, optionId, valueId);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v2/private/product/variation?store=DEFAULT",
				new HttpEntity<>(variation, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private PersistableProductVariation variation(Long id, String code, long optionId, long valueId) {
		PersistableProductVariation variation = new PersistableProductVariation();
		variation.setId(id);
		variation.setCode(code);
		variation.setOption(optionId);
		variation.setOptionValue(valueId);
		return variation;
	}

	private long findOptionSetId(String code) {
		JsonNode sets = get("/api/v1/private/product/property/set?store=DEFAULT");
		for (JsonNode item : sets) {
			if (code.equals(item.path("code").asText())) {
				return item.path("id").asLong();
			}
		}
		throw new AssertionError("Property set was not listed: " + code);
	}

	private JsonNode get(String path) {
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(getHeader()), JsonNode.class).getBody();
	}

	private boolean containsCode(JsonNode values, String code) {
		for (JsonNode item : values) {
			if (code.equals(item.path("code").asText())) {
				return true;
			}
		}
		return false;
	}
}
