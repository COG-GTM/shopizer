package com.salesmanager.core.modules.integration.shipping.canadapost;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingConfiguration;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.shipping.ShippingOrigin;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.shipping.canadapost.xml.CanadaPostRatingXml;

public class CanadaPostQuoteModuleTest {

	private static final String DEV_URL = "https://ct.soa-gw.canadapost.ca/rs/ship/price";
	private static final String PROD_URL = "https://soa-gw.canadapost.ca/rs/ship/price";
	private static final String MEDIA_TYPE = "application/vnd.cpc.ship.rate-v4+xml";
	private static final Map<String, String> NAMESPACES =
			Collections.singletonMap("r", CanadaPostRatingXml.RATING_NAMESPACE);

	private CanadaPostQuoteModule module;
	private MockRestServiceServer server;
	private MerchantStore store;

	@Before
	public void setUp() {
		CanadaPostQuoteModuleConfig config = new CanadaPostQuoteModuleConfig();
		config.setDevUrl(DEV_URL);
		config.setProdUrl(PROD_URL);
		module = new CanadaPostQuoteModule();
		module.setConfig(config);
		RestTemplate restTemplate = new RestTemplate();
		module.setRestTemplate(restTemplate);
		server = MockRestServiceServer.createServer(restTemplate);
		store = new MerchantStore();
		store.setSeizeunitcode("IN");
		store.setWeightunitcode("LB");
	}

	@Test
	public void requestsDomesticQuotesAndUsesBasePricesWhenShippingTaxIsOff() throws Exception {
		expectRequest(DEV_URL, "K1A0B1", Arrays.asList("DOM.RP", "DOM.EP", "DOM.XP"),
				"2", "26", "11", "6")
				.andRespond(withSuccess(fixture("domestic-price-quotes.xml"), MediaType.APPLICATION_XML));

		PackageDetails packageDetail = packageDetail(2, 10, 4, 2);
		List<ShippingOption> quotes = quote("CA", "K1A 0B1", "DEV", false, packageDetail);

		assertQuotes(quotes, mapOf(
				"DOM.RP", "Regular Parcel", "12.00",
				"DOM.EP", "Expedited Parcel", "19.50",
				"DOM.XP", "Xpresspost", "24.25"));
		assertEquals(1.0, packageDetail.getShippingWeight(), 0.0);
		server.verify();
	}

	@Test
	public void usesDuePricesWhenShippingTaxIsOn() throws Exception {
		expectRequest(DEV_URL, "K1A0B1", Arrays.asList("DOM.RP", "DOM.EP", "DOM.XP"),
				"2", "26", "11", "6")
				.andRespond(withSuccess(fixture("domestic-price-quotes.xml"), MediaType.APPLICATION_XML));

		List<ShippingOption> quotes = quote("CA", "K1A0B1", "DEV", true, packageDetail(2, 10, 4, 2));

		assertQuotes(quotes, mapOf(
				"DOM.RP", "Regular Parcel", "13.56",
				"DOM.EP", "Expedited Parcel", "22.04",
				"DOM.XP", "Xpresspost", "27.40"));
		server.verify();
	}

	@Test
	public void usesProductionUrlAndUnitedStatesZipCode() throws Exception {
		expectRequest(PROD_URL, "94105", Arrays.asList("USA.EP", "USA.XP"),
				"2", "26", "11", "6")
				.andExpect(xpath("/r:mailing-scenario/r:destination/r:united-states/r:zip-code", NAMESPACES)
						.string("94105"))
				.andRespond(withSuccess(fixture("usa-price-quotes.xml"), MediaType.APPLICATION_XML));

		List<ShippingOption> quotes = quote("US", " 94105 ", "PROD", false, packageDetail(2, 10, 4, 2));

		assertQuotes(quotes, mapOf(
				"USA.EP", "Expedited Parcel USA", "21.00",
				"USA.XP", "Tracked Packet USA", "16.75"));
		server.verify();
	}

