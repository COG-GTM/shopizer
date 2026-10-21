package com.salesmanager.test.shop.integration.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.xpath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Resource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.modules.integration.shipping.canadapost.CanadaPostQuoteModule;
import com.salesmanager.core.modules.integration.shipping.model.ShippingQuotePrePostProcessModule;
import com.salesmanager.core.modules.integration.shipping.model.ShippingQuoteModule;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.customer.address.AddressLocation;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ProductDescription;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.order.shipping.ReadableShippingSummary;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.shipping.ExpeditionConfiguration;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class CanadaPostShippingIntegrationTest extends ServicesTestSupport {

	private static final String DEV_URL = "https://ct.soa-gw.canadapost.ca/rs/ship/price";
	private static final String RATE_MEDIA_TYPE = "application/vnd.cpc.ship.rate-v4+xml";
	private static final Map<String, String> NAMESPACES =
			Collections.singletonMap("r", "http://www.canadapost.ca/ws/ship/rate-v4");

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private CanadaPostQuoteModule canadaPost;

	@Autowired
	private ShippingService shippingService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Resource(name = "shippingModules")
	private Map<String, ShippingQuoteModule> shippingModules;

	@Resource(name = "shippingModulePreProcessors")
	private List<ShippingQuotePrePostProcessModule> shippingModulePreProcessors;

	private org.springframework.http.client.ClientHttpRequestFactory originalRequestFactory;
	private MockRestServiceServer canadaPostServer;
	private List<ShippingQuotePrePostProcessModule> originalShippingModulePreProcessors;

	@Override
	protected ReadableProduct sampleProduct(String code) {
		String sku = code + "-" + UUID.randomUUID().toString().replace("-", "");
		PersistableCategory category = new PersistableCategory();
		category.setCode(sku);
		category.setSortOrder(1);
		category.setVisible(true);
		category.setDepth(4);
		category.setParent(new Category());

		CategoryDescription categoryDescription = new CategoryDescription();
		categoryDescription.setLanguage("en");
		categoryDescription.setName(sku);
		categoryDescription.setFriendlyUrl(sku);
		categoryDescription.setTitle(sku);
		category.setDescriptions(Collections.singletonList(categoryDescription));

		ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<PersistableCategory>(category, getHeader()), PersistableCategory.class);
		assertEquals(HttpStatus.CREATED, categoryResponse.getStatusCode());
		assertNotNull(categoryResponse.getBody());

		PersistableProduct product = product(sku);
		product.setCategories(Collections.singletonList(categoryResponse.getBody()));
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specifications);
		product.setAvailable(true);
		product.setPrice(BigDecimal.TEN);
		product.setSku(sku);
		product.setQuantity(100);
		product.setProductShipeable(true);

		ResponseEntity<Entity> created = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<PersistableProduct>(product, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());

		ResponseEntity<ReadableProduct> readable = testRestTemplate.exchange(
				"/api/v2/product/" + sku, HttpMethod.GET,
				new HttpEntity<String>(getHeader()), ReadableProduct.class);
		assertEquals(HttpStatus.OK, readable.getStatusCode());
		return readable.getBody();
	}

	@Test
	public void registersTheCanadaPostModuleAndKeepsLegacyJaxRsOutOfTheClasspath() {
		assertSame(canadaPost, applicationContext.getBean("canadapost"));
		assertSame(canadaPost, shippingModules.get("canadapost"));
		assertEquals(1, applicationContext.getBeansOfType(CanadaPostQuoteModule.class).size());
		assertEquals("https://soa-gw.canadapost.ca/rs/ship/price", canadaPost.getConfig().getProdUrl());
		assertEquals(DEV_URL, canadaPost.getConfig().getDevUrl());
		assertFalse(ClassUtils.isPresent("com.sun.jersey.api.client.Client", null));
		assertFalse(ClassUtils.isPresent("javax.ws.rs.core.MediaType", null));
	}

	@Test
	public void persistsCanadaPostConfigurationAndRejectsMissingApiKey() throws Exception {
		ResponseEntity<String> modules = adminExchange("/api/v1/private/modules/shipping",
				HttpMethod.GET, null, String.class);
		assertEquals(HttpStatus.OK, modules.getStatusCode());
		assertTrue(modules.getBody().contains("\"canadapost\""));

		ensureInitialConfiguration();
		IntegrationModuleConfiguration configuration = configuration(true, true);
		ResponseEntity<Void> saved = adminExchange("/api/v1/private/modules/shipping",
				HttpMethod.POST, configuration, Void.class);
		assertTrue(saved.getStatusCode().is2xxSuccessful());

		ResponseEntity<String> details = adminExchange("/api/v1/private/modules/shipping/canadapost",
				HttpMethod.GET, null, String.class);
		assertTrue(details.getBody().contains("api-key"));
		assertTrue(details.getBody().contains("services-domestic"));
		assertTrue(details.getBody().contains("DOM.RP"));

		ResponseEntity<Void> rejected = adminExchange("/api/v1/private/modules/shipping",
				HttpMethod.POST, configuration(false, false), Void.class);
		assertFalse(rejected.getStatusCode().is2xxSuccessful());

		ResponseEntity<String> retained = adminExchange("/api/v1/private/modules/shipping/canadapost",
				HttpMethod.GET, null, String.class);
		assertTrue(retained.getBody().contains("api-key"));
	}

	@Test
	public void returnsCanadaPostQuotesFromTheConfiguredModule() throws Exception {
		prepareShipping();
		MockRestServiceServer server = bindMockServer();
		expectDomesticRequest().andRespond(withSuccess(fixture("domestic-price-quotes.xml"),
				MediaType.APPLICATION_XML));

		ReadableShippingSummary summary = getShippingSummary();

		assertEquals(3, summary.getShippingOptions().size());
		assertOption(summary, "DOM.RP", "12.00");
		assertOption(summary, "DOM.EP", "19.50");
		assertOption(summary, "DOM.XP", "24.25");
		server.verify();
	}

	@Test
	public void omitsCanadaPostOptionsWhenTheCarrierReturnsMessages() throws Exception {
		prepareShipping();
		MockRestServiceServer server = bindMockServer();
		expectDomesticRequest().andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_XML)
				.body(fixture("messages-error.xml")));

		ResponseEntity<ReadableShippingSummary> response = requestShippingSummary();

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().getShippingOptions() == null
				|| response.getBody().getShippingOptions().stream()
				.noneMatch(option -> "canadapost".equals(option.getShippingModuleCode())));
		server.verify();
	}

	@AfterEach
	public void cleanup() throws Exception {
		if (originalRequestFactory != null) {
			canadaPost.getRestTemplate().setRequestFactory(originalRequestFactory);
			originalRequestFactory = null;
		}
		if (originalShippingModulePreProcessors != null) {
			shippingModulePreProcessors.clear();
			shippingModulePreProcessors.addAll(originalShippingModulePreProcessors);
			originalShippingModulePreProcessors = null;
		}
		canadaPostServer = null;
		ensureInitialConfiguration();
		adminExchange("/api/v1/private/modules/shipping", HttpMethod.POST,
				configuration(false, true), Void.class);
	}

	private void prepareShipping() throws Exception {
		// Legacy Drools requires --add-opens on JDK 17+; it is unrelated to Canada Post.
		originalShippingModulePreProcessors = new ArrayList<ShippingQuotePrePostProcessModule>(
				shippingModulePreProcessors);
		shippingModulePreProcessors.clear();

		ensureInitialConfiguration();
		deactivateOtherShippingModules();
		ResponseEntity<Void> moduleConfiguration = adminExchange("/api/v1/private/modules/shipping",
				HttpMethod.POST, configuration(true, true), Void.class);
		assertTrue(moduleConfiguration.getStatusCode().is2xxSuccessful());

		ExpeditionConfiguration expedition = new ExpeditionConfiguration();
		expedition.setTaxOnShipping(false);
		expedition.setShipToCountry(Collections.singletonList("CA"));
		ResponseEntity<Void> expeditionConfiguration = adminExchange(
				"/api/v1/private/shipping/expedition", HttpMethod.POST, expedition, Void.class);
		assertTrue(expeditionConfiguration.getStatusCode().is2xxSuccessful());

		PersistableAddress origin = new PersistableAddress();
		origin.setCountry("CA");
		origin.setPostalCode("M5V 3A8");
		origin.setCity("Toronto");
		origin.setAddress("100 King Street West");
		origin.setActive(true);
		ResponseEntity<Void> originConfiguration = adminExchange(
				"/api/v1/private/shipping/origin", HttpMethod.POST, origin, Void.class);
		assertTrue(originConfiguration.getStatusCode().is2xxSuccessful());
	}

	private void ensureInitialConfiguration() throws Exception {
		IntegrationModuleConfiguration initial = configuration(false, true);
		IntegrationConfiguration configured = new IntegrationConfiguration();
		configured.setModuleCode(initial.getCode());
		configured.setActive(initial.isActive());
		configured.setEnvironment("TEST");
		configured.setIntegrationKeys(initial.getIntegrationKeys());
		configured.setIntegrationOptions(initial.getIntegrationOptions());
		shippingService.saveShippingQuoteModuleConfiguration(configured,
				merchantStoreService.getByCode(Constants.DEFAULT_STORE));
	}

	private void deactivateOtherShippingModules() {
		ResponseEntity<String> response = adminExchange("/api/v1/private/modules/shipping",
				HttpMethod.GET, null, String.class);
		try {
			com.fasterxml.jackson.databind.JsonNode modules =
					new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
			for (com.fasterxml.jackson.databind.JsonNode module : modules) {
				String code = module.path("code").asText();
				if (!"canadapost".equals(code) && module.path("active").asBoolean()) {
					ResponseEntity<String> current = adminExchange(
							"/api/v1/private/modules/shipping/" + code, HttpMethod.GET, null, String.class);
					com.fasterxml.jackson.databind.JsonNode details =
							new com.fasterxml.jackson.databind.ObjectMapper().readTree(current.getBody());
					IntegrationModuleConfiguration inactive = new IntegrationModuleConfiguration();
					inactive.setCode(code);
					inactive.setActive(false);
					inactive.setIntegrationKeys(mapOf(details.path("integrationKeys")));
					inactive.setIntegrationOptions(optionMapOf(details.path("integrationOptions")));
					adminExchange("/api/v1/private/modules/shipping", HttpMethod.POST, inactive, Void.class);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read configured shipping modules", e);
		}
	}

	private Map<String, String> mapOf(com.fasterxml.jackson.databind.JsonNode node) {
		Map<String, String> values = new HashMap<String, String>();
		node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText()));
		return values;
	}

	private Map<String, List<String>> optionMapOf(com.fasterxml.jackson.databind.JsonNode node) {
		Map<String, List<String>> values = new HashMap<String, List<String>>();
		node.fields().forEachRemaining(entry -> {
			java.util.ArrayList<String> options = new java.util.ArrayList<String>();
			entry.getValue().forEach(value -> options.add(value.asText()));
			values.put(entry.getKey(), options);
		});
		return values;
	}

	private IntegrationModuleConfiguration configuration(boolean active, boolean includeApiKey) {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("canadapost");
		configuration.setActive(active);
		Map<String, String> keys = new HashMap<String, String>();
		keys.put("username", "cp-user");
		keys.put("password", "cp-password");
		keys.put("account", "1234567");
		if (includeApiKey) {
			keys.put("apikey", "api-key");
		}
		configuration.setIntegrationKeys(keys);
		Map<String, List<String>> options = new HashMap<String, List<String>>();
		options.put("services-domestic", Arrays.asList("DOM.RP", "DOM.EP", "DOM.XP"));
		configuration.setIntegrationOptions(options);
		return configuration;
	}

	private MockRestServiceServer bindMockServer() {
		RestTemplate restTemplate = canadaPost.getRestTemplate();
		originalRequestFactory = restTemplate.getRequestFactory();
		canadaPostServer = MockRestServiceServer.bindTo(restTemplate).build();
		return canadaPostServer;
	}

	private ResponseActions expectDomesticRequest() throws Exception {
		String authorization = "Basic " + java.util.Base64.getEncoder().encodeToString(
				"cp-user:cp-password".getBytes(StandardCharsets.ISO_8859_1));
		return canadaPostServer
				.expect(requestTo(DEV_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Accept", RATE_MEDIA_TYPE))
				.andExpect(header("Content-Type", RATE_MEDIA_TYPE))
				.andExpect(header("Accept-Language", "en-CA"))
				.andExpect(header("Authorization", authorization))
				.andExpect(xpath("/r:mailing-scenario/r:destination/r:domestic/r:postal-code", NAMESPACES)
						.string("K1A0B1"));
	}

	private ReadableShippingSummary getShippingSummary() {
		ResponseEntity<ReadableShippingSummary> response = requestShippingSummary();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ResponseEntity<ReadableShippingSummary> requestShippingSummary() {
		com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart cart = sampleCart();
		AddressLocation address = new AddressLocation();
		address.setPostalCode("K1A 0B1");
		address.setCountryCode("CA");
		return testRestTemplate.postForEntity("/api/v1/cart/" + cart.getCode() + "/shipping",
				new HttpEntity<AddressLocation>(address, new HttpHeaders()), ReadableShippingSummary.class);
	}

	private void assertOption(ReadableShippingSummary summary, String code, String price) {
		ShippingOption option = summary.getShippingOptions().stream()
				.filter(candidate -> code.equals(candidate.getOptionCode()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing shipping option " + code));
		assertEquals("canadapost", option.getShippingModuleCode());
		assertEquals(new BigDecimal(price), option.getOptionPrice());
	}

	private <T> ResponseEntity<T> adminExchange(String path, HttpMethod method, Object body, Class<T> type) {
		return testRestTemplate.exchange(path, method, new HttpEntity<Object>(body, getHeader()), type);
	}

	private String fixture(String name) throws IOException {
		try (InputStream input = getClass().getResourceAsStream("/canadapost/" + name)) {
			if (input == null) {
				throw new IOException("Missing fixture " + name);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
