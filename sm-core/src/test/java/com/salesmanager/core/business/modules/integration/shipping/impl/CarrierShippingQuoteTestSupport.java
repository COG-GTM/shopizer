package com.salesmanager.core.business.modules.integration.shipping.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.model.system.ModuleConfig;
import com.salesmanager.core.modules.integration.IntegrationException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class CarrierShippingQuoteTestSupport {

	protected static final String CREDENTIAL_MARKER = "test-credential";
	protected static final String SECRET_MARKER = "carrier-secret-marker";

	protected MerchantStore createStore(Country country) {
		MerchantStore store = new MerchantStore();
		store.setCountry(country);
		store.setStorecity("San Francisco");
		store.setStorepostalcode("94105");
		store.setDefaultLanguage(new Language());
		store.setWeightunitcode("LB");
		store.setSeizeunitcode("IN");
		return store;
	}

	protected Country createUnitedStates() {
		Country country = new Country();
		country.setIsoCode("US");
		return country;
	}

	protected Delivery createDelivery(Country country) {
		Delivery delivery = new Delivery();
		delivery.setCountry(country);
		delivery.setPostalCode("94105");
		delivery.setCity("San Francisco");
		return delivery;
	}

	protected List<PackageDetails> createPackages() {
		PackageDetails packageDetails = new PackageDetails();
		packageDetails.setShippingWeight(1.0);
		packageDetails.setShippingLength(1.0);
		packageDetails.setShippingWidth(1.0);
		packageDetails.setShippingHeight(1.0);
		return Arrays.asList(packageDetails);
	}

	protected IntegrationConfiguration createConfiguration(String environment, boolean ups) {
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setEnvironment(environment);

		Map<String, String> keys = new HashMap<String, String>();
		if (ups) {
			keys.put("accessKey", CREDENTIAL_MARKER);
			keys.put("userId", "test-user");
			keys.put("password", "test-password");
		} else {
			keys.put("account", CREDENTIAL_MARKER);
		}
		configuration.setIntegrationKeys(keys);

		Map<String, List<String>> options = new HashMap<String, List<String>>();
		options.put("packages", Arrays.asList("02"));
		configuration.setIntegrationOptions(options);
		return configuration;
	}

	protected IntegrationModule createModule(String code) {
		IntegrationModule module = new IntegrationModule();
		module.setCode(code);
		module.getRegionsSet().add("US");
		Map<String, String> details = new HashMap<String, String>();
		details.put("03", "UPS Ground");
		module.setDetails(details);

		Map<String, ModuleConfig> moduleConfigs = new HashMap<String, ModuleConfig>();
		for (String environment : Arrays.asList("TEST", "PROD")) {
			ModuleConfig moduleConfig = new ModuleConfig();
			moduleConfig.setEnv(environment);
			moduleConfig.setScheme("http");
			moduleConfig.setHost("mallory.example");
			moduleConfig.setPort("8080");
			moduleConfig.setUri("/steal");
			moduleConfigs.put(environment, moduleConfig);
		}
		module.setModuleConfigs(moduleConfigs);
		return module;
	}

	protected CloseableHttpClient createClient(String responseXml,
			AtomicReference<HttpUriRequest> capturedRequest, AtomicInteger requestCount)
			throws IOException {
		CloseableHttpClient client = mock(CloseableHttpClient.class);
		HttpResponse response = new BasicHttpResponse(
				new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
		response.setEntity(new StringEntity(responseXml, ContentType.APPLICATION_XML));

		when(client.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
				.thenAnswer(invocation -> {
					capturedRequest.set(invocation.getArgument(0));
					requestCount.incrementAndGet();
					@SuppressWarnings("unchecked")
					ResponseHandler<String> handler = invocation.getArgument(1);
					return handler.handleResponse(response);
				});
		return client;
	}

	protected boolean hasCause(Throwable error, Class<? extends Throwable> causeType) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (causeType.isInstance(cause)) {
				return true;
			}
		}
		return false;
	}

	protected IntegrationException requestWithXxe(QuoteCall quoteCall, String carrierResponse)
			throws Exception {
		java.io.File secret = java.io.File.createTempFile("carrier-xxe", ".txt");
		secret.deleteOnExit();
		java.nio.file.Files.write(secret.toPath(), SECRET_MARKER.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		String xml = carrierResponse.replace("SECRET_URI", secret.toURI().toString());

		AtomicReference<HttpUriRequest> capturedRequest = new AtomicReference<HttpUriRequest>();
		AtomicInteger requestCount = new AtomicInteger();
		CloseableHttpClient client = createClient(xml, capturedRequest, requestCount);
		try (org.mockito.MockedStatic<org.apache.http.impl.client.HttpClients> clients =
				org.mockito.Mockito.mockStatic(org.apache.http.impl.client.HttpClients.class)) {
			clients.when(org.apache.http.impl.client.HttpClients::createDefault).thenReturn(client);
			try {
				quoteCall.getQuote();
				throw new AssertionError("Expected an unsafe DOCTYPE to be rejected");
			} catch (IntegrationException expected) {
				org.junit.Assert.assertEquals(1, requestCount.get());
				org.junit.Assert.assertNotNull(capturedRequest.get());
				org.junit.Assert.assertFalse(expected.toString().contains(SECRET_MARKER));
				return expected;
			}
		}
	}

	@FunctionalInterface
	protected interface QuoteCall {
		Object getQuote() throws Exception;
	}
}
