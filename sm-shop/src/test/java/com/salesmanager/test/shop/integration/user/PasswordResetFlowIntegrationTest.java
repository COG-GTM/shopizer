package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;
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
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.controller.customer.facade.v1.CustomerFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.PasswordRequest;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.shop.utils.FilePathUtils;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PasswordResetFlowIntegrationTest extends ServicesTestSupport {

	private static final String CUSTOMER_PASSWORD = "InitialPass1!";
	private static final String ADMIN_USERNAME = "admin@shopizer.com";
	private static final Pattern RESET_TOKEN = Pattern.compile("/reset/([^/?]+)$");

	private EmailService emailService;

	@Inject
	private CustomerFacade customerFacade;

	@Inject
	private UserFacade userFacade;

	@Inject
	private CustomerService customerService;

	@Inject
	private UserService userService;

	@Inject
	private PasswordEncoder passwordEncoder;

	@Resource(name = "shopizer-properties")
	private Properties properties;

	private Object customerFacadeTarget;
	private Object userFacadeTarget;
	private EmailService originalCustomerEmailService;
	private EmailService originalUserEmailService;

	@Before
	public void mockEmailService() {
		customerFacadeTarget = AopTestUtils.getUltimateTargetObject(customerFacade);
		userFacadeTarget = AopTestUtils.getUltimateTargetObject(userFacade);
		originalCustomerEmailService =
				(EmailService) ReflectionTestUtils.getField(customerFacadeTarget, "emailService");
		originalUserEmailService =
				(EmailService) ReflectionTestUtils.getField(userFacadeTarget, "emailService");
		emailService = Mockito.mock(EmailService.class);
		ReflectionTestUtils.setField(customerFacadeTarget, "emailService", emailService);
		ReflectionTestUtils.setField(userFacadeTarget, "emailService", emailService);
	}

	@After
	public void restoreEmailService() {
		ReflectionTestUtils.setField(customerFacadeTarget, "emailService", originalCustomerEmailService);
		ReflectionTestUtils.setField(userFacadeTarget, "emailService", originalUserEmailService);
	}

	@Test
	public void customerResetRequestUsesStoreDomainForUntrustedMissingAndMalformedReturnUrls() throws Exception {
		for (String returnUrl : new String[] { "https://attacker.example", null, "\"><img src=x>" }) {
			String username = registerCustomer();
			String resetLink = requestResetAndCaptureLink(username, returnUrl);
			assertCustomerLinkMatchesStoredToken(username, resetLink, "https://localhost:8080/customer/DEFAULT/reset/");
		}
	}

	@Test
	public void customerResetRequestUsesConfiguredAllowlistedUrl() throws Exception {
		String previousValue = properties.getProperty(FilePathUtils.PASSWORD_RESET_ALLOWED_URLS);
		String username = registerCustomer();
		try {
			properties.setProperty(FilePathUtils.PASSWORD_RESET_ALLOWED_URLS, "https://shop.example.com");
			String link = requestResetAndCaptureLink(username, "https://shop.example.com/");
			assertTrue(link.startsWith("https://shop.example.com/customer/DEFAULT/reset/"));
		} finally {
			properties.remove(FilePathUtils.PASSWORD_RESET_ALLOWED_URLS);
			if (previousValue != null) {
				properties.setProperty(FilePathUtils.PASSWORD_RESET_ALLOWED_URLS, previousValue);
			}
		}
	}

	@Test
	public void adminCanResetPasswordAndTokenIsSingleUse() throws Exception {
		assertNotNull(userService.getByUserName(ADMIN_USERNAME));
		try {
			assertEquals(HttpStatus.OK, requestAdminReset("https://attacker.example").getStatusCode());
			String link = captureResetLink();
			assertTrue(link.startsWith("https://localhost:8080/user/DEFAULT/reset/"));

			assertEquals(HttpStatus.OK, requestAdminReset(null).getStatusCode());
			String linkWithoutReturnUrl = captureResetLink();
			assertTrue(linkWithoutReturnUrl.startsWith("https://localhost:8080/user/DEFAULT/reset/"));
			String token = tokenFromLink(linkWithoutReturnUrl);

			assertEquals(HttpStatus.OK,
					testRestTemplate.getForEntity("/api/v1/user/DEFAULT/reset/" + token, String.class).getStatusCode());
			ResponseEntity<String> resetResponse = testRestTemplate.postForEntity(
					"/api/v1/user/DEFAULT/password/" + token,
					passwordRequest("NewPass1!"), String.class);
			assertEquals(HttpStatus.OK, resetResponse.getStatusCode());

			assertTrue(adminLogin("NewPass1!").getStatusCode().is2xxSuccessful());
			assertFalse(adminLogin("password").getStatusCode().is2xxSuccessful());

			ResponseEntity<String> reuseResponse = testRestTemplate.postForEntity(
					"/api/v1/user/DEFAULT/password/" + token,
					passwordRequest("AnotherPass1!"), String.class);
			// Reusing a successfully consumed token currently returns 400 BAD_REQUEST.
			assertFalse(reuseResponse.getStatusCode().is2xxSuccessful());
		} finally {
			User restore = userService.getByUserName(ADMIN_USERNAME);
			restore.setAdminPassword(passwordEncoder.encode("password"));
			userService.save(restore);
			assertTrue(adminLogin("password").getStatusCode().is2xxSuccessful());
		}
	}

	@Test
	public void customerPasswordResetAdvancesAuditModifiedDate() throws Exception {
		String username = registerCustomer();
		Customer customer = customerService.getByNick(username, "DEFAULT");
		java.util.Date before = customer.getAuditSection().getDateModified();

		String link = requestResetAndCaptureLink(username, "https://attacker.example");
		String token = tokenFromLink(link);
		Thread.sleep(20L);
		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/customer/DEFAULT/reset/" + token, String.class).getStatusCode());
		assertEquals(HttpStatus.OK, resetCustomer(token, "AuditPass1!").getStatusCode());

		Customer afterReset = customerService.getByNick(username, "DEFAULT");
		java.util.Date after = afterReset.getAuditSection().getDateModified();
		assertNotNull(after);
		assertTrue(before == null || after.after(before));
	}

	@Test
	public void concurrentCustomerResetRequestsAllowExactlyOnePassword() throws Exception {
		String username = registerCustomer();
		String link = requestResetAndCaptureLink(username, null);
		String token = tokenFromLink(link);
		CountDownLatch startGate = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(8);
		List<Future<ResponseEntity<String>>> responses = new ArrayList<>();
		List<String> passwords = new ArrayList<>();
		try {
			for (int i = 0; i < 8; i++) {
				final String candidate = "Race" + i + "!Ab";
				passwords.add(candidate);
				responses.add(executor.submit(() -> {
					startGate.await();
					return resetCustomer(token, candidate);
				}));
			}
			startGate.countDown();

			String winningPassword = null;
			int successfulResets = 0;
			for (int i = 0; i < responses.size(); i++) {
				ResponseEntity<String> response = responses.get(i).get();
				if (response.getStatusCode().is2xxSuccessful()) {
					successfulResets++;
					winningPassword = passwords.get(i);
				}
			}
			assertEquals(1, successfulResets);
			ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
					"/api/v1/customer/login?store=DEFAULT",
					new HttpEntity<>(new AuthenticationRequest(username, winningPassword)), AuthenticationResponse.class);
			assertEquals(HttpStatus.OK, login.getStatusCode());
			assertNotNull(login.getBody().getToken());
		} finally {
			executor.shutdownNow();
		}
	}

	private String registerCustomer() {
		String username = "pr318-" + System.nanoTime() + "@example.test";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(username);
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Password");
		billing.setLastName("Reset");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=DEFAULT", customer, String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return username;
	}

	private String requestResetAndCaptureLink(String username, String returnUrl) throws Exception {
		ResetPasswordRequest request = new ResetPasswordRequest(username);
		request.setReturnUrl(returnUrl);
		clearInvocations(emailService);
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/customer/password/reset/request?store=DEFAULT", request, String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());

		ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(any(MerchantStore.class), emailCaptor.capture());
		return emailCaptor.getValue().getTemplateTokens().get("RESET_PASSWORD_LINK");
	}

	private ResponseEntity<String> requestAdminReset(String returnUrl) {
		ResetPasswordRequest request = new ResetPasswordRequest(ADMIN_USERNAME);
		request.setReturnUrl(returnUrl);
		clearInvocations(emailService);
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/user/password/reset/request?store=DEFAULT", request, String.class);
		return response;
	}

	private String captureResetLink() throws Exception {
		ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(any(MerchantStore.class), emailCaptor.capture());
		return emailCaptor.getValue().getTemplateTokens().get("RESET_PASSWORD_LINK");
	}

	private void assertCustomerLinkMatchesStoredToken(String username, String link, String prefix) {
		assertTrue(link.startsWith(prefix));
		Customer customer = customerService.getByNick(username, "DEFAULT");
		assertNotNull(customer.getCredentialsResetRequest());
		assertEquals(customer.getCredentialsResetRequest().getCredentialsRequest(), tokenFromLink(link));
	}

	private String tokenFromLink(String link) {
		Matcher matcher = RESET_TOKEN.matcher(link);
		assertTrue("Unexpected reset link: " + link, matcher.find());
		return matcher.group(1);
	}

	private ResponseEntity<String> resetCustomer(String token, String password) {
		return testRestTemplate.postForEntity("/api/v1/customer/DEFAULT/password/" + token,
				passwordRequest(password), String.class);
	}

	private PasswordRequest passwordRequest(String password) {
		PasswordRequest request = new PasswordRequest();
		request.setCurrent(password);
		request.setPassword(password);
		request.setRepeatPassword(password);
		return request;
	}

	private ResponseEntity<AuthenticationResponse> adminLogin(String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new AuthenticationRequest(ADMIN_USERNAME, password), AuthenticationResponse.class);
	}
}
