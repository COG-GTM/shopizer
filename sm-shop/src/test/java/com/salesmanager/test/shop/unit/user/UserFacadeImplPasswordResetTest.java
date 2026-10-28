package com.salesmanager.test.shop.unit.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.GenericRuntimeException;
import com.salesmanager.shop.store.api.exception.RestApiException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;
import com.salesmanager.shop.utils.EmailUtils;
import com.salesmanager.shop.utils.FilePathUtils;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LabelUtils;

/**
 * Unit tests (collaborators mocked) for the admin password reset flow of
 * UserFacadeImpl: allow-listed link base, no user enumeration, 60 minute
 * single-use tokens.
 */
public class UserFacadeImplPasswordResetTest {

	private static final String ADMIN = "admin@acme.example";
	private static final String ADMIN_UI = "https://admin.acme.example/console";

	private UserFacadeImpl facade;
	private UserService userService;
	private EmailService emailService;
	private PasswordEncoder passwordEncoder;
	private FilePathUtils filePathUtils;
	private MerchantStore store;
	private Language language;

	@Before
	public void setUp() {
		facade = new UserFacadeImpl();
		userService = mock(UserService.class);
		emailService = mock(EmailService.class);
		passwordEncoder = mock(PasswordEncoder.class);
		EmailUtils emailUtils = mock(EmailUtils.class);
		when(emailUtils.createEmailObjectsMap(any(), any(), any(), any())).thenReturn(new HashMap<>());
		LanguageService languageService = mock(LanguageService.class);
		when(languageService.toLocale(any(), any())).thenReturn(Locale.ENGLISH);

		filePathUtils = new FilePathUtils();
		filePathUtils.properties.setProperty(FilePathUtils.USER_PASSWORD_RESET_ALLOWED_URLS, ADMIN_UI);

		ReflectionTestUtils.setField(facade, "userService", userService);
		ReflectionTestUtils.setField(facade, "emailService", emailService);
		ReflectionTestUtils.setField(facade, "passwordEncoder", passwordEncoder);
		ReflectionTestUtils.setField(facade, "filePathUtils", filePathUtils);
		ReflectionTestUtils.setField(facade, "emailUtils", emailUtils);
		ReflectionTestUtils.setField(facade, "lamguageService", languageService);
		ReflectionTestUtils.setField(facade, "imageUtils", mock(ImageFilePath.class));
		ReflectionTestUtils.setField(facade, "messages", mock(LabelUtils.class));

		store = new MerchantStore();
		store.setCode("ACME");
		store.setStorename("Acme");
		store.setStoreEmailAddress("store@acme.example");
		language = new Language();
		language.setCode("en");
	}

	@Test
	public void disallowedReturnUrlFailsBeforeAnyLookupTokenOrEmail() throws Exception {
		String[] urls = { null, "", "https://mallory.example", "https://admin.acme.example.mallory.example/console",
				"https://admin.acme.example@mallory.example/console" };
		for (String url : urls) {
			try {
				facade.requestPasswordReset(ADMIN, url, store, language);
				fail("expected rejection for " + url);
			} catch (RestApiException expected) {
				assertEquals("400", expected.getErrorCode());
			}
		}
		verifyNoInteractions(userService, emailService);
	}

	@Test
	public void unknownUserReturnsSilentlyWithoutSaveOrEmail() throws Exception {
		when(userService.getByUserName("nobody", "ACME")).thenReturn(null);

		facade.requestPasswordReset("nobody", ADMIN_UI, store, language);

		verify(userService, never()).saveOrUpdate(any(User.class));
		verifyNoInteractions(emailService);
	}

	@Test
	public void knownUserGetsSixtyMinuteTokenAndLinkOnConfiguredBase() throws Exception {
		User user = user();
		when(userService.getByUserName(ADMIN, "ACME")).thenReturn(user);

		long before = System.currentTimeMillis();
		facade.requestPasswordReset(ADMIN, "https://admin.acme.example/console/login?next=//mallory.example", store,
				language);
		long after = System.currentTimeMillis();

		verify(userService).saveOrUpdate(user);
		CredentialsReset reset = user.getCredentialsResetRequest();
		assertNotNull(reset);
		String token = reset.getCredentialsRequest();
		assertNotNull(token);
		long expiry = reset.getCredentialsRequestExpiry().getTime();
		assertTrue(expiry >= before + TimeUnit.MINUTES.toMillis(60));
		assertTrue(expiry <= after + TimeUnit.MINUTES.toMillis(60));

		ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(eq(store), email.capture());
		assertEquals(ADMIN, email.getValue().getTo());
		assertEquals(ADMIN_UI + "/user/ACME/reset/" + token,
				email.getValue().getTemplateTokens().get("RESET_PASSWORD_LINK"));
	}

