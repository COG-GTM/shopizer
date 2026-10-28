package com.salesmanager.test.shop.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

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
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.populator.customer.CustomerPopulator;

@RunWith(MockitoJUnitRunner.class)
public class CustomerPopulatorIdTest {

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
    private CustomerPopulator customerPopulator;

    private Language language;
    private MerchantStore store;

    @Before
    public void setUp() throws Exception {
        language = new Language();
        language.setCode("en");
        store = new MerchantStore();
        when(countryService.getCountriesMap(any(Language.class))).thenReturn(Collections.emptyMap());
        when(zoneService.getZones(any(Language.class))).thenReturn(Collections.emptyMap());
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
    }

    @Test
    public void newTargetDoesNotTakeBodyId() throws Exception {
        PersistableCustomer source = new PersistableCustomer();
        source.setId(42L);

        Customer target = customerPopulator.populate(source, new Customer(), store, language);

        assertNull(target.getId());
    }

    @Test
    public void existingTargetKeepsItsOwnId() throws Exception {
        PersistableCustomer source = new PersistableCustomer();
        source.setId(42L);
        Customer existing = new Customer();
        existing.setId(7L);

        Customer target = customerPopulator.populate(source, existing, store, language);

        assertEquals(Long.valueOf(7L), target.getId());
    }

    @Test
    public void createdTargetIgnoresBodyIdButMapsOtherFields() throws Exception {
        PersistableCustomer source = new PersistableCustomer();
        source.setId(42L);
        source.setEmailAddress("customer-" + UUID.randomUUID() + "@example.com");
        source.setUserName(source.getEmailAddress());
        source.setPassword("cleartext");

        Customer target = customerPopulator.populate(source, store, language);

        assertNull(target.getId());
        assertEquals(source.getEmailAddress(), target.getEmailAddress());
        assertEquals("ENC", target.getPassword());
        assertFalse(target.isAnonymous());
        assertSame(store, target.getMerchantStore());
    }
}
