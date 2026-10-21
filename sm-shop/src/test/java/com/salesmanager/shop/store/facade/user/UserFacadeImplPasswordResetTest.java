package com.salesmanager.shop.store.facade.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.security.facade.SecurityFacade;
import com.salesmanager.shop.utils.EmailUtils;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LabelUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.shop.populator.user.PersistableUserPopulator;
import com.salesmanager.core.business.modules.email.Email;

@RunWith(MockitoJUnitRunner.class)
public class UserFacadeImplPasswordResetTest {

	@Mock
	private MerchantStoreService merchantStoreService;
	@Mock
	private UserService userService;
	@Mock
	private PermissionService permissionService;
	@Mock
	private LanguageService languageService;
	@Mock
	private PersistableUserPopulator persistableUserPopulator;
	@Mock
	private SecurityFacade securityFacade;
	@Mock
	private EmailUtils emailUtils;
	@Mock
	private EmailService emailService;
	@Mock
	private ImageFilePath imageUtils;
	@Mock
	private LabelUtils messages;
	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserFacadeImpl userFacade;

	private MerchantStore store;
	private Language language;
	private User user;

	@Before
	public void setUp() throws Exception {
		store = store("localhost:8080");
		language = new Language();
		user = new User();
		user.setAdminEmail("admin@shopizer.com");
		user.setFirstName("Admin");

		ReflectionTestUtils.setField(userFacade, "passwordResetAllowedHosts", "");
		ReflectionTestUtils.setField(userFacade, "lamguageService", languageService);
		ReflectionTestUtils.setField(userFacade, "emailUtils", emailUtils);
		ReflectionTestUtils.setField(userFacade, "emailService", emailService);
		ReflectionTestUtils.setField(userFacade, "imageUtils", imageUtils);
		ReflectionTestUtils.setField(userFacade, "messages", messages);
		ReflectionTestUtils.setField(userFacade, "passwordEncoder", passwordEncoder);

		lenient().when(languageService.toLocale(any(Language.class), any(MerchantStore.class))).thenReturn(Locale.ENGLISH);
		lenient().when(imageUtils.getContextPath()).thenReturn("/shop");
		lenient().when(emailUtils.createEmailObjectsMap(anyString(), any(MerchantStore.class), eq(messages), eq(Locale.ENGLISH)))
				.thenReturn(new HashMap<>());
		lenient().when(messages.getMessage(anyString(), any(Locale.class))).thenReturn("message");
		lenient().when(messages.getMessage(anyString(), any(String[].class), any(Locale.class))).thenReturn("message");
		lenient().when(userService.getByUserName(anyString(), anyString())).thenReturn(user);
	}

	@Test
	public void rejectsUntrustedReturnUrlsBeforeLookingUpUser() {
		String[] rejectedUrls = {
				"https://evil.example",
				"http://localhost:8080@evil.example",
				"http://localhost:8080\\@evil.example",
				"http://evil.example#@localhost:8080",
				"javascript://localhost:8080/x",
				"ftp://localhost:8080/",
				"//evil.example/admin",
				"not a url",
				"http:///admin",
				"http://localhost.evil.example/admin",
				"http://evillocalhost:8080/admin"
		};

		for (String returnUrl : rejectedUrls) {
			try {
				userFacade.requestPasswordReset("admin@shopizer.com", returnUrl, store, language);
				fail("Expected rejection for " + returnUrl);
			} catch (ServiceRuntimeException exception) {
				assertEquals("Expected error code 400 for " + returnUrl, "400", exception.getErrorCode());
			}
		}

		verifyNoInteractions(userService, emailService);
	}

	@Test
	public void acceptsStoreHostIgnoringSchemeCaseAndPort() throws Exception {
		assertResetLink("http://localhost:8080/admin");
		assertResetLink("HTTP://LOCALHOST:8080/admin");
		assertResetLink("https://localhost:4200");
	}