	@Test
	public void eachRequestIssuesANewToken() throws Exception {
		User user = user();
		when(userService.getByUserName(ADMIN, "ACME")).thenReturn(user);

		facade.requestPasswordReset(ADMIN, ADMIN_UI, store, language);
		String first = user.getCredentialsResetRequest().getCredentialsRequest();
		facade.requestPasswordReset(ADMIN, ADMIN_UI, store, language);
		String second = user.getCredentialsResetRequest().getCredentialsRequest();

		assertTrue(!first.equals(second));
	}

	@Test
	public void emailFailureIsNotPropagatedAndTokenIsKept() throws Exception {
		User user = user();
		when(userService.getByUserName(ADMIN, "ACME")).thenReturn(user);
		doThrow(new ServiceException("smtp down")).when(emailService).sendHtmlEmail(any(), any());

		facade.requestPasswordReset(ADMIN, ADMIN_UI, store, language);

		verify(userService).saveOrUpdate(user);
		assertNotNull(user.getCredentialsResetRequest().getCredentialsRequest());
	}

	@Test
	public void lookupFailureIsWrappedAsServiceRuntimeException() throws Exception {
		ServiceException cause = new ServiceException("db down");
		when(userService.getByUserName(ADMIN, "ACME")).thenThrow(cause);
		try {
			facade.requestPasswordReset(ADMIN, ADMIN_UI, store, language);
			fail("expected ServiceRuntimeException");
		} catch (ServiceRuntimeException expected) {
			assertSame(cause, expected.getCause());
		}
		verifyNoInteractions(emailService);
	}

	@Test
	public void resetPasswordConsumesTokenWithEncodedPassword() throws Exception {
		User user = userWithToken("tok", minutesFromNow(30));
		when(userService.getByPasswordResetToken("ACME", "tok")).thenReturn(user);
		when(passwordEncoder.encode("NewPassword1")).thenReturn("ENC");
		when(userService.resetPasswordWithToken(user.getId(), "tok", "ENC")).thenReturn(true);

		facade.resetPassword("NewPassword1", "tok", "ACME");

		verify(userService).resetPasswordWithToken(user.getId(), "tok", "ENC");
		verify(userService, never()).save(any(User.class));
		verify(userService, never()).saveOrUpdate(any(User.class));
	}

	@Test
	public void resetPasswordFailsWhenTokenWasConsumedConcurrently() throws Exception {
		User user = userWithToken("tok", minutesFromNow(30));
		when(userService.getByPasswordResetToken("ACME", "tok")).thenReturn(user);
		when(passwordEncoder.encode(anyString())).thenReturn("ENC");
		when(userService.resetPasswordWithToken(anyLong(), anyString(), anyString())).thenReturn(false);

		try {
			facade.resetPassword("NewPassword1", "tok", "ACME");
			fail("expected GenericRuntimeException");
		} catch (GenericRuntimeException expected) {
			// expected
		}
	}

	@Test
	public void expiredTokenNeverReachesPasswordUpdate() throws Exception {
		User user = userWithToken("tok", minutesFromNow(-1));
		when(userService.getByPasswordResetToken("ACME", "tok")).thenReturn(user);

		try {
			facade.resetPassword("NewPassword1", "tok", "ACME");
			fail("expected GenericRuntimeException");
		} catch (GenericRuntimeException expected) {
			// expected
		}
		try {
			facade.verifyPasswordRequestToken("tok", "ACME");
			fail("expected GenericRuntimeException");
		} catch (GenericRuntimeException expected) {
			// expected
		}
		verify(userService, never()).resetPasswordWithToken(anyLong(), anyString(), anyString());
	}

	@Test
	public void unknownTokenIsRejected() throws Exception {
		when(userService.getByPasswordResetToken("ACME", "nope")).thenReturn(null);
		try {
			facade.resetPassword("NewPassword1", "nope", "ACME");
			fail("expected ServiceRuntimeException");
		} catch (ServiceRuntimeException expected) {
			// expected
		}
		verify(userService, never()).resetPasswordWithToken(anyLong(), anyString(), anyString());
	}

	private User user() {
		User user = new User();
		user.setId(7L);
		user.setAdminName(ADMIN);
		user.setAdminEmail(ADMIN);
		user.setFirstName("Ada");
		return user;
	}

	private User userWithToken(String token, Date expiry) {
		User user = user();
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(token);
		reset.setCredentialsRequestExpiry(expiry);
		user.setCredentialsResetRequest(reset);
		return user;
	}

	private Date minutesFromNow(long minutes) {
		return new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes));
	}
}
