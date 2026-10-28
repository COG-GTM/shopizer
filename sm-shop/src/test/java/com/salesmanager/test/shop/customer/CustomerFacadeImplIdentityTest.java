package com.salesmanager.test.shop.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.customer.optin.CustomerOptinService;
import com.salesmanager.core.business.services.customer.review.CustomerReviewService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.reference.zone.ZoneService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.system.optin.OptinService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.utils.CoreConfiguration;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.populator.customer.CustomerPopulator;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl;
import com.salesmanager.shop.utils.EmailTemplatesUtils;
import com.salesmanager.shop.utils.EmailUtils;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LabelUtils;

@RunWith(MockitoJUnitRunner.class)
public class CustomerFacadeImplIdentityTest {

    @Mock
    private CustomerService customerService;

    @Mock
    private CustomerPopulator customerPopulator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GroupService groupService;

    @Mock
    private EmailTemplatesUtils emailTemplatesUtils;

    @Mock
    private CoreConfiguration coreConfiguration;

    @Mock
    private OptinService optinService;

    @Mock
    private CustomerOptinService customerOptinService;

    @Mock
    private ShoppingCartService shoppingCartService;

    @Mock
    private LanguageService languageService;

    @Mock
    private LabelUtils messages;

    @Mock
    private CountryService countryService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private ZoneService zoneService;

    @Mock
    private EmailService emailService;

    @Mock
    private AuthenticationManager customerAuthenticationManager;

    @Mock
    private CustomerReviewService customerReviewService;

    @Mock
    private EmailUtils emailUtils;

    @Mock
    private ImageFilePath imageUtils;

    @InjectMocks
    private CustomerFacadeImpl customerFacade;

    private MerchantStore store;
    private Language language;

    @Before
    public void setUp() throws Exception {
        language = new Language();
        language.setCode("en");
        store = new MerchantStore();
        store.setDefaultLanguage(language);
        when(groupService.listGroup(any(GroupType.class))).thenReturn(Collections.emptyList());
        when(coreConfiguration.getProperty(anyString())).thenReturn("http");
        doNothing().when(emailTemplatesUtils).sendRegistrationEmail(any(PersistableCustomer.class),
                any(MerchantStore.class), any(), anyString());
    }

    @Test
    public void registerCustomerClearsBodyIdBeforePopulateAndSave() throws Exception {
        PersistableCustomer source = new PersistableCustomer();
        source.setId(42L);
        Customer populated = new Customer();
        source.setUserName("register-" + UUID.randomUUID() + "@example.com");
        source.setEmailAddress(source.getUserName());
        Long[] ids = new Long[2];
        when(customerService.getByNick(source.getUserName())).thenReturn(null);
        when(customerPopulator.populate(any(PersistableCustomer.class), any(MerchantStore.class),
                any(Language.class))).thenAnswer(invocation -> {
                    ids[0] = invocation.getArgument(0, PersistableCustomer.class).getId();
                    return populated;
                });
        doAnswer(invocation -> {
            ids[1] = invocation.getArgument(0, Customer.class).getId();
            populated.setId(100L);
            return null;
        }).when(customerService).saveOrUpdate(populated);

        PersistableCustomer result = customerFacade.registerCustomer(source, store, language);

        assertNull(ids[0]);
        assertNull(ids[1]);
        assertEquals(Long.valueOf(100L), result.getId());
    }

    @Test
    public void adminCreateClearsBodyIdBeforePopulateAndSave() throws Exception {
        PersistableCustomer source = new PersistableCustomer();
        source.setId(42L);
        source.setEmailAddress("create-" + UUID.randomUUID() + "@example.com");
        source.setPassword("cleartext");
        Long[] ids = new Long[3];
        when(customerService.getByNick(source.getEmailAddress())).thenReturn(null);
        when(customerPopulator.populate(any(PersistableCustomer.class), any(Customer.class),
                any(MerchantStore.class), any(Language.class))).thenAnswer(invocation -> {
                    ids[0] = invocation.getArgument(0, PersistableCustomer.class).getId();
                    Customer target = invocation.getArgument(1, Customer.class);
                    ids[1] = target.getId();
                    target.setDefaultLanguage(language);
                    target.setEmailAddress(source.getEmailAddress());
                    target.setNick(source.getEmailAddress());
                    target.setPassword("ENC");
                    return target;
        });
        doAnswer(invocation -> {
            Customer customer = invocation.getArgument(0, Customer.class);
            ids[2] = customer.getId();
            customer.setId(200L);
            return null;
        }).when(customerService).save(any(Customer.class));

        ReadableCustomer result = customerFacade.create(source, store, language);

        assertNull(ids[0]);
        assertNull(ids[1]);
        assertNull(ids[2]);
        assertEquals(Long.valueOf(200L), result.getId());
    }

    @Test
    public void adminUpdateKeepsLoadedEntityId() throws Exception {
        PersistableCustomer source = new PersistableCustomer();
        source.setId(7L);
        Customer loaded = new Customer();
        loaded.setId(7L);
        when(customerService.getById(7L)).thenReturn(loaded);
        doNothing().when(customerService).save(loaded);

        customerFacade.update(source, store);

        verify(customerService).save(loaded);
        assertEquals(Long.valueOf(7L), loaded.getId());
    }
}
