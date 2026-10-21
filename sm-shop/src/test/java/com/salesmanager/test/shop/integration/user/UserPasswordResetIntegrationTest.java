package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.PasswordRequest;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserPasswordResetIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN_USERNAME = "admin@shopizer.com";
	private static final String DEFAULT_STORE = "DEFAULT";
	private static final String RESET_REQUEST_PATH = "/api/v1/user/password/reset/request?store=DEFAULT";

	@MockBean
	private EmailService emailService;

	@Inject
	private UserService userService;

	@Before
	public void resetEmailServiceMock() {
		Mockito.reset(emailService);
	}

	@Test
	public void rejectsExternalReturnUrl() throws Exception {
		String[] returnUrls = { "https://evil.example", "http://localhost:8080@evil.example",
				"javascript://localhost:8080/x" };

		for (String returnUrl : returnUrls) {
			ResponseEntity<Void> response = testRestTemplate.postForEntity(RESET_REQUEST_PATH,
					resetPasswordRequest(returnUrl), Void.class);

			assertTrue("Expected a 4xx response for " + returnUrl, response.getStatusCode().is4xxClientError());
			assertNoStoredResetToken();
		}

		verify(emailService, never()).sendHtmlEmail(any(), any());
	}

	@Test
	public void allowedReturnUrlSendsLinkAndTokenIsSingleUse() throws Exception {
		ResponseEntity<Void> resetResponse = testRestTemplate.postForEntity(RESET_REQUEST_PATH,
				resetPasswordRequest("http://localhost:8080/admin"), Void.class);
		assertTrue(resetResponse.getStatusCode().is2xxSuccessful());

		ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(any(), emailCaptor.capture());
		assertTrue(emailCaptor.getValue().getTemplateTokens().get("RESET_PASSWORD_LINK")
				.startsWith("http://localhost:8080/admin/user/DEFAULT/reset/"));

		User admin = userService.getByUserName(ADMIN_USERNAME, DEFAULT_STORE);
		assertNotNull(admin);
		assertNotNull(admin.getCredentialsResetRequest());
		String token = admin.getCredentialsResetRequest().getCredentialsRequest();
		assertNotNull(token);

		PasswordRequest passwordRequest = new PasswordRequest();
		passwordRequest.setUsername(ADMIN_USERNAME);
		passwordRequest.setPassword("password");
		passwordRequest.setRepeatPassword("password");

		String resetPath = "/api/v1/user/DEFAULT/password/" + token;
		ResponseEntity<Void> passwordResponse = testRestTemplate.postForEntity(resetPath, passwordRequest,
				Void.class);
		assertTrue(passwordResponse.getStatusCode().is2xxSuccessful());

		ResponseEntity<Void> repeatedPasswordResponse = testRestTemplate.postForEntity(resetPath, passwordRequest,
				Void.class);
		assertFalse(repeatedPasswordResponse.getStatusCode().is2xxSuccessful());
		assertNoStoredResetToken();
	}

	private ResetPasswordRequest resetPasswordRequest(String returnUrl) {
		ResetPasswordRequest request = new ResetPasswordRequest();
		request.setUsername(ADMIN_USERNAME);
		request.setReturnUrl(returnUrl);
		return request;
	}

	private void assertNoStoredResetToken() throws Exception {
		User admin = userService.getByUserName(ADMIN_USERNAME, DEFAULT_STORE);
		assertNotNull(admin);
		CredentialsReset credentialsReset = admin.getCredentialsResetRequest();
		assertTrue(credentialsReset == null || credentialsReset.getCredentialsRequest() == null);
	}
}
