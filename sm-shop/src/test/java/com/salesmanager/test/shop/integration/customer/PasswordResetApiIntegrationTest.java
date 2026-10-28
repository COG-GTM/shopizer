package com.salesmanager.test.shop.integration.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.api.exception.ErrorEntity;
import com.salesmanager.shop.store.facade.customer.CustomerFacadeImpl;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PasswordResetApiIntegrationTest extends ServicesTestSupport {

	@Autowired
	private CustomerService customerService;
	@Autowired
	private UserService userService;
	@Autowired
	private MerchantStoreService storeService;
	@Autowired
	private ApplicationContext applicationContext;

	private EmailService emailService;
	private EmailService customerOriginalEmailService;
	private EmailService userOriginalEmailService;

	@Before
	public void mockEmailDelivery() {
		emailService = org.mockito.Mockito.mock(EmailService.class);
		CustomerFacadeImpl customerFacade = AopTestUtils.getUltimateTargetObject(
				applicationContext.getBean("customerFacadev1"));
		UserFacadeImpl userFacade = AopTestUtils.getUltimateTargetObject(
				applicationContext.getBean("userFacade"));
		customerOriginalEmailService = (EmailService) ReflectionTestUtils.getField(customerFacade, "emailService");
		userOriginalEmailService = (EmailService) ReflectionTestUtils.getField(userFacade, "emailService");
		ReflectionTestUtils.setField(customerFacade, "emailService", emailService);
		ReflectionTestUtils.setField(userFacade, "emailService", emailService);
	}

	@After
	public void restoreEmailDelivery() {
		CustomerFacadeImpl customerFacade = AopTestUtils.getUltimateTargetObject(
				applicationContext.getBean("customerFacadev1"));
		UserFacadeImpl userFacade = AopTestUtils.getUltimateTargetObject(
				applicationContext.getBean("userFacade"));
		ReflectionTestUtils.setField(customerFacade, "emailService", customerOriginalEmailService);
		ReflectionTestUtils.setField(userFacade, "emailService", userOriginalEmailService);
	}

	@Test
	public void rejectsCustomerUrlsWithoutChangingTokenOrSendingEmail() {
		String email = registerCustomer();
		clearInvocations(emailService);
		for (String returnUrl : new String[] { "https://mallory.example/", "\"><img src=x>" }) {
			ResponseEntity<ErrorEntity> response = resetCustomer(email, returnUrl);
			assertEquals(400, response.getStatusCodeValue());
			assertEquals("400", response.getBody().getErrorCode());
		}
		Customer customer = customerService.getByNick(email);
		assertTrue(customer.getCredentialsResetRequest() == null
				|| customer.getCredentialsResetRequest().getCredentialsRequest() == null);
		org.mockito.Mockito.verifyNoInteractions(emailService);
	}

	@Test
	public void customerResetPersistsTokenAndEmailsExpectedLink() throws Exception {
		String email = registerCustomer();
		MerchantStore store = storeService.getByCode(Constants.DEFAULT_STORE);
		ResponseEntity<Void> response = resetCustomer(email, "/shop", Void.class);
		assertEquals(OK, response.getStatusCode());

		Customer customer = customerService.getByNick(email);
		String token = customer.getCredentialsResetRequest().getCredentialsRequest();
		assertNotNull(token);
		ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(org.mockito.ArgumentMatchers.any(MerchantStore.class), captor.capture());
		assertEquals("email_template_password_reset_request_customer.ftl", captor.getValue().getTemplateName());
		assertEquals("https://" + store.getDomainName() + "/shop/customer/" + Constants.DEFAULT_STORE + "/reset/"
				+ token, captor.getValue().getTemplateTokens().get("RESET_PASSWORD_LINK"));
		ResponseEntity<String> verifyToken = testRestTemplate.getForEntity(
				"/api/v1/customer/" + Constants.DEFAULT_STORE + "/reset/" + token, String.class);
		assertEquals(OK, verifyToken.getStatusCode());
		assertNotNull(store.getDomainName());
	}

	@Test
	public void rejectedAdminUrlLeavesTokenUnchangedAndAllowedUrlCanBeVerified() throws Exception {
		MerchantStore store = storeService.getByCode(Constants.DEFAULT_STORE);
		User admin = userService.getByUserName("admin@shopizer.com", store.getCode());
		String priorToken = admin.getCredentialsResetRequest() == null ? null
				: admin.getCredentialsResetRequest().getCredentialsRequest();
		clearInvocations(emailService);
		ResetPasswordRequest request = new ResetPasswordRequest("admin@shopizer.com");
		request.setReturnUrl("https://mallory.example/");
		ResponseEntity<ErrorEntity> rejected = testRestTemplate.postForEntity(
				"/api/v1/user/password/reset/request", new HttpEntity<>(request), ErrorEntity.class);
		assertEquals(400, rejected.getStatusCodeValue());
		assertEquals("400", rejected.getBody().getErrorCode());
		assertNotNull(rejected.getBody().getMessage());
		assertEquals(priorToken, userService.getByUserName("admin@shopizer.com", store.getCode())
				.getCredentialsResetRequest() == null ? null
						: userService.getByUserName("admin@shopizer.com", store.getCode())
								.getCredentialsResetRequest().getCredentialsRequest());
		verifyNoInteractions(emailService);

		clearInvocations(emailService);
		request.setReturnUrl("http://" + store.getDomainName() + "/admin");
		assertEquals(OK, testRestTemplate.postForEntity("/api/v1/user/password/reset/request",
				new HttpEntity<>(request), String.class).getStatusCode());
		User refreshed = userService.getByUserName("admin@shopizer.com", store.getCode());
		String token = refreshed.getCredentialsResetRequest().getCredentialsRequest();
		assertNotNull(token);
		ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(org.mockito.ArgumentMatchers.any(MerchantStore.class), email.capture());
		assertEquals("http://" + store.getDomainName() + "/admin/user/" + Constants.DEFAULT_STORE + "/reset/"
				+ token, email.getValue().getTemplateTokens().get("RESET_PASSWORD_LINK"));
		assertEquals(OK, testRestTemplate.getForEntity(
				"/api/v1/user/" + Constants.DEFAULT_STORE + "/reset/" + token, String.class).getStatusCode());
	}

	private String registerCustomer() {
		String email = "reset-" + UUID.randomUUID() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Reset");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer), AuthenticationResponse.class);
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return email;
	}

	private ResponseEntity<ErrorEntity> resetCustomer(String email, String returnUrl) {
		return resetCustomer(email, returnUrl, ErrorEntity.class);
	}

	private <T> ResponseEntity<T> resetCustomer(String email, String returnUrl, Class<T> responseType) {
		ResetPasswordRequest request = new ResetPasswordRequest(email);
		request.setReturnUrl(returnUrl);
		return testRestTemplate.postForEntity("/api/v1/customer/password/reset/request",
				new HttpEntity<>(request), responseType);
	}
}
