package com.salesmanager.shop.store.facade.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.utils.EmailUtils;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LabelUtils;

/**
 * Unit tests for the admin password reset return URL allow-list and single-use
 * tokens in {@link UserFacadeImpl}.
 */
public class UserFacadeImplPasswordResetTest {

	private static final String USER_NAME = "admin@shopizer.com";
	private static final String STORE_CODE = "DEFAULT";

	private UserFacadeImpl facade;
	private UserService userService;
	private EmailService emailService;
	private PasswordEncoder passwordEncoder;
	private MerchantStore store;
	private User user;

	@Before
	public void setUp() throws Exception {
		facade = new UserFacadeImpl();
		userService = mock(UserService.class);
		emailService = mock(EmailService.class);
		passwordEncoder = mock(PasswordEncoder.class);

		ReflectionTestUtils.setField(facade, "userService", userService);
		ReflectionTestUtils.setField(facade, "emailService", emailService);
		ReflectionTestUtils.setField(facade, "passwordEncoder", passwordEncoder);
		ReflectionTestUtils.setField(facade, "lamguageService", mock(LanguageService.class));
		ReflectionTestUtils.setField(facade, "emailUtils", mock(EmailUtils.class));
		ReflectionTestUtils.setField(facade, "imageUtils", mock(ImageFilePath.class));
		ReflectionTestUtils.setField(facade, "messages", mock(LabelUtils.class));
		ReflectionTestUtils.setField(facade, "passwordResetAllowedHosts", "");

		store = new MerchantStore();
		store.setCode(STORE_CODE);
		store.setStorename("Default store");
		store.setStoreEmailAddress("store@shopizer.com");
		store.setDomainName("localhost:8080");

		user = new User();
		user.setAdminName(USER_NAME);
		user.setAdminEmail(USER_NAME);
		when(userService.getByUserName(USER_NAME, STORE_CODE)).thenReturn(user);
	}

	private void request(String returnUrl) {
		facade.requestPasswordReset(USER_NAME, returnUrl, store, new Language("en"));
	}

	private void assertRejected(String returnUrl) throws Exception {
		try {
			request(returnUrl);
			fail("Expected return url to be rejected: " + returnUrl);
		} catch (ServiceRuntimeException e) {
			assertEquals("400", e.getErrorCode());
		}
	}

	private String assertAcceptedAndGetLink(String returnUrl) throws Exception {
		request(returnUrl);
		ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(eq(store), email.capture());
		return email.getValue().getTemplateTokens().get("RESET_PASSWORD_LINK");
	}

	@Test
	public void rejectsForeignHostsBeforeTouchingUserOrEmail() throws Exception {
		String[] rejected = { "https://evil.example", "http://evil.example/admin", "http://localhost.evil.example:8080",
				"http://evil.example/localhost:8080", "http://evil.example?h=localhost:8080",
				"http://evil.example#localhost:8080", "http://localhost:8080@evil.example",
				"http://user:pass@localhost:8080/admin", "javascript://localhost:8080/x", "ftp://localhost:8080/x",
				"file://localhost/etc/passwd", "localhost:8080/admin", "not a url", "", "http:///admin",
				"http://local host:8080/admin" };
		for (String returnUrl : rejected) {
			assertRejected(returnUrl);
		}
		verifyNoInteractions(userService);
		verifyNoInteractions(emailService);
		assertNull(user.getCredentialsResetRequest());
	}

	@Test
	public void rejectsNullReturnUrl() {
		try {
			request(null);
			fail("Expected null return url to be rejected");
		} catch (NullPointerException expected) {
			// Validate.notNull
		}
		verifyNoInteractions(userService);
	}

	@Test
	public void acceptsStoreDomainHostAndBuildsLinkOnIt() throws Exception {
		String link = assertAcceptedAndGetLink("http://localhost:8080/admin");

		assertTrue(link, link.startsWith("http://localhost:8080/admin/user/DEFAULT/reset/"));
		String token = user.getCredentialsResetRequest().getCredentialsRequest();
		assertEquals("http://localhost:8080/admin/user/DEFAULT/reset/" + token, link);
		assertTrue(user.getCredentialsResetRequest().getCredentialsRequestExpiry().after(new Date()));
		verify(userService).saveOrUpdate(user);
	}

	@Test
	public void acceptsStoreHostOnAnotherPortAndScheme() throws Exception {
		String link = assertAcceptedAndGetLink("https://localhost:4200");
		assertTrue(link, link.startsWith("https://localhost:4200/user/DEFAULT/reset/"));
	}

	@Test
	public void hostComparisonIsCaseInsensitive() throws Exception {
		store.setDomainName("Shop.Example.COM");
		String link = assertAcceptedAndGetLink("HTTPS://SHOP.example.com/admin");
		assertTrue(link, link.startsWith("HTTPS://SHOP.example.com/admin/user/DEFAULT/reset/"));
	}

	@Test
	public void storeDomainWithSchemeIsAccepted() throws Exception {
		store.setDomainName("https://shop.example.com:8443/");
		assertAcceptedAndGetLink("https://shop.example.com/admin");
	}

	@Test
	public void storeWithoutDomainAndNoConfiguredHostsRejectsEverything() throws Exception {
		store.setDomainName(null);
		assertRejected("http://localhost:8080/admin");
		verifyNoInteractions(emailService);
	}

	@Test
	public void configuredAllowedHostsAreAccepted() throws Exception {
		ReflectionTestUtils.setField(facade, "passwordResetAllowedHosts",
				" admin.example.com , https://backoffice.example.org:8443/path ,,");

		assertAcceptedAndGetLink("https://admin.example.com/reset");
		ReflectionTestUtils.setField(facade, "emailService", emailService = mock(EmailService.class));
		assertAcceptedAndGetLink("http://BACKOFFICE.example.org");
		ReflectionTestUtils.setField(facade, "emailService", emailService = mock(EmailService.class));
		assertAcceptedAndGetLink("http://localhost:8080");
	}

	@Test
	public void configuredAllowedHostsDoNotAllowSubdomainsOrOtherHosts() throws Exception {
		ReflectionTestUtils.setField(facade, "passwordResetAllowedHosts", "admin.example.com");

		assertRejected("https://evil.admin.example.com");
		assertRejected("https://admin.example.com.evil.example");
		assertRejected("https://evil.example");
		verify(emailService, never()).sendHtmlEmail(any(), any());
	}

	@Test
	public void resetPasswordEncodesPasswordAndClearsToken() throws Exception {
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest("token-1");
		reset.setCredentialsRequestExpiry(new Date(System.currentTimeMillis() + 3_600_000L));
		user.setCredentialsResetRequest(reset);
		when(userService.getByPasswordResetToken(STORE_CODE, "token-1")).thenReturn(user);
		when(passwordEncoder.encode("NewPassword1")).thenReturn("ENCODED");

		facade.resetPassword("NewPassword1", "token-1", STORE_CODE);

		assertEquals("ENCODED", user.getAdminPassword());
		assertNull(user.getCredentialsResetRequest());
		verify(userService).save(user);
	}

	@Test
	public void resetPasswordWithUnknownTokenFailsAndDoesNotSave() throws Exception {
		when(userService.getByPasswordResetToken(anyString(), anyString())).thenReturn(null);
		try {
			facade.resetPassword("NewPassword1", "unknown", STORE_CODE);
			fail("Expected unknown token to be rejected");
		} catch (ServiceRuntimeException expected) {
			// token not found
		}
		verify(userService, never()).save(any(User.class));
		verify(passwordEncoder, never()).encode(any());
	}
}
