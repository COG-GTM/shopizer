package com.salesmanager.test.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.constants.ShippingConstants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.system.ModuleConfigurationRepository;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.shipping.ShippingServiceImpl;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.business.services.system.ModuleConfigurationServiceImpl;
import com.salesmanager.core.business.utils.CacheUtils;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.ShippingConfiguration;
import com.salesmanager.core.model.shipping.ShippingType;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.ModuleConfig;

/**
 * JSONValue.parse (json-simple) -> ObjectMapper in ShippingServiceImpl and ModuleConfigurationServiceImpl.
 */
public class JacksonServiceParsingTest {

	private MerchantConfigurationService merchantConfigurationService;
	private CountryService countryService;
	private ShippingServiceImpl shippingService;
	private final MerchantStore store = new MerchantStore();

	@BeforeEach
	void setUp() {
		merchantConfigurationService = mock(MerchantConfigurationService.class);
		countryService = mock(CountryService.class);
		shippingService = new ShippingServiceImpl();
		ReflectionTestUtils.setField(shippingService, "merchantConfigurationService", merchantConfigurationService);
		ReflectionTestUtils.setField(shippingService, "countryService", countryService);
		store.setCode("DEFAULT");
	}

	private static MerchantConfiguration config(String key, String value) {
		MerchantConfiguration c = new MerchantConfiguration();
		c.setKey(key);
		c.setValue(value);
		return c;
	}

	@Test
	void supportedCountriesAreParsedFromJsonArray() throws Exception {
		when(merchantConfigurationService.getMerchantConfiguration("SUPPORTED_CNTR", store))
				.thenReturn(config("SUPPORTED_CNTR", "[\"CA\",\"US\",\"FR\"]"));
		assertEquals(List.of("CA", "US", "FR"), shippingService.getSupportedCountries(store));
	}

	@Test
	void supportedCountriesEmptyWhenNotConfiguredOrBlank() throws Exception {
		assertEquals(List.of(), shippingService.getSupportedCountries(store));
		when(merchantConfigurationService.getMerchantConfiguration("SUPPORTED_CNTR", store))
				.thenReturn(config("SUPPORTED_CNTR", "  "));
		assertEquals(List.of(), shippingService.getSupportedCountries(store));
		when(merchantConfigurationService.getMerchantConfiguration("SUPPORTED_CNTR", store))
				.thenReturn(config("SUPPORTED_CNTR", "[]"));
		assertEquals(List.of(), shippingService.getSupportedCountries(store));
	}

	@Test
	void malformedSupportedCountriesRaiseServiceException() throws Exception {
		when(merchantConfigurationService.getMerchantConfiguration("SUPPORTED_CNTR", store))
				.thenReturn(config("SUPPORTED_CNTR", "[\"CA\","));
		assertThrows(ServiceException.class, () -> shippingService.getSupportedCountries(store));
	}

	@Test
	void setSupportedCountriesWritesJsonThatGetSupportedCountriesReads() throws Exception {
		shippingService.setSupportedCountries(store, List.of("CA", "US"));
		ArgumentCaptor<MerchantConfiguration> saved = ArgumentCaptor.forClass(MerchantConfiguration.class);
		verify(merchantConfigurationService).saveOrUpdate(saved.capture());
		assertEquals("[\"CA\",\"US\"]", saved.getValue().getValue());

		when(merchantConfigurationService.getMerchantConfiguration("SUPPORTED_CNTR", store)).thenReturn(saved.getValue());
		assertEquals(List.of("CA", "US"), shippingService.getSupportedCountries(store));
	}

	@Test
	void internationalShipToCountryListUsesParsedCodes() throws Exception {
		ShippingConfiguration conf = new ShippingConfiguration();
		conf.setShippingType(ShippingType.INTERNATIONAL);
		when(merchantConfigurationService.getMerchantConfiguration(ShippingConstants.SHIPPING_CONFIGURATION, store))
				.thenReturn(config(ShippingConstants.SHIPPING_CONFIGURATION, conf.toJSONString()));
		when(merchantConfigurationService.getMerchantConfiguration("SUPPORTED_CNTR", store))
				.thenReturn(config("SUPPORTED_CNTR", "[\"CA\",\"US\"]"));
		List<Country> countries = new ArrayList<>();
		Language en = new Language();
		when(countryService.getCountries(eq(List.of("CA", "US")), any(Language.class))).thenReturn(countries);

		assertSame(countries, shippingService.getShipToCountryList(store, en));
	}

