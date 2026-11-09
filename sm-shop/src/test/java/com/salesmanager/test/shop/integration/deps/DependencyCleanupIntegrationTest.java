package com.salesmanager.test.shop.integration.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Behaviour touched by the dependency cleanup, through the real Spring context: IntegrationConfiguration
 * JSON (json-simple -> Jackson) persisted and read back, module JSON columns parsed by
 * ModuleConfigurationServiceImpl, supported-country parsing, shipping configuration JSON and multipart upload.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class DependencyCleanupIntegrationTest extends ServicesTestSupport {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String TRICKY = "1 \"Main\" St / Suite \\ 5, Montréal 中";

	private JsonNode getJson(String url, HttpHeaders headers) throws Exception {
		ResponseEntity<String> r = testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
		assertEquals(HttpStatus.OK, r.getStatusCode(), url + " -> " + r.getBody());
		return MAPPER.readTree(r.getBody());
	}

	@Test
	void paymentModuleConfigurationRoundTripsSpecialCharacters() throws Exception {
		HttpHeaders headers = getHeader();
		Map<String, Object> body = new HashMap<>();
		body.put("code", "moneyorder");
		body.put("active", true);
		body.put("defaultSelected", true);
		body.put("integrationKeys", Map.of("address", TRICKY));
		ResponseEntity<String> post = testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
		assertEquals(HttpStatus.OK, post.getStatusCode(), String.valueOf(post.getBody()));

		JsonNode module = getJson("/api/v1/private/modules/payment/moneyorder", headers);
		assertEquals("moneyorder", module.get("code").asText());
		assertTrue(module.get("active").asBoolean());
		assertTrue(module.get("defaultSelected").asBoolean());
		assertEquals(TRICKY, module.get("integrationKeys").get("address").asText());

		JsonNode list = getJson("/api/v1/private/modules/payment", headers);
		boolean found = false;
		for (JsonNode m : list) {
			if ("moneyorder".equals(m.get("code").asText())) {
				found = true;
				assertTrue(m.get("active").asBoolean(), m.toString());
			}
		}
		assertTrue(found, list.toString());
	}

	@Test
	void shippingModulesExposeParsedModuleJson() throws Exception {
		JsonNode modules = getJson("/api/v1/private/modules/shipping", getHeader());
		assertTrue(modules.isArray() && modules.size() > 0, modules.toString());
		List<String> codes = new java.util.ArrayList<>();
		for (JsonNode m : modules) {
			assertTrue(m.hasNonNull("code"), m.toString());
			assertTrue(m.has("active") && m.has("configured") && m.has("requiredKeys"), m.toString());
			codes.add(m.get("code").asText());
		}
		assertTrue(codes.containsAll(List.of("ups", "canadapost", "weightBased", "storePickUp")), codes.toString());
	}

	@Test
	void shipToCountriesUseSupportedCountryJson() throws Exception {
		JsonNode countries = getJson("/api/v1/shipping/country?store=DEFAULT", new HttpHeaders());
		assertTrue(countries.isArray(), countries.toString());
		assertTrue(countries.size() > 0, countries.toString());
		assertTrue(countries.get(0).hasNonNull("code"), countries.toString());
	}

	@Test
	void expeditionConfigurationRoundTrip() throws Exception {
		HttpHeaders headers = getHeader();
		Map<String, Object> exp = new HashMap<>();
		exp.put("iternationalShipping", true);
		exp.put("taxOnShipping", true);
		exp.put("shipToCountry", List.of("CA", "US"));
		ResponseEntity<String> post = testRestTemplate.exchange("/api/v1/private/shipping/expedition", HttpMethod.POST,
				new HttpEntity<>(exp, headers), String.class);
		assertEquals(HttpStatus.OK, post.getStatusCode(), String.valueOf(post.getBody()));

		JsonNode read = getJson("/api/v1/private/shipping/expedition", headers);
		assertTrue(read.get("iternationalShipping").asBoolean(), read.toString());
		assertTrue(read.get("taxOnShipping").asBoolean(), read.toString());
		List<String> codes = MAPPER.convertValue(read.get("shipToCountry"), List.class);
		assertTrue(codes.containsAll(List.of("CA", "US")), read.toString());

		JsonNode countries = getJson("/api/v1/shipping/country?store=DEFAULT", new HttpHeaders());
		assertEquals(2, countries.size(), countries.toString());
	}

	@Test
	void multipartImageUploadIsAccepted() throws Exception {
		HttpHeaders headers = getHeader();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		byte[] png = new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0 };
		form.add("qqfile", new ByteArrayResource(png) {
			@Override
			public String getFilename() {
				return "deps-upload.png";
			}
		});
		form.add("qquuid", "uuid-1");
		form.add("qqfilename", "deps-upload.png");
		form.add("qqtotalfilesize", String.valueOf(png.length));
		ResponseEntity<String> r = testRestTemplate.exchange("/api/v1/private/content/images/add?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
		assertEquals(HttpStatus.CREATED, r.getStatusCode(), String.valueOf(r.getBody()));
		JsonNode status = MAPPER.readTree(r.getBody());
		assertTrue(status.get("success").asBoolean(), r.getBody());
		assertFalse(status.hasNonNull("error") && !status.get("error").asText().isEmpty(), r.getBody());
	}
}
