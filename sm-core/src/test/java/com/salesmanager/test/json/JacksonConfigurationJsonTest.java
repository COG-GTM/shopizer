package com.salesmanager.test.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.modules.email.EmailConfig;
import com.salesmanager.core.business.services.reference.loader.ConfigurationModulesLoader;
import com.salesmanager.core.business.utils.ajax.AjaxPageableResponse;
import com.salesmanager.core.business.utils.ajax.AjaxResponse;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.shipping.Package;
import com.salesmanager.core.model.shipping.ShippingBasisType;
import com.salesmanager.core.model.shipping.ShippingConfiguration;
import com.salesmanager.core.model.shipping.ShippingPackageType;
import com.salesmanager.core.model.shipping.ShippingType;
import com.salesmanager.core.model.system.CustomIntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.MerchantConfig;
import com.salesmanager.core.model.tax.TaxBasisCalculation;
import com.salesmanager.core.model.tax.TaxConfiguration;
import com.salesmanager.core.modules.integration.shipping.model.CustomShippingQuoteWeightItem;
import com.salesmanager.core.modules.integration.shipping.model.CustomShippingQuotesConfiguration;
import com.salesmanager.core.modules.integration.shipping.model.CustomShippingQuotesRegion;

/**
 * json-simple -> Jackson: every toJSONString() that is persisted in MERCHANT_CONFIGURATION / module
 * configuration must still produce valid JSON that the existing Jackson readers parse back.
 */
public class JacksonConfigurationJsonTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String TRICKY = "a \"quoted\" value / with \\ backslash\nnewline\tand é ü 中";

	private static JsonNode parse(String json) throws Exception {
		return MAPPER.readTree(json);
	}

	@Test
	void integrationConfigurationRoundTripsThroughConfigurationModulesLoader() throws Exception {
		IntegrationConfiguration c = new IntegrationConfiguration();
		c.setModuleCode("stripe");
		c.setActive(true);
		c.setDefaultSelected(true);
		c.setEnvironment(IntegrationConfiguration.TEST_ENVIRONMENT);
		Map<String, String> keys = new LinkedHashMap<>();
		keys.put("publishableKey", "pk_test_123");
		keys.put("secretKey", TRICKY);
		keys.put("url", "https://example.com/a/b?c=d&e=f");
		c.setIntegrationKeys(keys);
		Map<String, List<String>> options = new HashMap<>();
		options.put("transaction", List.of("AUTHORIZECAPTURE"));
		c.setIntegrationOptions(options);

		JsonNode node = parse(c.toJSONString());
		assertEquals("stripe", node.get("moduleCode").asText());
		assertTrue(node.get("active").isBoolean());
		assertTrue(node.get("active").asBoolean());
		assertTrue(node.get("defaultSelected").asBoolean());
		assertEquals("TEST", node.get("environment").asText());
		assertEquals(TRICKY, node.get("integrationKeys").get("secretKey").asText());
		assertEquals("https://example.com/a/b?c=d&e=f", node.get("integrationKeys").get("url").asText());
		assertEquals("AUTHORIZECAPTURE", node.get("integrationOptions").get("transaction").get(0).asText());

		Map<String, IntegrationConfiguration> in = new LinkedHashMap<>();
		in.put("stripe", c);
		IntegrationConfiguration moneyorder = new IntegrationConfiguration();
		moneyorder.setModuleCode("moneyorder");
		moneyorder.setActive(false);
		moneyorder.getIntegrationKeys().put("address", "1 Main St, Montréal");
		in.put("moneyorder", moneyorder);

		Map<String, IntegrationConfiguration> out = ConfigurationModulesLoader
				.loadIntegrationConfigurations(ConfigurationModulesLoader.toJSONString(in));
		assertEquals(2, out.size());
		assertEquals(keys, out.get("stripe").getIntegrationKeys());
		assertTrue(out.get("stripe").isActive());
		assertTrue(out.get("stripe").isDefaultSelected());
		assertEquals("TEST", out.get("stripe").getEnvironment());
		assertEquals(List.of("AUTHORIZECAPTURE"), out.get("stripe").getIntegrationOptions().get("transaction"));
		assertFalse(out.get("moneyorder").isActive());
		assertEquals("1 Main St, Montréal", out.get("moneyorder").getIntegrationKeys().get("address"));
	}

	@Test
	void integrationConfigurationWithoutKeysOmitsIntegrationKeys() throws Exception {
		IntegrationConfiguration c = new IntegrationConfiguration();
		c.setModuleCode("free");
		JsonNode node = parse(c.toJSONString());
		assertFalse(node.has("integrationKeys"));
		assertFalse(node.has("integrationOptions"));
		assertFalse(node.get("active").asBoolean());
	}

	@Test
	void integrationConfigurationNullKeyValueIsJsonNull() throws Exception {
		IntegrationConfiguration c = new IntegrationConfiguration();
		c.setModuleCode("m");
		c.getIntegrationKeys().put("empty", null);
		JsonNode node = parse(c.toJSONString());
		assertTrue(node.get("integrationKeys").get("empty").isNull());
	}

	@Test
	void merchantConfigRoundTrip() throws Exception {
		MerchantConfig config = new MerchantConfig();
		config.setDisplayContactUs(true);
		config.setDisplayCustomerSection(true);
		config.setTestMode(true);
		config.setAllowPurchaseItems(false);
		config.getUseDefaultSearchConfig().put("en", Boolean.TRUE);
		config.getUseDefaultSearchConfig().put("fr", null);
		config.getDefaultSearchConfigPath().put("en", "/search/en/config.json");
		config.getDefaultSearchConfigPath().put("fr", "  ");

		String json = config.toJSONString();
		JsonNode node = parse(json);
		assertTrue(node.get("displayContactUs").isBoolean());
		assertTrue(node.get("useDefaultSearchConfig").isObject(), "nested object, not a string: " + json);
		assertTrue(node.get("useDefaultSearchConfig").get("en").asBoolean());
		assertFalse(node.get("useDefaultSearchConfig").has("fr"), "null values are skipped");
		assertEquals("/search/en/config.json", node.get("defaultSearchConfigPath").get("en").asText());
		assertFalse(node.get("defaultSearchConfigPath").has("fr"), "blank values are skipped");

		MerchantConfig read = MAPPER.readValue(json, MerchantConfig.class);
		assertTrue(read.isDisplayContactUs());
		assertTrue(read.isDisplayCustomerSection());
		assertTrue(read.isTestMode());
		assertFalse(read.isAllowPurchaseItems());
		assertTrue(read.isDisplayPagesMenu());
		assertEquals(Boolean.TRUE, read.getUseDefaultSearchConfig().get("en"));
		assertEquals("/search/en/config.json", read.getDefaultSearchConfigPath().get("en"));
	}

	@Test
	void taxConfigurationRoundTrip() throws Exception {
		TaxConfiguration tax = new TaxConfiguration();
		tax.setTaxBasisCalculation(TaxBasisCalculation.BILLINGADDRESS);
		String json = tax.toJSONString();
		assertEquals("{\"taxBasisCalculation\":\"BILLINGADDRESS\"}", json);
		assertEquals(TaxBasisCalculation.BILLINGADDRESS,
				MAPPER.readValue(json, TaxConfiguration.class).getTaxBasisCalculation());
	}

	@Test
	void shippingConfigurationRoundTrip() throws Exception {
		ShippingConfiguration conf = new ShippingConfiguration();
		conf.setShippingType(ShippingType.INTERNATIONAL);
		conf.setShippingBasisType(ShippingBasisType.BILLING);
		conf.setShippingPackageType(ShippingPackageType.BOX);
		conf.setFreeShippingType(ShippingType.NATIONAL);
		conf.setBoxWidth(10);
		conf.setBoxHeight(20);
		conf.setBoxLength(30);
		conf.setBoxWeight(1.5);
		conf.setMaxWeight(25.25);
		conf.setFreeShippingEnabled(true);
		conf.setOrderTotalFreeShipping(new BigDecimal("99.99"));
		conf.setHandlingFees(new BigDecimal("2.50"));
		conf.setTaxOnShipping(true);
		Package p = new Package();
		p.setCode("small-box");
		p.setBoxWidth(5);
		p.setBoxHeight(6);
		p.setBoxLength(7);
		p.setBoxWeight(0.5);
		p.setMaxWeight(10);
		p.setTreshold(3);
		p.setDefaultPackaging(true);
		p.setShipPackageType(ShippingPackageType.BOX);
		conf.getPackages().add(p);

		String json = conf.toJSONString();
		JsonNode node = parse(json);
		assertEquals("INTERNATIONAL", node.get("shipType").asText());
		assertEquals("BILLING", node.get("shipBaseType").asText());
		assertEquals("BOX", node.get("shipPackageType").asText());
		assertTrue(node.get("orderTotalFreeShipping").isNumber(), "BigDecimal stays a JSON number: " + json);
		assertEquals(0, new BigDecimal("99.99").compareTo(node.get("orderTotalFreeShipping").decimalValue()));
		assertEquals(0, new BigDecimal("2.50").compareTo(node.get("handlingFees").decimalValue()));
		assertTrue(node.get("packages").isArray(), "packages is a JSON array, not a string: " + json);
		assertEquals("small-box", node.get("packages").get(0).get("code").asText());

		ShippingConfiguration read = MAPPER.readValue(json, ShippingConfiguration.class);
		assertEquals(ShippingType.INTERNATIONAL, read.getShippingType());
		assertEquals(ShippingBasisType.BILLING, read.getShippingBasisType());
		assertEquals(ShippingPackageType.BOX, read.getShippingPackageType());
		assertEquals(10, read.getBoxWidth());
		assertEquals(25.25, read.getMaxWeight());
		assertTrue(read.isFreeShippingEnabled());
		assertTrue(read.isTaxOnShipping());
		assertEquals(0, new BigDecimal("99.99").compareTo(read.getOrderTotalFreeShipping()));
		assertEquals(0, new BigDecimal("2.50").compareTo(read.getHandlingFees()));
		assertEquals(1, read.getPackages().size());
		assertEquals("small-box", read.getPackages().get(0).getCode());
		assertEquals(3, read.getPackages().get(0).getTreshold());
		assertTrue(read.getPackages().get(0).isDefaultPackaging());
	}

	@Test
	void shippingConfigurationNullBigDecimalsAreJsonNull() throws Exception {
		JsonNode node = parse(new ShippingConfiguration().toJSONString());
		assertTrue(node.get("handlingFees").isNull());
		assertTrue(node.get("orderTotalFreeShipping").isNull());
		assertFalse(node.has("shipFreeType"));
		assertEquals(0, node.get("packages").size());
	}

	@Test
	void emailConfigRoundTrip() throws Exception {
		EmailConfig e = new EmailConfig();
		e.setHost("smtp.example.com");
		e.setPort("587");
		e.setProtocol("smtp");
		e.setUsername("user@example.com");
		e.setPassword("p\"a/s\\s");
		e.setSmtpAuth(true);
		e.setStarttls(true);
		String json = e.toJSONString();
		JsonNode node = parse(json);
		assertTrue(node.get("smtpAuth").isBoolean());
		assertEquals("587", node.get("port").asText());

		EmailConfig read = MAPPER.readValue(json, EmailConfig.class);
		assertEquals("smtp.example.com", read.getHost());
		assertEquals("587", read.getPort());
		assertEquals("smtp", read.getProtocol());
		assertEquals("user@example.com", read.getUsername());
		assertEquals("p\"a/s\\s", read.getPassword());
		assertTrue(read.isSmtpAuth());
		assertTrue(read.isStarttls());
	}

	@Test
	void transactionDetailsJson() throws Exception {
		Transaction t = new Transaction();
		assertNull(t.toJSONString());
		Map<String, String> details = new LinkedHashMap<>();
		details.put("TRANSACTIONID", "ch_123");
		details.put("NOTE", TRICKY);
		t.setTransactionDetails(details);
		JsonNode node = parse(t.toJSONString());
		assertEquals("ch_123", node.get("TRANSACTIONID").asText());
		assertEquals(TRICKY, node.get("NOTE").asText());
	}

	@Test
	void customShippingQuotesConfigurationIsValidJson() throws Exception {
		CustomShippingQuoteWeightItem item = new CustomShippingQuoteWeightItem();
		item.setPrice(new BigDecimal("12.50"));
		item.setMaximumWeight(5);
		CustomShippingQuotesRegion region = new CustomShippingQuotesRegion();
		region.setCustomRegionName("North America");
		region.setCountries(List.of("CA", "US"));
		region.setQuoteItems(List.of(item));
		CustomShippingQuotesConfiguration conf = new CustomShippingQuotesConfiguration();
		conf.setModuleCode("weightBased");
		conf.setActive(true);
		conf.setRegions(List.of(region));

		CustomIntegrationConfiguration asInterface = conf;
		JsonNode node = parse(asInterface.toJSONString());
		assertEquals("weightBased", node.get("moduleCode").asText());
		assertTrue(node.get("active").asBoolean());
		JsonNode r = node.get("regions").get(0);
		assertEquals("North America", r.get("customRegionName").asText());
		assertEquals("US", r.get("countries").get(1).asText());
		JsonNode q = r.get("quoteItems").get(0);
		assertTrue(q.get("price").isNumber());
		assertEquals(0, new BigDecimal("12.50").compareTo(q.get("price").decimalValue()));
		assertEquals(5, q.get("maximumWeight").asInt());
	}

	@Test
	void ajaxResponseEscapesStatusMessageAndDataEntries() throws Exception {
		AjaxResponse resp = new AjaxResponse();
		resp.setStatus(AjaxResponse.RESPONSE_STATUS_FAIURE);
		resp.setStatusMessage(TRICKY);
		Map<String, String> entry = new LinkedHashMap<>();
		entry.put("name", TRICKY);
		entry.put("id", "1");
		entry.put("nothing", null);
		resp.addDataEntry(entry);

		JsonNode node = parse(resp.toJSONString());
		assertEquals(-1, node.get("response").get("status").asInt());
		assertEquals(TRICKY, node.get("response").get("statusMessage").asText());
		JsonNode data = node.get("response").get("data").get(0);
		assertTrue(data.get("name").isTextual());
		assertEquals(TRICKY, data.get("name").asText());
		assertEquals("1", data.get("id").asText());
		assertTrue(data.get("nothing").isNull());
	}

	@Test
	void ajaxResponseSuccessHasNoStatusMessage() throws Exception {
		AjaxResponse resp = new AjaxResponse();
		resp.setStatus(AjaxResponse.RESPONSE_STATUS_SUCCESS);
		resp.setStatusMessage("ignored");
		JsonNode node = parse(resp.toJSONString());
		assertEquals(0, node.get("response").get("status").asInt());
		assertFalse(node.get("response").has("statusMessage"));
	}

	@Test
	void ajaxPageableResponseDataIsValidJson() throws Exception {
		AjaxPageableResponse resp = new AjaxPageableResponse();
		resp.setStatus(AjaxResponse.RESPONSE_STATUS_SUCCESS);
		resp.setStartRow(0);
		resp.setEndRow(1);
		resp.setTotalRow(1);
		Map<String, String> entry = new LinkedHashMap<>();
		entry.put("sku", "SKU/1 \"x\"");
		resp.addDataEntry(entry);
		JsonNode node = parse(resp.toJSONString());
		assertEquals("SKU/1 \"x\"", node.get("response").get("data").get(0).get("sku").asText());
	}
}
