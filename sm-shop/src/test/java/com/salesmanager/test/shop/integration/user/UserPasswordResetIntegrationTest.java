package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.PasswordRequest;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserPasswordResetIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN = "admin@shopizer.com";
	private static final String STORE = "DEFAULT";
	private static final String REQUEST_PATH = "/api/v1/user/password/reset/request?store=DEFAULT";

	@MockBean
	private EmailService emailService;

	@Inject
	private UserService userService;

	@Inject
	private PasswordEncoder passwordEncoder;

	@Before
	public void resetState() throws Exception {
		Mockito.reset(emailService);
		clearToken();
	}

	@After
	public void restoreAdmin() throws Exception {
		User admin = admin();
		admin.setAdminPassword(passwordEncoder.encode("password"));
		admin.setCredentialsResetRequest(null);
		userService.save(admin);
	}

	@Test
	public void externalReturnUrlIsRejectedWithoutTokenOrEmail() throws Exception {
		String[] urls = { "https://mallory.example", "http://localhost:8080@mallory.example",
				"javascript://localhost:8080/x" };
		for (String url : urls) {
			ResponseEntity<String> response = testRestTemplate.postForEntity(REQUEST_PATH, request(ADMIN, url),
					String.class);
			assertEquals(url, HttpStatus.BAD_REQUEST, response.getStatusCode());
		}
		assertNull(admin().getCredentialsResetRequest());
		verify(emailService, never()).sendHtmlEmail(any(), any());
	}

	@Test
	public void unknownUserGetsSameResponseAsKnownUser() throws Exception {
		ResponseEntity<String> unknown = testRestTemplate.postForEntity(REQUEST_PATH,
				request("nobody@shopizer.com", null), String.class);
		assertEquals(HttpStatus.OK, unknown.getStatusCode());
		verify(emailService, never()).sendHtmlEmail(any(), any());

		ResponseEntity<String> known = testRestTemplate.postForEntity(REQUEST_PATH, request(ADMIN, null),
				String.class);
		assertEquals(HttpStatus.OK, known.getStatusCode());
		assertEquals(unknown.getBody(), known.getBody());
	}

	@Test
	public void resetLinkUsesConfiguredBaseUrlAndTokenIsSingleUse() throws Exception {
		ResponseEntity<String> response = testRestTemplate.postForEntity(REQUEST_PATH,
				request(ADMIN, "http://localhost:4200/some/path"), String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());

		User admin = admin();
		assertNotNull(admin.getCredentialsResetRequest());
		String token = admin.getCredentialsResetRequest().getCredentialsRequest();
		Date expiry = admin.getCredentialsResetRequest().getCredentialsRequestExpiry();
		assertTrue(expiry.getTime() <= System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(61));
		assertTrue(expiry.after(new Date()));

		ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(any(), email.capture());
		assertEquals("http://localhost:4200/user/DEFAULT/reset/" + token,
				email.getValue().getTemplateTokens().get("RESET_PASSWORD_LINK"));

		assertEquals(HttpStatus.OK, testRestTemplate
				.getForEntity("/api/v1/user/DEFAULT/reset/" + token, String.class).getStatusCode());

		String changePath = "/api/v1/user/DEFAULT/password/" + token;
		assertEquals(HttpStatus.OK,
				testRestTemplate.postForEntity(changePath, passwordRequest("NewPassword1"), String.class)
						.getStatusCode());
		assertNull(admin().getCredentialsResetRequest());
		assertTrue(passwordEncoder.matches("NewPassword1", admin().getAdminPassword()));

		assertTrue(testRestTemplate.postForEntity(changePath, passwordRequest("Again1"), String.class)
				.getStatusCode().isError());
		assertTrue(testRestTemplate.getForEntity("/api/v1/user/DEFAULT/reset/" + token, String.class)
				.getStatusCode().isError());
		assertTrue(passwordEncoder.matches("NewPassword1", admin().getAdminPassword()));
	}

	@Test
	public void expiredTokenIsRejected() throws Exception {
		testRestTemplate.postForEntity(REQUEST_PATH, request(ADMIN, null), String.class);
		User admin = admin();
		String token = admin.getCredentialsResetRequest().getCredentialsRequest();
		admin.getCredentialsResetRequest()
				.setCredentialsRequestExpiry(new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)));
		userService.save(admin);

		assertTrue(testRestTemplate
				.postForEntity("/api/v1/user/DEFAULT/password/" + token, passwordRequest("NewPassword1"), String.class)
				.getStatusCode().isError());
		assertTrue(passwordEncoder.matches("password", admin().getAdminPassword()));
	}

	private User admin() throws Exception {
		return userService.getByUserName(ADMIN, STORE);
	}

	private void clearToken() throws Exception {
		User admin = admin();
		admin.setCredentialsResetRequest(null);
		userService.save(admin);
	}

	private ResetPasswordRequest request(String username, String returnUrl) {
		ResetPasswordRequest request = new ResetPasswordRequest(username);
		request.setReturnUrl(returnUrl);
		return request;
	}

	private PasswordRequest passwordRequest(String password) {
		PasswordRequest request = new PasswordRequest();
		request.setUsername(ADMIN);
		request.setPassword(password);
		request.setRepeatPassword(password);
		return request;
	}
}