	@Test
	public void aggregatesDuePriceForLaterPackages() throws Exception {
		expectRequest(DEV_URL, "K1A0B1", Arrays.asList("DOM.RP", "DOM.EP", "DOM.XP"),
				"2", "26", "11", "6")
				.andRespond(withSuccess(fixture("domestic-price-quotes.xml"), MediaType.APPLICATION_XML));
		expectRequest(DEV_URL, "K1A0B1", Arrays.asList("DOM.RP", "DOM.EP", "DOM.XP"),
				"5", "31", "16", "8")
				.andRespond(withSuccess(fixture("domestic-price-quotes.xml"), MediaType.APPLICATION_XML));

		List<ShippingOption> quotes = quote("CA", "K1A0B1", "DEV", false,
				packageDetail(2, 10, 4, 2), packageDetail(5, 12, 6, 3));

		assertQuotes(quotes, mapOf(
				"DOM.RP", "Regular Parcel", "25.56",
				"DOM.EP", "Expedited Parcel", "41.54",
				"DOM.XP", "Xpresspost", "51.65"));
		server.verify();
	}

	@Test
	public void mapsMessagesBodyFromHttp400ToTransactionException() throws Exception {
		expectRequest(DEV_URL, "K1A0B1", Arrays.asList("DOM.RP", "DOM.EP", "DOM.XP"),
				"2", "26", "11", "6")
				.andRespond(withStatus(HttpStatus.BAD_REQUEST)
						.contentType(MediaType.APPLICATION_XML)
						.body(fixture("messages-error.xml")));

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote("CA", "K1A0B1", "DEV", false, packageDetail(2, 10, 4, 2)));

