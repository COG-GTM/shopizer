package com.salesmanager.test.shop.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;
import com.salesmanager.shop.utils.EmailUtils;
import com.salesmanager.shop.utils.FilePathUtils;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LabelUtils;

@RunWith(MockitoJUnitRunner.class)
public class UserFacadeImplPasswordResetTest {

	private static final String RESET_PASSWORD_LINK = "RESET_PASSWORD_LINK";

	@Mock
	private UserService userService;

	@Mock
	private EmailService emailService;

	@Mock
	private EmailUtils emailUtils;

	@Mock
	private ImageFilePath imageUtils;

	@Mock
	private LabelUtils messages;

	@Mock
	private LanguageService lamguageService;

	@InjectMocks
	private UserFacadeImpl facade;

	private final FilePathUtils filePathUtils = new FilePathUtils();
	private final MerchantStore store = new MerchantStore();
	private final Language language = new Language();

	@Before
	public void setUp() throws Exception {
		store.setCode("DEFAULT");
		store.setDomainName("shop.example.com");
		store.setStorename("Shop");
		store.setStoreEmailAddress("shop@example.com");

		ReflectionTestUtils.setField(facade, "filePathUtils", filePathUtils);
		ReflectionTestUtils.setField(facade, "allowedPasswordResetReturnUrls", "http://localhost:4200");

		when(lamguageService.toLocale(language, store)).thenReturn(Locale.ENGLISH);
		when(imageUtils.getContextPath()).thenReturn("");
		when(emailUtils.createEmailObjectsMap(anyString(), eq(store), eq(messages), eq(Locale.ENGLISH)))
				.thenReturn(new HashMap<String, String>());
		when(messages.getMessage(anyString(), eq(Locale.ENGLISH))).thenReturn("message");
		when(messages.getMessage(anyString(), any(String[].class), eq(Locale.ENGLISH))).thenReturn("message");
		doNothing().when(emailService).sendHtmlEmail(eq(store), any(Email.class));
	}

	@Test
	public void rejectsDisallowedReturnUrlBeforeLookingUpUserOrSendingEmail() {
		try {
			facade.requestPasswordReset("admin@shop.example.com", "https://evil.example", store, language);
		} catch (OperationNotAllowedException expected) {
			verifyNoInteractions(userService, emailService);
			return;
		}
		throw new AssertionError("Expected OperationNotAllowedException");
	}

	@Test
	public void rejectsUserInfoReturnUrlBeforeLookingUpUserOrSendingEmail() {
		try {
			facade.requestPasswordReset("admin@shop.example.com", "https://localhost:4200@evil.example", store,
					language);
		} catch (OperationNotAllowedException expected) {
			verifyNoInteractions(userService, emailService);
			return;
		}
		throw new AssertionError("Expected OperationNotAllowedException");
	}

	@Test
	public void persistsTokenAndBuildsSingleSlashResetLinkForConfiguredOrigin() throws Exception {
		User user = user();
		when(userService.getByUserName("admin@shop.example.com", "DEFAULT")).thenReturn(user);

		facade.requestPasswordReset("admin@shop.example.com", "http://localhost:4200/", store, language);

		assertResetLink(user, "http://localhost:4200/user/DEFAULT/reset/");
	}

	@Test
	public void persistsTokenAndBuildsResetLinkForStoreDomain() throws Exception {
		User user = user();
		when(userService.getByUserName("admin@shop.example.com", "DEFAULT")).thenReturn(user);

		facade.requestPasswordReset("admin@shop.example.com", "https://shop.example.com/admin", store, language);

		assertResetLink(user, "https://shop.example.com/admin/user/DEFAULT/reset/");
	}

	@Test
	public void wrapsUnknownUserAndDoesNotSaveToken() throws Exception {
		when(userService.getByUserName("missing@shop.example.com", "DEFAULT")).thenReturn(null);

		try {
			facade.requestPasswordReset("missing@shop.example.com", "http://localhost:4200", store, language);
		} catch (ServiceRuntimeException expected) {
			verify(userService, never()).saveOrUpdate(any(User.class));
			return;
		}
		throw new AssertionError("Expected ServiceRuntimeException");
	}

	@Test(expected = NullPointerException.class)
	public void rejectsNullReturnUrlUsingExistingValidation() {
		facade.requestPasswordReset("admin@shop.example.com", null, store, language);
	}

	private User user() {
		User user = new User();
		user.setAdminName("admin@shop.example.com");
		user.setAdminEmail("admin@shop.example.com");
		user.setFirstName("Admin");
		return user;
	}

	private void assertResetLink(User user, String expectedPrefix) throws Exception {
		CredentialsReset credentialsReset = user.getCredentialsResetRequest();
		assertNotNull(credentialsReset);
		assertNotNull(credentialsReset.getCredentialsRequest());
		assertNotNull(credentialsReset.getCredentialsRequestExpiry());
		verify(userService).saveOrUpdate(user);

		ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(eq(store), emailCaptor.capture());
		Map<String, String> templateTokens = emailCaptor.getValue().getTemplateTokens();
		String resetLink = templateTokens.get(RESET_PASSWORD_LINK);
		assertTrue(resetLink.startsWith(expectedPrefix));
		assertEquals(expectedPrefix + credentialsReset.getCredentialsRequest(), resetLink);
	}
}
