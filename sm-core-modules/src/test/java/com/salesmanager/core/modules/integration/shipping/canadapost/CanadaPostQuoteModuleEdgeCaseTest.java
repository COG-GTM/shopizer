package com.salesmanager.core.modules.integration.shipping.canadapost;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.xpath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingConfiguration;
import com.salesmanager.core.model.shipping.ShippingOrigin;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.shipping.canadapost.xml.CanadaPostRatingXml;

public class CanadaPostQuoteModuleEdgeCaseTest {

	private static final String DEV_URL = "https://ct.soa-gw.canadapost.ca/rs/ship/price";
	private static final String PROD_URL = "https://soa-gw.canadapost.ca/rs/ship/price";
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
	public void mapsHttp500WithEmptyBodyToTransactionException() {
		server.expect(requestTo(DEV_URL))
				.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(""));

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote(configuration(), "CA", "K1A0B1", null, packageDetail()));

		assertEquals(IntegrationException.TRANSACTION_EXCEPTION, exception.getErrorCode());
		server.verify();
	}

	@Test
	public void mapsHttp503WithHtmlBodyToTransactionException() {
		server.expect(requestTo(DEV_URL))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
						.contentType(MediaType.TEXT_HTML)
						.body("<html><body>unavailable</body></html>"));

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote(configuration(), "CA", "K1A0B1", null, packageDetail()));

		assertEquals(IntegrationException.TRANSACTION_EXCEPTION, exception.getErrorCode());
		server.verify();
	}

	@Test
	public void mapsTransportFailureToTransactionException() {
		server.expect(requestTo(DEV_URL)).andRespond(withException(new IOException("connection reset")));

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote(configuration(), "CA", "K1A0B1", null, packageDetail()));

		assertEquals(IntegrationException.TRANSACTION_EXCEPTION, exception.getErrorCode());
		server.verify();
	}

	@Test
	public void mapsMessagesOnHttp200AndPreservesMessageOrder() throws Exception {
		server.expect(requestTo(DEV_URL))
				.andRespond(withSuccess(fixture("messages-multiple.xml"), MediaType.APPLICATION_XML));

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote(configuration(), "CA", "K1A0B1", null, packageDetail()));

		assertEquals(IntegrationException.TRANSACTION_EXCEPTION, exception.getErrorCode());
		assertEquals("c1 - d1 c2 - d2 ", exception.getMessageCode());
		server.verify();
	}

	@Test
	public void leavesPackageDimensionsAndWeightUnconvertedForCentimetresAndKilograms() throws Exception {
		store.setSeizeunitcode("CM");
		store.setWeightunitcode("KG");
		server.expect(requestTo(DEV_URL))
				.andExpect(xpath("/r:mailing-scenario/r:parcel-characteristics/r:weight", NAMESPACES).string("2"))
				.andExpect(xpath("/r:mailing-scenario/r:parcel-characteristics/r:dimensions/r:length", NAMESPACES)
						.string("10"))
				.andExpect(xpath("/r:mailing-scenario/r:parcel-characteristics/r:dimensions/r:width", NAMESPACES)
						.string("4"))
				.andExpect(xpath("/r:mailing-scenario/r:parcel-characteristics/r:dimensions/r:height", NAMESPACES)
						.string("2"))
				.andRespond(withSuccess(fixture("domestic-price-quotes.xml"), MediaType.APPLICATION_XML));

		quote(configuration(), "CA", "K1A0B1", null, packageDetail());

		server.verify();
	}

	@Test
	public void usesDevelopmentUrlForTestAndNullEnvironmentAndProductionUrlForProd() throws Exception {
		server.expect(requestTo(DEV_URL))
				.andRespond(withSuccess(fixture("domestic-price-quotes.xml"), MediaType.APPLICATION_XML));
		server.expect(requestTo(DEV_URL))
				.andRespond(withSuccess(fixture("domestic-price-quotes.xml"), MediaType.APPLICATION_XML));
		server.expect(requestTo(PROD_URL))
				.andRespond(withSuccess(fixture("domestic-price-quotes.xml"), MediaType.APPLICATION_XML));

		quote(configuration(), "CA", "K1A0B1", "TEST", packageDetail());
		quote(configuration(), "CA", "K1A0B1", null, packageDetail());
		quote(configuration(), "CA", "K1A0B1", "PROD", packageDetail());

		server.verify();
	}

	@Test
	public void rejectsMissingPasswordWithoutMakingHttpCall() {
		IntegrationConfiguration configuration = configuration();
		configuration.getIntegrationKeys().remove("password");

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote(configuration, "CA", "K1A0B1", null, packageDetail()));

		assertEquals("Canadapost missing configuration key password", exception.getMessageCode());
		server.verify();
	}

	@Test
	public void rejectsMissingAccountKeyWithoutMakingHttpCall() {
		IntegrationConfiguration configuration = configuration();
		configuration.getIntegrationKeys().remove("account");

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote(configuration, "CA", "K1A0B1", null, packageDetail()));

		assertEquals("Canadapost missing configuration key account", exception.getMessageCode());
		server.verify();
	}

	@Test
	public void rejectsNullIntegrationOptionsWithoutMakingHttpCall() {
		IntegrationConfiguration configuration = configuration();
		configuration.setIntegrationOptions(null);

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote(configuration, "CA", "K1A0B1", null, packageDetail()));

		assertEquals("Canadapost missing configuration options", exception.getMessageCode());
		server.verify();
	}

	@Test
	public void rejectsMissingDomesticServicesWithoutMakingHttpCall() {
		IntegrationConfiguration configuration = configuration();
		configuration.getIntegrationOptions().put("services-domestic", Collections.<String>emptyList());

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote(configuration, "CA", "K1A0B1", null, packageDetail()));

		assertEquals("Canadapost selected domestic services are missing", exception.getMessageCode());
		server.verify();
	}

	@Test
	public void rejectsMissingUsaServicesWithoutMakingHttpCall() {
		IntegrationConfiguration configuration = configuration();
		configuration.getIntegrationOptions().put("services-usa", Collections.<String>emptyList());

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> quote(configuration, "US", "94105", null, packageDetail()));

		assertEquals("Canadapost selected USA services are missing", exception.getMessageCode());
		server.verify();
	}

	@Test
	public void rejectsNullOriginPostalCode() {
		ShippingOrigin origin = new ShippingOrigin();
		IntegrationConfiguration configuration = configuration();

		NullPointerException exception = assertThrows(NullPointerException.class,
				() -> module.getShippingQuotes(null, Collections.singletonList(packageDetail()), BigDecimal.ZERO,
						delivery("CA", "K1A0B1"), origin, store, configuration, null,
						new ShippingConfiguration(), null));

		assertEquals("Origin postal code is null", exception.getMessage());
		server.verify();
	}

	@Test
	public void validatesAllRequiredKeysAndRejectsNullKeys() throws Exception {
		module.validateModuleConfiguration(configuration(), store);
		IntegrationConfiguration configuration = configuration();
		configuration.setIntegrationKeys(null);

		IntegrationException exception = assertThrows(IntegrationException.class,
				() -> module.validateModuleConfiguration(configuration, store));

		assertEquals(IntegrationException.ERROR_VALIDATION_SAVE, exception.getErrorCode());
	}

	@Test
	public void customModuleConfigurationIsNull() throws Exception {
		assertNull(module.getCustomModuleConfiguration(store));
	}

	private List<com.salesmanager.core.model.shipping.ShippingOption> quote(
			IntegrationConfiguration configuration, String countryCode, String postalCode,
			String environment, PackageDetails... packages) throws Exception {
		configuration.setEnvironment(environment);
		return module.getShippingQuotes(null, Arrays.asList(packages), BigDecimal.ZERO,
				delivery(countryCode, postalCode), origin(), store, configuration, null,
				new ShippingConfiguration(), null);
	}

	private Delivery delivery(String countryCode, String postalCode) {
		Country country = new Country();
		country.setIsoCode(countryCode);
		Delivery delivery = new Delivery();
		delivery.setCountry(country);
		delivery.setPostalCode(postalCode);
		return delivery;
	}

	private ShippingOrigin origin() {
		ShippingOrigin origin = new ShippingOrigin();
		origin.setPostalCode("M5V3A8");
		return origin;
	}

	private IntegrationConfiguration configuration() {
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		Map<String, String> keys = new HashMap<String, String>();
		keys.put("username", "user");
		keys.put("password", "password");
		keys.put("account", "1234567");
		keys.put("apikey", "api-key");
		configuration.setIntegrationKeys(keys);
		Map<String, List<String>> options = new HashMap<String, List<String>>();
		options.put("services-domestic", Arrays.asList("DOM.RP", "DOM.EP"));
		options.put("services-usa", Collections.singletonList("USA.EP"));
		configuration.setIntegrationOptions(options);
		return configuration;
	}

	private PackageDetails packageDetail() {
		PackageDetails detail = new PackageDetails();
		detail.setShippingWeight(2);
		detail.setShippingLength(10);
		detail.setShippingWidth(4);
		detail.setShippingHeight(2);
		return detail;
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