	@Test
	public void acceptsHostsFromConfiguredProperty() throws Exception {
		ReflectionTestUtils.setField(userFacade, "passwordResetAllowedHosts",
				" admin.example.com , https://Backoffice.Example.org:8443/x ,");

		assertResetLink("https://admin.example.com/a");
		assertResetLink("http://backoffice.example.org");

		try {
			userFacade.requestPasswordReset("admin@shopizer.com", "https://evil.example", store, language);
			fail("Expected rejection for an unconfigured host");
		} catch (ServiceRuntimeException exception) {
			assertEquals("400", exception.getErrorCode());
		}
	}

	@Test
	public void acceptsStoreDomainConfiguredAsFullUrl() throws Exception {
		store.setDomainName("https://Shop.Example.com/");

		assertResetLink("http://shop.example.com/admin");
	}

	@Test
	public void rejectsStoreWithoutDomainWhenNoHostsAreConfigured() {
		for (String domain : new String[] { null, " " }) {
			store.setDomainName(domain);
			try {
				userFacade.requestPasswordReset("admin@shopizer.com", "http://localhost:8080/admin", store, language);
				fail("Expected rejection for blank store domain");
			} catch (ServiceRuntimeException exception) {
				assertEquals("400", exception.getErrorCode());
			}
		}

		verifyNoInteractions(userService, emailService);
	}

	@Test
	public void clearsTokenAndEncodesPasswordWhenResetting() throws Exception {
		CredentialsReset credentialsReset = credentialsReset("token", new Date(System.currentTimeMillis() + 86_400_000L));
		user.setCredentialsResetRequest(credentialsReset);
		when(userService.getByPasswordResetToken("DEFAULT", "token")).thenReturn(user);
		when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");

		userFacade.resetPassword("new-password", "token", "DEFAULT");

		assertNull(user.getCredentialsResetRequest());
		assertEquals("encoded-password", user.getAdminPassword());
		verify(userService).save(user);
	}

	@Test
	public void rejectsExpiredTokenWithoutSavingUser() throws Exception {
		user.setCredentialsResetRequest(credentialsReset("expired", new Date(System.currentTimeMillis() - 86_400_000L)));
		when(userService.getByPasswordResetToken("DEFAULT", "expired")).thenReturn(user);

		try {
			userFacade.resetPassword("new-password", "expired", "DEFAULT");
			fail("Expected expired token rejection");
		} catch (RuntimeException expected) {
			assertNotNull(expected);
		}

		org.mockito.Mockito.verify(userService, org.mockito.Mockito.never()).save(any(User.class));
	}

	private void assertResetLink(String returnUrl) throws Exception {
		userFacade.requestPasswordReset("admin@shopizer.com", returnUrl, store, language);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userService).saveOrUpdate(userCaptor.capture());
		CredentialsReset reset = userCaptor.getValue().getCredentialsResetRequest();
		assertNotNull(reset);
		String token = reset.getCredentialsRequest();
		assertEquals(UUID.fromString(token).toString(), token);

		ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(eq(store), emailCaptor.capture());
		assertEquals(returnUrl + "/user/DEFAULT/reset/" + token,
				emailCaptor.getValue().getTemplateTokens().get("RESET_PASSWORD_LINK"));
		org.mockito.Mockito.reset(userService, emailService);
		lenient().when(userService.getByUserName(anyString(), anyString())).thenReturn(user);
	}

	private static MerchantStore store(String domainName) {
		MerchantStore merchantStore = new MerchantStore();
		merchantStore.setCode("DEFAULT");
		merchantStore.setDomainName(domainName);
		merchantStore.setStorename("Store");
		merchantStore.setStoreEmailAddress("store@example.com");
		return merchantStore;
	}

	private static CredentialsReset credentialsReset(String token, Date expiry) {
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(token);
		reset.setCredentialsRequestExpiry(expiry);
		return reset;
	}
}