		assertEquals(IntegrationException.TRANSACTION_EXCEPTION, exception.getErrorCode());
		assertEquals("12345 - Invalid account credentials ", exception.getMessageCode());
		server.verify();
	}

	@Test
	public void rejectsMissingConfigurationKeys() {
		IntegrationConfiguration configuration = configuration("CA");
		configuration.setIntegrationKeys(null);
		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quotes(configuration, "CA", "K1A0B1", "DEV", false, packageDetail(2, 10, 4, 2)));

		assertEquals("Canadapost missing configuration keys", exception.getMessageCode());
		server.verify();
	}

	@Test
	public void rejectsCountriesOutsideCanadaAndTheUs() {
		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote("GB", "SW1A 1AA", "DEV", false, packageDetail(2, 10, 4, 2)));

		assertEquals("Canadapost Not configured for shipping in country GB", exception.getMessageCode());
		server.verify();
	}

	@Test
	public void returnsNullForBlankDeliveryPostalCode() throws Exception {
		List<ShippingOption> quotes = quote("CA", "  ", "DEV", false, packageDetail(2, 10, 4, 2));

		assertNull(quotes);
		server.verify();
	}

	@Test
	public void validatesApiKeyAlongWithOtherIntegrationKeys() {
		IntegrationConfiguration configuration = configuration("CA");
		configuration.getIntegrationKeys().remove("apikey");

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> module.validateModuleConfiguration(configuration, store));

		assertEquals(IntegrationException.ERROR_VALIDATION_SAVE, exception.getErrorCode());
		assertEquals(Collections.singletonList("apikey"), exception.getErrorFields());
	}

	private org.springframework.test.web.client.ResponseActions expectRequest(String url, String postalCode,
			List<String> services, String weight, String length, String width, String height) throws Exception {
		String auth = "Basic " + Base64.getEncoder().encodeToString(
				"user:password".getBytes(StandardCharsets.ISO_8859_1));
		org.springframework.test.web.client.ResponseActions actions = server.expect(requestTo(url))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Accept", MEDIA_TYPE))
				.andExpect(header("Content-Type", MEDIA_TYPE))
				.andExpect(header("Accept-Language", "en-CA"))
				.andExpect(header("Authorization", auth))
				.andExpect(xpath("/r:mailing-scenario/r:customer-number", NAMESPACES).string("1234567"))
				.andExpect(xpath("/r:mailing-scenario/r:origin-postal-code", NAMESPACES).string("M5V3A8"))
				.andExpect(xpath("/r:mailing-scenario/r:parcel-characteristics/r:weight", NAMESPACES)
						.string(weight))
				.andExpect(xpath("/r:mailing-scenario/r:parcel-characteristics/r:dimensions/r:length", NAMESPACES)
						.string(length))
				.andExpect(xpath("/r:mailing-scenario/r:parcel-characteristics/r:dimensions/r:width", NAMESPACES)
						.string(width))
				.andExpect(xpath("/r:mailing-scenario/r:parcel-characteristics/r:dimensions/r:height", NAMESPACES)
						.string(height));
		String destinationPath = services.get(0).startsWith("USA.")
				? "/r:mailing-scenario/r:destination/r:united-states/r:zip-code"
				: "/r:mailing-scenario/r:destination/r:domestic/r:postal-code";
		actions.andExpect(xpath(destinationPath, NAMESPACES).string(postalCode));
		for (int i = 0; i < services.size(); i++) {
			actions.andExpect(xpath("/r:mailing-scenario/r:services/r:service-code[" + (i + 1) + "]", NAMESPACES)
					.string(services.get(i)));
		}
		return actions;
	}

	private List<ShippingOption> quote(String countryCode, String postalCode, String environment,
			boolean taxOnShipping, PackageDetails... packages) throws Exception {
		return quotes(configuration(countryCode), countryCode, postalCode, environment, taxOnShipping, packages);
	}

	private List<ShippingOption> quotes(IntegrationConfiguration configuration, String countryCode,
			String postalCode, String environment, boolean taxOnShipping, PackageDetails... packages)
			throws Exception {
		configuration.setEnvironment(environment);
		ShippingConfiguration shippingConfiguration = new ShippingConfiguration();
		shippingConfiguration.setTaxOnShipping(taxOnShipping);
		Delivery delivery = new Delivery();
		delivery.setCountry(country(countryCode));
		delivery.setPostalCode(postalCode);
		ShippingOrigin origin = new ShippingOrigin();
		origin.setPostalCode("m5v 3a8");
		return module.getShippingQuotes(null, Arrays.asList(packages), BigDecimal.ZERO, delivery, origin, store,
				configuration, null, shippingConfiguration, null);
	}

	private IntegrationConfiguration configuration(String countryCode) {
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		Map<String, String> keys = new HashMap<String, String>();
		keys.put("username", "user");
		keys.put("password", "password");
		keys.put("account", "1234567");
		keys.put("apikey", "api-key");
		configuration.setIntegrationKeys(keys);
		Map<String, List<String>> options = new HashMap<String, List<String>>();
		options.put("services-domestic", Arrays.asList("DOM.RP", "DOM.EP", "DOM.XP"));
		options.put("services-usa", Arrays.asList("USA.EP", "USA.XP"));
		options.put("services-intl", Collections.singletonList("INT.XP"));
		configuration.setIntegrationOptions(options);
		return configuration;
	}

	private Country country(String isoCode) {
		Country country = new Country();
		country.setIsoCode(isoCode);
		return country;
	}

	private PackageDetails packageDetail(double weight, double length, double width, double height) {
		PackageDetails detail = new PackageDetails();
		detail.setShippingWeight(weight);
		detail.setShippingLength(length);
		detail.setShippingWidth(width);
		detail.setShippingHeight(height);
		return detail;
	}

	private String fixture(String name) throws IOException {
		try (InputStream input = getClass().getResourceAsStream("/canadapost/" + name)) {
			assertNotNull(input);
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private Map<String, String[]> mapOf(String... values) {
		Map<String, String[]> quotes = new TreeMap<String, String[]>();
		for (int i = 0; i < values.length; i += 3) {
			quotes.put(values[i], new String[] { values[i + 1], values[i + 2] });
		}
		return quotes;
	}

	private void assertQuotes(List<ShippingOption> actual, Map<String, String[]> expected) {
		Map<String, ShippingOption> byCode = actual.stream()
				.collect(Collectors.toMap(ShippingOption::getOptionCode, option -> option));
		assertEquals(expected.keySet(), byCode.keySet());
		for (Map.Entry<String, String[]> entry : expected.entrySet()) {
			ShippingOption option = byCode.get(entry.getKey());
			assertEquals(entry.getKey(), entry.getValue()[0], option.getOptionName());
			assertEquals(new BigDecimal(entry.getValue()[1]), option.getOptionPrice());
		}
	}
}
