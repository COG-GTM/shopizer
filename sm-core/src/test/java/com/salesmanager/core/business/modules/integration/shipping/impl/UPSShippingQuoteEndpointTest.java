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

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.mockito.MockedStatic;

import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.IntegrationException;

public class UPSShippingQuoteEndpointTest extends CarrierShippingQuoteTestSupport {

	private static final String RESPONSE_XML = "<?xml version=\"1.0\"?>"
			+ "<RatingServiceSelectionResponse><Response><ResponseStatusCode>1</ResponseStatusCode>"
			+ "<ResponseStatusDescription>Success</ResponseStatusDescription></Response>"
			+ "<RatedShipment><Service><Code>03</Code></Service><TotalCharges>"
			+ "<MonetaryValue>12.50</MonetaryValue></TotalCharges>"
			+ "<GuaranteedDaysToDelivery>1</GuaranteedDaysToDelivery></RatedShipment>"
			+ "</RatingServiceSelectionResponse>";

	@Test
	public void usesPinnedTestEndpointAndParsesCarrierResponse() throws Exception {
		assertPinnedEndpoint("TEST", "https", "wwwcie.ups.com", 443, "/ups.app/xml/Rate");
	}

	@Test
	public void usesPinnedProductionEndpointAndParsesCarrierResponse() throws Exception {
		assertPinnedEndpoint("PROD", "https", "onlinetools.ups.com", 443, "/xml/Rate");
	}

	@Test
	public void doesNotSendRequestForUnsupportedEnvironment() throws Exception {
		AtomicReference<HttpUriRequest> capturedRequest = new AtomicReference<HttpUriRequest>();
		AtomicInteger requestCount = new AtomicInteger();
		CloseableHttpClient client = createClient(RESPONSE_XML, capturedRequest, requestCount);
		Country country = createUnitedStates();
		IntegrationConfiguration configuration = createConfiguration("STAGING", true);

		try (MockedStatic<HttpClients> clients = mockStatic(HttpClients.class)) {
			clients.when(HttpClients::createDefault).thenReturn(client);
			try {
				new UPSShippingQuote().getShippingQuotes(null, createPackages(), BigDecimal.ONE,
						createDelivery(country), null, createStore(country), configuration,
						createModule("ups"), null, Locale.ENGLISH);
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
				+ "<!DOCTYPE RatingServiceSelectionResponse [<!ENTITY xxe SYSTEM \"SECRET_URI\">]>"
				+ "<RatingServiceSelectionResponse><RatedShipment><Service><Code>03</Code></Service>"
				+ "<TotalCharges><MonetaryValue>&xxe;</MonetaryValue></TotalCharges>"
				+ "</RatedShipment></RatingServiceSelectionResponse>";
		Country country = createUnitedStates();
		IntegrationConfiguration configuration = createConfiguration("TEST", true);
		IntegrationModule module = createModule("ups");

		IntegrationException expected = requestWithXxe(
				() -> new UPSShippingQuote().getShippingQuotes(null, createPackages(), BigDecimal.ONE,
						createDelivery(country), null, createStore(country), configuration, module,
						null, Locale.ENGLISH),
				xxeResponse);
		assertTrue(hasCause(expected, org.xml.sax.SAXException.class));
	}

	private void assertPinnedEndpoint(String environment, String scheme, String host, int port,
			String path) throws Exception {
		AtomicReference<HttpUriRequest> capturedRequest = new AtomicReference<HttpUriRequest>();
		AtomicInteger requestCount = new AtomicInteger();
		CloseableHttpClient client = createClient(RESPONSE_XML, capturedRequest, requestCount);
		Country country = createUnitedStates();
		IntegrationConfiguration configuration = createConfiguration(environment, true);
		IntegrationModule module = createModule("ups");

		try (MockedStatic<HttpClients> clients = mockStatic(HttpClients.class)) {
			clients.when(HttpClients::createDefault).thenReturn(client);
			List<ShippingOption> options = new UPSShippingQuote().getShippingQuotes(
					null, createPackages(), BigDecimal.ONE, createDelivery(country), null,
					createStore(country), configuration, module, null, Locale.ENGLISH);

			assertEquals(1, requestCount.get());
			assertEquals(1, options.size());
			assertEquals(new BigDecimal("12.50"), options.get(0).getOptionPrice());
			assertEquals("UPS Ground", options.get(0).getOptionName());
			assertNotNull(capturedRequest.get());

			URI uri = capturedRequest.get().getURI();
			assertEquals(scheme, uri.getScheme());
			assertEquals(host, uri.getHost());
			assertEquals(port, uri.getPort());
			assertEquals(path, uri.getPath());
			assertFalse(uri.toString().contains("mallory.example"));

			String requestBody = EntityUtils.toString(((HttpPost) capturedRequest.get()).getEntity());
			assertTrue(requestBody.contains(CREDENTIAL_MARKER));
			assertTrue(requestBody.contains("test-user"));
			assertTrue(requestBody.contains("test-password"));
			verify(client, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
		}
	}
}
