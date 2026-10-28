package com.salesmanager.shop.store.facade.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.facade.customer.CustomerFacadeImpl;
import com.salesmanager.shop.utils.EmailUtils;
import com.salesmanager.shop.utils.FilePathUtils;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LabelUtils;
import com.salesmanager.core.business.modules.email.Email;

@RunWith(MockitoJUnitRunner.class)
public class CustomerFacadeImplPasswordResetTest {

	@InjectMocks
	private CustomerFacadeImpl facade;
	@Spy
	private FilePathUtils filePathUtils = new FilePathUtils();
	@Mock
	private CustomerService customerService;
	@Mock
	private LanguageService languageService;
	@Mock
	private EmailUtils emailUtils;
	@Mock
	private EmailService emailService;
	@Mock
	private ImageFilePath imageUtils;
	@Mock
	private LabelUtils messages;
	@Mock
	private com.salesmanager.shop.store.controller.customer.facade.CustomerFacade legacyFacade;
	@Mock
	private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

	private MerchantStore store;
	private Customer customer;

	@Before
	public void setUp() {
		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");
		store.setDomainName("localhost:8080");
		store.setStorename("Shop");
		store.setStoreEmailAddress("owner@example.com");
		customer = new Customer();
		customer.setNick("customer@example.com");
		customer.setEmailAddress("customer@example.com");
		Billing billing = new Billing();
		billing.setFirstName("Customer");
		customer.setBilling(billing);
		filePathUtils.properties.setProperty(FilePathUtils.PASSWORD_RESET_ALLOWED_ORIGINS,
				"http://localhost:4200");
		when(languageService.toLocale(any(Language.class), any(MerchantStore.class))).thenReturn(Locale.ENGLISH);
		lenient().when(imageUtils.getContextPath()).thenReturn("");
	}

	@Test
	public void rejectsMaliciousUrlBeforeTokenOrEmail() {
		assertRejected("\"><img src=\"https://mallory.example/leak?t=");
		verifyNoInteractions(customerService, emailService);
	}

	@Test
	public void rejectsForeignOriginBeforeTokenOrEmail() {
		assertRejected("https://mallory.example/");
		verifyNoInteractions(customerService, emailService);
	}

	@Test
	public void relativeReturnUrlCreatesCustomerResetEmail() throws Exception {
		when(customerService.getByNick("customer@example.com", 1)).thenReturn(customer);
		Map<String, String> tokens = new HashMap<>();
		when(emailUtils.createEmailObjectsMap(anyString(), any(MerchantStore.class), any(LabelUtils.class),
				any(Locale.class))).thenReturn(tokens);

		facade.requestPasswordReset("customer@example.com", "/shop", store, new Language());

		ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
		verify(customerService).saveOrUpdate(saved.capture());
		String token = saved.getValue().getCredentialsResetRequest().getCredentialsRequest();
		assertNotNull(token);
		ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(any(MerchantStore.class), email.capture());
		assertEquals("https://localhost:8080/shop/customer/DEFAULT/reset/" + token,
				tokens.get("RESET_PASSWORD_LINK"));
		assertEquals("email_template_password_reset_request_customer.ftl", email.getValue().getTemplateName());
	}

	@Test
	public void blankReturnUrlUsesStoreOrigin() throws Exception {
		when(customerService.getByNick("customer@example.com", 1)).thenReturn(customer);
		Map<String, String> tokens = new HashMap<>();
		when(emailUtils.createEmailObjectsMap(anyString(), any(MerchantStore.class), any(LabelUtils.class),
				any(Locale.class))).thenReturn(tokens);

		facade.requestPasswordReset("customer@example.com", "", store, new Language());

		verify(customerService).saveOrUpdate(any(Customer.class));
		verify(emailService).sendHtmlEmail(any(MerchantStore.class), any(Email.class));
		assertEquals("https://localhost:8080/customer/DEFAULT/reset/"
				+ customer.getCredentialsResetRequest().getCredentialsRequest(),
				tokens.get("RESET_PASSWORD_LINK"));
	}

	@Test
	public void unknownCustomerDoesNotSendEmail() {
		when(customerService.getByNick("unknown@example.com", 1)).thenReturn(null);
		try {
			facade.requestPasswordReset("unknown@example.com", "/shop", store, new Language());
		} catch (ServiceRuntimeException expected) {
			verifyNoInteractions(emailService);
			return;
		}
		throw new AssertionError("expected ServiceRuntimeException");
	}

	private void assertRejected(String returnUrl) {
		try {
			facade.requestPasswordReset("customer@example.com", returnUrl, store, new Language());
		} catch (ServiceRuntimeException e) {
			assertEquals("400", e.getErrorCode());
			return;
		}
		throw new AssertionError("expected bad request");
	}
}
