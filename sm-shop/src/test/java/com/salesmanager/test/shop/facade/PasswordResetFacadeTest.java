package com.salesmanager.test.shop.facade;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.GenericRuntimeException;
import com.salesmanager.shop.store.facade.customer.CustomerFacadeImpl;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class PasswordResetFacadeTest {

	private static final String TOKEN = "single-use-token";

	@Mock
	private CustomerService customerService;

	@Mock
	private UserService userService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private CustomerFacadeImpl customerFacade;

	@InjectMocks
	private UserFacadeImpl userFacade;

	private Customer customer;
	private User user;

	@Before
	public void setUp() {
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(TOKEN);
		reset.setCredentialsRequestExpiry(new Date(System.currentTimeMillis() + 60_000));

		customer = new Customer();
		customer.setId(17L);
		customer.setCredentialsResetRequest(reset);
		user = new User();
		user.setId(29L);
		user.setCredentialsResetRequest(reset);
		when(passwordEncoder.encode("newPass")).thenReturn("encoded-new-pass");
	}

	@Test
	public void customerResetFailsWhenConditionalUpdateDidNotMatch() throws Exception {
		when(customerService.getByPasswordResetToken("DEFAULT", TOKEN)).thenReturn(customer);
		when(customerService.resetPasswordWithToken(17L, TOKEN, "encoded-new-pass")).thenReturn(false);

		assertAlreadyUsed(() -> customerFacade.resetPassword("newPass", TOKEN, "DEFAULT"));

		verifyCustomerWritesNotUsed();
	}

	@Test
	public void customerResetUsesEncodedPasswordWithoutSavingEntity() throws Exception {
		when(customerService.getByPasswordResetToken("DEFAULT", TOKEN)).thenReturn(customer);
		when(customerService.resetPasswordWithToken(17L, TOKEN, "encoded-new-pass")).thenReturn(true);

		customerFacade.resetPassword("newPass", TOKEN, "DEFAULT");

		verify(passwordEncoder).encode("newPass");
		verify(customerService).resetPasswordWithToken(17L, TOKEN, "encoded-new-pass");
		verifyCustomerWritesNotUsed();
	}

	@Test
	public void userResetFailsWhenConditionalUpdateDidNotMatch() throws Exception {
		when(userService.getByPasswordResetToken("DEFAULT", TOKEN)).thenReturn(user);
		when(userService.resetPasswordWithToken(29L, TOKEN, "encoded-new-pass")).thenReturn(false);

		assertAlreadyUsed(() -> userFacade.resetPassword("newPass", TOKEN, "DEFAULT"));

		verifyUserWritesNotUsed();
	}

	@Test
	public void userResetUsesEncodedPasswordWithoutSavingEntity() throws Exception {
		when(userService.getByPasswordResetToken("DEFAULT", TOKEN)).thenReturn(user);
		when(userService.resetPasswordWithToken(29L, TOKEN, "encoded-new-pass")).thenReturn(true);

		userFacade.resetPassword("newPass", TOKEN, "DEFAULT");

		verify(passwordEncoder).encode("newPass");
		verify(userService).resetPasswordWithToken(29L, TOKEN, "encoded-new-pass");
		verifyUserWritesNotUsed();
	}

	private void assertAlreadyUsed(Runnable reset) {
		try {
			reset.run();
			fail("Expected a used-token exception");
		} catch (GenericRuntimeException exception) {
			assertTrue(exception.getErrorMessage().contains("already been used"));
		}
	}

	private void verifyCustomerWritesNotUsed() throws Exception {
		verify(customerService, never()).saveOrUpdate(any(Customer.class));
		verify(customerService, never()).save(any(Customer.class));
		verify(customerService, never()).update(any(Customer.class));
	}

	private void verifyUserWritesNotUsed() throws Exception {
		verify(userService, never()).saveOrUpdate(any(User.class));
		verify(userService, never()).save(any(User.class));
		verify(userService, never()).update(any(User.class));
	}
}
