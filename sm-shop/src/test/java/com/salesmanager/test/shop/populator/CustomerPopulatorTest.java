package com.salesmanager.test.shop.populator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.services.customer.attribute.CustomerOptionService;
import com.salesmanager.core.business.services.customer.attribute.CustomerOptionValueService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.reference.zone.ZoneService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.populator.customer.CustomerPopulator;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CustomerPopulatorTest {

	@Mock
	private CountryService countryService;
	@Mock
	private ZoneService zoneService;
	@Mock
	private LanguageService languageService;
	@Mock
	private CustomerOptionService customerOptionService;
	@Mock
	private CustomerOptionValueService customerOptionValueService;
	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private CustomerPopulator populator;

	private MerchantStore store;
	private Language language;

	@Before
	public void setUp() throws Exception {
		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");
		language = new Language("en");

		Country belgium = new Country("BE");
		Map<String, Country> countries = new HashMap<>();
		countries.put("BE", belgium);
		when(countryService.getCountriesMap(any(Language.class))).thenReturn(countries);
		when(zoneService.getZones(any(Language.class))).thenReturn(Collections.emptyMap());
		when(languageService.getByCode(anyString())).thenReturn(language);
		when(passwordEncoder.encode(anyString())).thenAnswer(i -> "ENC(" + i.getArgument(0) + ")");
	}

	private PersistableCustomer request(Long id) {
		PersistableCustomer source = new PersistableCustomer();
		source.setId(id);
		source.setEmailAddress("new@test.com");
		source.setUserName("new@test.com");
		source.setPassword("secret123");
		source.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("First");
		billing.setLastName("Last");
		billing.setCountry("BE");
		source.setBilling(billing);
		return source;
	}

	@Test
	public void newCustomerIgnoresClientSuppliedId() throws Exception {
		Customer target = populator.populate(request(42L), new Customer(), store, language);
		assertNull("client supplied id must not be copied onto a new entity", target.getId());
	}

	@Test
	public void createTargetVariantIgnoresClientSuppliedId() throws Exception {
		Customer target = populator.populate(request(42L), store, language);
		assertNull(target.getId());
	}

	@Test
	public void existingEntityKeepsItsOwnIdWhenRequestCarriesAnotherId() throws Exception {
		Customer existing = new Customer();
		existing.setId(7L);
		Customer target = populator.populate(request(42L), existing, store, language);
		assertSame(existing, target);
		assertEquals(Long.valueOf(7L), target.getId());
	}

	@Test
	public void otherFieldsAreStillPopulated() throws Exception {
		Customer target = populator.populate(request(42L), new Customer(), store, language);
		assertEquals("new@test.com", target.getEmailAddress());
		assertEquals("new@test.com", target.getNick());
		assertEquals("ENC(secret123)", target.getPassword());
		assertFalse(target.isAnonymous());
		assertSame(store, target.getMerchantStore());
		assertEquals("First", target.getBilling().getFirstName());
		assertEquals("Last", target.getBilling().getLastName());
		assertEquals("BE", target.getBilling().getCountry().getIsoCode());
		assertSame(language, target.getDefaultLanguage());
	}
}
