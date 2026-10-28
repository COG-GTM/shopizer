package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.PasswordRequest;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.CapturingEmailSender;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * End-to-end admin password reset through the real Spring context and H2.
 * Uses the shared application context (no @MockBean) and captures emails by
 * swapping the HtmlEmailSender of the real EmailService.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserPasswordResetFlowIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN = "admin@shopizer.com";
	private static final String ADMIN_PASSWORD = "password";
	private static final String ADMIN_UI = "http://localhost:4200";
	private static final String REQUEST_PATH = "/api/v1/user/password/reset/request?store=DEFAULT";
	private static final String VERIFY_PATH = "/api/v1/user/DEFAULT/reset/";
	private static final String CHANGE_PATH = "/api/v1/user/DEFAULT/password/";
	private static final String LOGIN_PATH = "/api/v1/private/login";

	@Inject
	private EmailService emailService;

	@Inject
	private UserService userService;

	@Inject
	private PasswordEncoder passwordEncoder;

	private CapturingEmailSender emails;

	@Before
	public void setUp() throws Exception {
		emails = CapturingEmailSender.install(emailService);
		restoreAdmin();
	}

	@After
	public void tearDown() throws Exception {
		emails.uninstall();
		restoreAdmin();
	}

	@Test
	public void emailLinkUsesConfiguredOriginAndDropsRequestedPathQueryAndFragment() throws Exception {
		ResponseEntity<String> response = requestReset(ADMIN, ADMIN_UI + "/evil/path?next=https://mallory.example#frag");
		assertEquals(HttpStatus.OK, response.getStatusCode());

		String token = admin().getCredentialsResetRequest().getCredentialsRequest();
		assertEquals(1, emails.sent().size());
		String link = resetLink(emails.sent().get(0));
		assertNotNull(link);
		assertTrue(link, link.startsWith(ADMIN_UI));
		assertTrue(link, link.contains(token));
		assertFalse(link, link.contains("evil"));
		assertFalse(link, link.contains("mallory"));
		assertFalse(link, link.contains("frag"));
	}

	@Test
	public void lookalikeHostsAreRejectedWithoutTokenOrEmail() throws Exception {
		String[] urls = { "http://localhost:4200.mallory.example", "http://localhost:4201", "https://localhost:4200",
				"ftp://localhost:4200", "//localhost:4200", "http://LOCALHOST:4200@mallory.example" };
		for (String url : urls) {
			assertEquals(url, HttpStatus.BAD_REQUEST, requestReset(ADMIN, url).getStatusCode());
		}
		assertNull(admin().getCredentialsResetRequest());
		assertTrue(emails.sent().isEmpty());
	}

	@Test
	public void unknownUserResponseIsIdenticalAndSendsNothing() throws Exception {
		ResponseEntity<String> unknown = requestReset("nobody-" + System.nanoTime() + "@example.com", ADMIN_UI);
		assertTrue(emails.sent().isEmpty());
		ResponseEntity<String> known = requestReset(ADMIN, ADMIN_UI);
		assertEquals(known.getStatusCode(), unknown.getStatusCode());
		assertEquals(known.getBody(), unknown.getBody());
		assertEquals(1, emails.sent().size());
	}

	@Test
	public void secondRequestReplacesTokenAndInvalidatesFirst() throws Exception {
		requestReset(ADMIN, ADMIN_UI);
		String first = admin().getCredentialsResetRequest().getCredentialsRequest();
		requestReset(ADMIN, ADMIN_UI);
		String second = admin().getCredentialsResetRequest().getCredentialsRequest();

		assertNotEquals(first, second);
		assertNotEquals(HttpStatus.OK, testRestTemplate.getForEntity(VERIFY_PATH + first, String.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(VERIFY_PATH + second, String.class).getStatusCode());
	}

	@Test
	public void expiryKeepsTimeOfDayAfterDatabaseRoundTrip() throws Exception {
		long before = System.currentTimeMillis();
		requestReset(ADMIN, ADMIN_UI);
		long after = System.currentTimeMillis();

		Date expiry = admin().getCredentialsResetRequest().getCredentialsRequestExpiry();
		long hour = TimeUnit.MINUTES.toMillis(60);
		long slack = TimeUnit.SECONDS.toMillis(5);
		assertTrue("expiry " + expiry, expiry.getTime() >= before + hour - slack);
		assertTrue("expiry " + expiry, expiry.getTime() <= after + hour + slack);
	}

	@Test
	public void fullRestFlowChangesPasswordOnceAndNewPasswordLogsIn() throws Exception {
		requestReset(ADMIN, ADMIN_UI);
		String token = admin().getCredentialsResetRequest().getCredentialsRequest();

		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(VERIFY_PATH + token, String.class).getStatusCode());
		assertEquals(HttpStatus.OK, changePassword(token, "NewPass1").getStatusCode());

		assertEquals(HttpStatus.OK, login(ADMIN, "NewPass1").getStatusCode());
		assertFalse(login(ADMIN, ADMIN_PASSWORD).getStatusCode().is2xxSuccessful());

		assertFalse(changePassword(token, "Again1").getStatusCode().is2xxSuccessful());
		assertFalse(testRestTemplate.getForEntity(VERIFY_PATH + token, String.class).getStatusCode().is2xxSuccessful());
		assertEquals(HttpStatus.OK, login(ADMIN, "NewPass1").getStatusCode());
	}

	@Test
	public void concurrentResetsWithSameTokenSucceedExactlyOnce() throws Exception {
		requestReset(ADMIN, ADMIN_UI);
		User admin = admin();
		Long id = admin.getId();
		String token = admin.getCredentialsResetRequest().getCredentialsRequest();

		int threads = 8;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				String encoded = passwordEncoder.encode("Racer" + i);
				Callable<Boolean> call = () -> {
					start.await();
					return userService.resetPasswordWithToken(id, token, encoded);
				};
				results.add(pool.submit(call));
			}
			start.countDown();
			int successes = 0;
			for (Future<Boolean> result : results) {
				if (result.get(30, TimeUnit.SECONDS)) {
					successes++;
				}
			}
			assertEquals(1, successes);
		} finally {
			pool.shutdownNow();
		}
		User consumed = admin();
		assertTrue(consumed.getCredentialsResetRequest() == null
				|| consumed.getCredentialsResetRequest().getCredentialsRequest() == null);
	}

	private ResponseEntity<String> requestReset(String username, String returnUrl) {
		ResetPasswordRequest request = new ResetPasswordRequest(username);
		request.setReturnUrl(returnUrl);
		return testRestTemplate.postForEntity(REQUEST_PATH, request, String.class);
	}

	private ResponseEntity<String> changePassword(String token, String password) {
		PasswordRequest request = new PasswordRequest();
		request.setUsername(ADMIN);
		request.setPassword(password);
		request.setRepeatPassword(password);
		return testRestTemplate.postForEntity(CHANGE_PATH + token, request, String.class);
	}

	private ResponseEntity<String> login(String user, String password) {
		return testRestTemplate.postForEntity(LOGIN_PATH, new HttpEntity<>(new AuthenticationRequest(user, password)),
				String.class);
	}

	private static String resetLink(Email email) {
		return email.getTemplateTokens().values().stream().filter(v -> v != null && v.startsWith("http"))
				.findFirst().orElse(null);
	}

	private User admin() throws Exception {
		return userService.getByUserName(ADMIN, "DEFAULT");
	}

	private void restoreAdmin() throws Exception {
		User admin = admin();
		admin.setAdminPassword(passwordEncoder.encode(ADMIN_PASSWORD));
		admin.setCredentialsResetRequest(null);
		userService.save(admin);
	}
}
