package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOption;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class VariationAdminJourneyTest extends ServicesTestSupport {

	private static final String API = "/api/v2/private/product/variation";
	private HttpHeaders headers;
	private Long optionId;
	private Long valueId;
	private String prefix;
	private final List<Long> variationIds = new ArrayList<>();
	private final List<Long> valueIds = new ArrayList<>();

	@Before
	public void createOptions() {
		headers = getHeader();
		prefix = "identity_" + UUID.randomUUID().toString().replace("-", "");
		PersistableProductOption option = new PersistableProductOption();
		option.setCode(prefix);
		ProductOptionDescription description = new ProductOptionDescription();
		description.setLanguage("en");
		description.setName("Identity option");
		option.getDescriptions().add(description);
		ResponseEntity<JsonNode> optionResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product/option", new HttpEntity<>(option, headers), JsonNode.class);
		assertEquals(201, optionResponse.getStatusCodeValue());
		optionId = optionResponse.getBody().path("id").longValue();

		PersistableProductOptionValue value = new PersistableProductOptionValue();
		value.setCode(prefix);
		ProductOptionValueDescription valueDescription = new ProductOptionValueDescription();
		valueDescription.setLanguage("en");
		valueDescription.setName("Identity value");
		value.getDescriptions().add(valueDescription);
		ResponseEntity<JsonNode> valueResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product/option/value", new HttpEntity<>(value, headers), JsonNode.class);
		assertEquals(201, valueResponse.getStatusCodeValue());
		valueId = valueResponse.getBody().path("id").longValue();
		valueIds.add(valueId);
	}

	@After
	public void cleanUp() {
		for (Long id : variationIds) {
			testRestTemplate.exchange(API + "/" + id, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
		}
		for (Long id : valueIds) {
			testRestTemplate.exchange("/api/v1/private/product/option/value/" + id,
					HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
		}
		if (optionId != null) {
			testRestTemplate.exchange("/api/v1/private/product/option/" + optionId,
					HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
		}
	}

	private PersistableProductVariation body(String suffix, Long id) {
		PersistableProductVariation result = new PersistableProductVariation();
		result.setCode(prefix + suffix);
		result.setId(id);
		result.setOption(optionId);
		result.setOptionValue(valueId);
		return result;
	}

	private long create(String suffix, Long suppliedId) {
		PersistableProductOptionValue value = new PersistableProductOptionValue();
		value.setCode(prefix + suffix);
		ProductOptionValueDescription description = new ProductOptionValueDescription();
		description.setLanguage("en");
		description.setName("Identity value " + suffix);
		value.getDescriptions().add(description);
		ResponseEntity<JsonNode> valueResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product/option/value", new HttpEntity<>(value, headers), JsonNode.class);
		assertEquals(201, valueResponse.getStatusCodeValue());
		valueId = valueResponse.getBody().path("id").longValue();
		valueIds.add(valueId);
		ResponseEntity<JsonNode> response = testRestTemplate.postForEntity(API,
				new HttpEntity<>(body(suffix, suppliedId), headers), JsonNode.class);
		assertEquals(201, response.getStatusCodeValue());
		assertTrue(response.getBody().path("id").isIntegralNumber());
		long id = response.getBody().path("id").longValue();
		variationIds.add(id);
		return id;
	}

	private JsonNode get(long id) {
		ResponseEntity<JsonNode> response = testRestTemplate.exchange(API + "/" + id,
				HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
		assertEquals(200, response.getStatusCodeValue());
		return response.getBody();
	}

	@Test
	public void adminCreatesDistinctVariationWhenBodyNamesAnExistingId() {
		long originalId = create("_original", null);
		JsonNode original = get(originalId);

		long createdId = create("_created", originalId);

		assertNotEquals(originalId, createdId);
		assertEquals(original, get(originalId));
		assertEquals(prefix + "_created", get(createdId).path("code").textValue());
	}

	@Test
	public void adminUpdatesPathVariationWithoutChangingBodyNamedVariation() {
		long originalId = create("_original", null);
		long targetId = create("_target", null);
		JsonNode original = get(originalId);

		ResponseEntity<String> updated = testRestTemplate.exchange(API + "/" + targetId,
				HttpMethod.PUT, new HttpEntity<>(body("_updated", originalId), headers), String.class);

		assertEquals(200, updated.getStatusCodeValue());
		assertEquals(original, get(originalId));
		assertEquals(targetId, get(targetId).path("id").longValue());
		assertEquals(prefix + "_updated", get(targetId).path("code").textValue());
	}

	@Test
	public void unauthenticatedCallerCannotCreateVariations() {
		ResponseEntity<String> anonymous = testRestTemplate.postForEntity(API, body("_anonymous", null), String.class);
		assertTrue(anonymous.getStatusCodeValue() == 401 || anonymous.getStatusCodeValue() == 403);
	}
}
