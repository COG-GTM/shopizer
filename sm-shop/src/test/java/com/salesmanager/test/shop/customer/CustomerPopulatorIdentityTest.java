package com.salesmanager.test.shop.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.reference.zone.ZoneService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.populator.customer.CustomerPopulator;

@RunWith(MockitoJUnitRunner.class)
public class CustomerPopulatorIdentityTest {

    @Mock private CountryService countryService;
    @Mock private ZoneService zoneService;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private CustomerPopulator populator;

    private final MerchantStore store = new MerchantStore();
    private final Language language = new Language();

    @Before
    public void setUp() throws Exception {
        when(countryService.getCountriesMap(language)).thenReturn(Collections.emptyMap());
        when(zoneService.getZones(language)).thenReturn(Collections.emptyMap());
    }

    @Test
    public void newTargetLeavesIdentityForPersistence() throws Exception {
        PersistableCustomer source = new PersistableCustomer();
        source.setId(42L);

        Customer result = populator.populate(source, store, language);

        assertNull(result.getId());
        assertEquals(Long.valueOf(42), source.getId());
        assertSame(store, result.getMerchantStore());
        assertSame(language, result.getDefaultLanguage());
    }

    @Test
    public void existingTargetKeepsItsIdentityWhenMappingEditableFields() throws Exception {
        Customer target = new Customer();
        target.setId(7L);
        PersistableCustomer source = new PersistableCustomer();
        source.setId(42L);
        source.setEmailAddress("updated@example.test");

        Customer result = populator.populate(source, target, store, language);

        assertSame(target, result);
        assertEquals(Long.valueOf(7), result.getId());
        assertEquals("updated@example.test", result.getEmailAddress());
    }

    @Test
    public void updateWithoutPasswordPreservesCredentialsAndIdentity() throws Exception {
        Customer target = new Customer();
        target.setId(7L);
        target.setNick("customer@example.test");
        target.setPassword("stored-password-hash");
        PersistableCustomer source = new PersistableCustomer();
        source.setPassword(" ");

        populator.populate(source, target, store, language);

        assertEquals(Long.valueOf(7), target.getId());
        assertEquals("customer@example.test", target.getNick());
        assertEquals("stored-password-hash", target.getPassword());
    }

    @Test
    public void newCustomerStillMapsEncodedCredentials() throws Exception {
        PersistableCustomer source = new PersistableCustomer();
        source.setUserName("customer@example.test");
        source.setPassword("test-password");
        when(passwordEncoder.encode("test-password")).thenReturn("encoded-password");

        Customer result = populator.populate(source, store, language);

        assertNull(result.getId());
        assertEquals("customer@example.test", result.getNick());
        assertEquals("encoded-password", result.getPassword());
        assertFalse(result.isAnonymous());
    }
}