	@Test
	void shippingConfigurationSaveThenGetRoundTrip() throws Exception {
		ShippingConfiguration conf = new ShippingConfiguration();
		conf.setShippingType(ShippingType.INTERNATIONAL);
		conf.setHandlingFees(new BigDecimal("3.25"));
		conf.setFreeShippingEnabled(true);
		shippingService.saveShippingConfiguration(conf, store);
		ArgumentCaptor<MerchantConfiguration> saved = ArgumentCaptor.forClass(MerchantConfiguration.class);
		verify(merchantConfigurationService).saveOrUpdate(saved.capture());

		when(merchantConfigurationService.getMerchantConfiguration(ShippingConstants.SHIPPING_CONFIGURATION, store))
				.thenReturn(saved.getValue());
		ShippingConfiguration read = shippingService.getShippingConfiguration(store);
		assertEquals(ShippingType.INTERNATIONAL, read.getShippingType());
		assertTrue(read.isFreeShippingEnabled());
		assertEquals(0, new BigDecimal("3.25").compareTo(read.getHandlingFees()));
	}

	@Test
	void integrationModulesJsonColumnsAreParsed() throws Exception {
		ModuleConfigurationRepository repository = mock(ModuleConfigurationRepository.class);
		CacheUtils cache = mock(CacheUtils.class);
		ModuleConfigurationServiceImpl service = new ModuleConfigurationServiceImpl(repository);
		ReflectionTestUtils.setField(service, "cache", cache);

		IntegrationModule ups = new IntegrationModule();
		ups.setCode("ups");
		ups.setModule("SHIPPING");
		ups.setRegions("[\"US\",\"CA\",\"FR\",\"GB\"]");
		ups.setConfigDetails("{\"name\":\"UPS\",\"weight\":5,\"url\":\"https://www.ups.com/a/b\"}");
		ups.setConfiguration("[{\"env\":\"TEST\",\"scheme\":\"https\",\"host\":\"wwwcie.ups.com\",\"port\":\"443\",\"uri\":\"/ups.app/xml/Rate\"},"
				+ "{\"env\":\"PROD\",\"scheme\":\"https\",\"host\":\"onlinetools.ups.com\",\"port\":\"443\",\"uri\":\"/xml/Rate\",\"config1\":\"c1\"}]");
		IntegrationModule plain = new IntegrationModule();
		plain.setCode("plain");
		plain.setModule("SHIPPING");
		List<IntegrationModule> fromDb = new ArrayList<>(List.of(ups, plain));
		when(repository.findByModule("SHIPPING")).thenReturn(fromDb);

		List<IntegrationModule> modules = service.getIntegrationModules("SHIPPING");

		assertEquals(2, modules.size());
		IntegrationModule m = modules.get(0);
		assertEquals(Set.of("US", "CA", "FR", "GB"), m.getRegionsSet());
		Map<String, String> details = m.getDetails();
		assertEquals("UPS", details.get("name"));
		assertEquals("https://www.ups.com/a/b", details.get("url"));
		assertEquals("5", String.valueOf(details.get("weight")));
		Map<String, ModuleConfig> configs = m.getModuleConfigs();
		assertEquals(2, configs.size());
		assertEquals("wwwcie.ups.com", configs.get("TEST").getHost());
		assertEquals("443", configs.get("TEST").getPort());
		assertEquals("/ups.app/xml/Rate", configs.get("TEST").getUri());
		assertEquals("https", configs.get("PROD").getScheme());
		assertEquals("PROD", configs.get("PROD").getEnv());
		assertTrue(modules.get(1).getRegionsSet().isEmpty());
		assertTrue(modules.get(1).getModuleConfigs() == null || modules.get(1).getModuleConfigs().isEmpty());
		verify(cache).putInCache(fromDb, "INTEGRATION_MSHIPPING");
	}

	@Test
	void cachedIntegrationModulesAreNotReparsed() throws Exception {
		ModuleConfigurationRepository repository = mock(ModuleConfigurationRepository.class);
		CacheUtils cache = mock(CacheUtils.class);
		ModuleConfigurationServiceImpl service = new ModuleConfigurationServiceImpl(repository);
		ReflectionTestUtils.setField(service, "cache", cache);
		List<IntegrationModule> cached = new ArrayList<>();
		when(cache.getFromCache(anyString())).thenReturn(cached);
		assertSame(cached, service.getIntegrationModules("PAYMENT"));
		verify(repository, org.mockito.Mockito.never()).findByModule(anyString());
	}
}
