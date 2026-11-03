package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.controller.customer.facade.v1.CustomerFacade;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.PasswordRequest;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PasswordResetJourneyUatTest extends ServicesTestSupport {

	private static final Pattern RESET_TOKEN = Pattern.compile("/reset/([^/?]+)$");

	@Inject
	private CustomerFacade customerFacade;

	private EmailService emailService;

	private Object customerFacadeTarget;
	private EmailService originalEmailService;

	@Before
	public void mockEmailService() {
		customerFacadeTarget = AopTestUtils.getUltimateTargetObject(customerFacade);
		originalEmailService = (EmailService) ReflectionTestUtils.getField(customerFacadeTarget, "emailService");
		emailService = Mockito.mock(EmailService.class);
		ReflectionTestUtils.setField(customerFacadeTarget, "emailService", emailService);
	}

	@After
	public void restoreEmailService() {
		ReflectionTestUtils.setField(customerFacadeTarget, "emailService", originalEmailService);
	}

	@Test
	public void shopperCanResetPasswordThroughEmailedLink() throws Exception {
		String username = "reset-uat-" + System.nanoTime() + "@example.test";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(username);
		customer.setPassword("OldPass1!");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Reset");
		billing.setLastName("Uat");
		billing.setCountry("BE");
		customer.setBilling(billing);
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=DEFAULT", customer, String.class).getStatusCode());

		assertTrue(customerLogin(username, "OldPass1!").getStatusCode().is2xxSuccessful());

		ResetPasswordRequest request = new ResetPasswordRequest(username);
		request.setReturnUrl("https://attacker.example");
		clearInvocations(emailService);
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity(
				"/api/v1/customer/password/reset/request?store=DEFAULT", request, String.class).getStatusCode());

		ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(any(MerchantStore.class), emailCaptor.capture());
		String link = emailCaptor.getValue().getTemplateTokens().get("RESET_PASSWORD_LINK");
		assertTrue(link.startsWith("https://localhost:8080/customer/DEFAULT/reset/"));
		Matcher matcher = RESET_TOKEN.matcher(link);
		assertTrue(matcher.find());
		String token = matcher.group(1);

		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/customer/DEFAULT/reset/" + token, String.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity(
				"/api/v1/customer/DEFAULT/password/" + token,
				passwordRequest("NewPass1!"), String.class).getStatusCode());
		assertTrue(customerLogin(username, "NewPass1!").getStatusCode().is2xxSuccessful());
		assertTrue(!customerLogin(username, "OldPass1!").getStatusCode().is2xxSuccessful());
		assertTrue(!testRestTemplate.postForEntity("/api/v1/customer/DEFAULT/password/" + token,
				passwordRequest("OtherPass1!"), String.class)
				.getStatusCode().is2xxSuccessful());
		assertTrue(!testRestTemplate.getForEntity("/api/v1/customer/DEFAULT/reset/" + token, String.class)
				.getStatusCode().is2xxSuccessful());
	}

	private ResponseEntity<AuthenticationResponse> customerLogin(String username, String password) {
		return testRestTemplate.postForEntity("/api/v1/customer/login?store=DEFAULT",
				new AuthenticationRequest(username, password), AuthenticationResponse.class);
	}

	private PasswordRequest passwordRequest(String password) {
		PasswordRequest request = new PasswordRequest();
		request.setCurrent(password);
		request.setPassword(password);
		request.setRepeatPassword(password);
		return request;
	}
}
