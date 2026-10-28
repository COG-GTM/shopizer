package com.salesmanager.shop.store.facade.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.security.facade.SecurityFacade;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;
import com.salesmanager.shop.utils.EmailUtils;
import com.salesmanager.shop.utils.FilePathUtils;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LabelUtils;
import com.salesmanager.shop.populator.user.PersistableUserPopulator;

@RunWith(MockitoJUnitRunner.class)
public class UserFacadeImplPasswordResetTest {

	@InjectMocks
	private UserFacadeImpl facade;
	@Spy
	private FilePathUtils filePathUtils = new FilePathUtils();
	@Mock
	private MerchantStoreService merchantStoreService;
	@Mock
	private UserService userService;
	@Mock
	private PermissionService permissionService;
	@Mock
	private LanguageService languageService;
	@Mock
	private LanguageService lamguageService;
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

	private MerchantStore store;
	private User user;

	@Before
	public void setUp() {
		store = new MerchantStore();
		store.setCode("DEFAULT");
		store.setDomainName("localhost:8080");
		store.setStorename("Shop");
		store.setStoreEmailAddress("owner@example.com");
		user = new User("admin@example.com", "password", "admin@example.com");
		user.setFirstName("Admin");
		filePathUtils.properties.setProperty(FilePathUtils.PASSWORD_RESET_ALLOWED_ORIGINS,
				"http://localhost:4200");
		when(lamguageService.toLocale(any(Language.class), any(MerchantStore.class))).thenReturn(Locale.ENGLISH);
		when(imageUtils.getContextPath()).thenReturn("");
	}

	@Test
	public void rejectsForeignOriginBeforeTokenOrEmail() {
		try {
			facade.requestPasswordReset("admin@example.com", "https://mallory.example/", store, new Language());
		} catch (ServiceRuntimeException e) {
			assertEquals("400", e.getErrorCode());
			verifyNoInteractions(userService, emailService);
			return;
		}
		throw new AssertionError("expected bad request");
	}

	@Test
	public void allowedHashRouteHasExactlyOneSlashBeforeUserPath() throws Exception {
		Map<String, String> tokens = request("http://localhost:4200/#");
		assertEquals("http://localhost:4200/#/user/DEFAULT/reset/" + user.getCredentialsResetRequest()
				.getCredentialsRequest(), tokens.get("RESET_PASSWORD_LINK"));
	}

	@Test
	public void storeAdminPathIsPreserved() throws Exception {
		Map<String, String> tokens = request("http://localhost:8080/admin");
		assertEquals("http://localhost:8080/admin/user/DEFAULT/reset/"
				+ user.getCredentialsResetRequest().getCredentialsRequest(), tokens.get("RESET_PASSWORD_LINK"));
	}

	@Test
	public void originWithoutTrailingSlashIsJoinedOnce() throws Exception {
		Map<String, String> tokens = request("http://localhost:4200");
		assertEquals("http://localhost:4200/user/DEFAULT/reset/"
				+ user.getCredentialsResetRequest().getCredentialsRequest(), tokens.get("RESET_PASSWORD_LINK"));
	}

	private Map<String, String> request(String returnUrl) throws Exception {
		when(userService.getByUserName("admin@example.com", "DEFAULT")).thenReturn(user);
		Map<String, String> tokens = new HashMap<>();
		when(emailUtils.createEmailObjectsMap(anyString(), any(MerchantStore.class), any(LabelUtils.class),
				any(Locale.class))).thenReturn(tokens);
		facade.requestPasswordReset("admin@example.com", returnUrl, store, new Language());
		verify(userService).saveOrUpdate(user);
		ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(any(MerchantStore.class), email.capture());
		assertEquals("email_template_password_reset_request_user.ftl", email.getValue().getTemplateName());
		assertNotNull(user.getCredentialsResetRequest().getCredentialsRequest());
		return tokens;
	}
}
