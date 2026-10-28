package com.salesmanager.core.business.modules.integration.shipping.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.utils.ProductPriceUtils;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.IntegrationException;

public class USPSShippingQuoteEndpointTest extends CarrierShippingQuoteTestSupport {

	private static final String RESPONSE_XML = "<?xml version=\"1.0\"?>"
			+ "<RateV3Response><Package ID=\"1\"><Postage CLASSID=\"1\">"
			+ "<MailService>Priority Mail</MailService><Rate>12.50</Rate>"
			+ "</Postage></Package></RateV3Response>";

	@Test
	public void usesPinnedTestEndpointAndParsesCarrierResponse() throws Exception {
		assertPinnedEndpoint("TEST", "testing.shippingapis.com", "/ShippingAPI.dll");
	}

	@Test
	public void usesPinnedProductionEndpointAndParsesCarrierResponse() throws Exception {
		assertPinnedEndpoint("PROD", "production.shippingapis.com", "/ShippingAPI.dll");
	}

	@Test
	public void doesNotSendRequestForUnsupportedEnvironment() throws Exception {
		AtomicReference<HttpUriRequest> capturedRequest = new AtomicReference<HttpUriRequest>();
		AtomicInteger requestCount = new AtomicInteger();
		CloseableHttpClient client = createClient(RESPONSE_XML, capturedRequest, requestCount);
		Country country = createUnitedStates();
		IntegrationConfiguration configuration = createConfiguration("STAGING", false);

		try (MockedStatic<HttpClients> clients = mockStatic(HttpClients.class)) {
			clients.when(HttpClients::createDefault).thenReturn(client);
			try {
				createQuote(country).getShippingQuotes(null, createPackages(), BigDecimal.ONE,
						createDelivery(country), null, createStore(country), configuration,
						createModule("usps"), null, Locale.ENGLISH);
				fail("Expected an unsupported environment to be rejected");
			} catch (IntegrationException expected) {
				assertNull(capturedRequest.get());
				assertEquals(0, requestCount.get());
				verify(client, never()).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
			}
		}
	}

	@Test
	public void rejectsXxeWithoutReturningExternalFileContents() throws Exception {
		String xxeResponse = "<?xml version=\"1.0\"?>"
				+ "<!DOCTYPE RateV3Response [<!ENTITY xxe SYSTEM \"SECRET_URI\">]>"
				+ "<RateV3Response><Package><Postage CLASSID=\"1\"><MailService>&xxe;</MailService>"
				+ "<Rate>12.50</Rate></Postage></Package></RateV3Response>";
		Country country = createUnitedStates();
		IntegrationConfiguration configuration = createConfiguration("TEST", false);
		IntegrationModule module = createModule("usps");

		IntegrationException expected = requestWithXxe(
				() -> createQuote(country).getShippingQuotes(null, createPackages(), BigDecimal.ONE,
						createDelivery(country), null, createStore(country), configuration, module,
						null, Locale.ENGLISH),
				xxeResponse);
		assertTrue(hasCause(expected, org.xml.sax.SAXException.class));
	}

	private void assertPinnedEndpoint(String environment, String host, String path) throws Exception {
		AtomicReference<HttpUriRequest> capturedRequest = new AtomicReference<HttpUriRequest>();
		AtomicInteger requestCount = new AtomicInteger();
		CloseableHttpClient client = createClient(RESPONSE_XML, capturedRequest, requestCount);
		Country country = createUnitedStates();
		IntegrationConfiguration configuration = createConfiguration(environment, false);
		IntegrationModule module = createModule("usps");

		try (MockedStatic<HttpClients> clients = mockStatic(HttpClients.class)) {
			clients.when(HttpClients::createDefault).thenReturn(client);
			List<ShippingOption> options = createQuote(country).getShippingQuotes(
					null, createPackages(), BigDecimal.ONE, createDelivery(country), null,
					createStore(country), configuration, module, null, Locale.ENGLISH);

			assertEquals(1, requestCount.get());
			assertEquals(1, options.size());
			assertEquals(new BigDecimal("12.50"), options.get(0).getOptionPrice());
			assertNotNull(capturedRequest.get());

			URI uri = capturedRequest.get().getURI();
			assertEquals("http", uri.getScheme());
			assertEquals(host, uri.getHost());
			assertEquals(80, uri.getPort());
			assertEquals(path, uri.getPath());
			assertFalse(uri.toString().contains("mallory.example"));
			assertTrue(uri.getRawQuery().contains(CREDENTIAL_MARKER));
			verify(client, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
		}
	}

	private USPSShippingQuote createQuote(Country country) throws Exception {
		USPSShippingQuote quote = new USPSShippingQuote();
		CountryService countryService = mock(CountryService.class);
		Map<String, Country> countries = Collections.singletonMap("US", country);
		when(countryService.getCountriesMap(any(Language.class))).thenReturn(countries);
		ReflectionTestUtils.setField(quote, "countryService", countryService);
		ReflectionTestUtils.setField(quote, "productPriceUtils", mock(ProductPriceUtils.class));
		return quote;
	}
}
