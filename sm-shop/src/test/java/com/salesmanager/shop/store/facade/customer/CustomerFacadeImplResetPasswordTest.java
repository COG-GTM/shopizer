package com.salesmanager.shop.store.facade.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.utils.DateUtil;

@RunWith(MockitoJUnitRunner.class)
public class CustomerFacadeImplResetPasswordTest {

	private static final String STORE = "DEFAULT";
	private static final String TOKEN = "reset-token";

	@Mock
	private CustomerService customerService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private CustomerFacadeImpl customerFacade;

	@Test
	public void resetPasswordInvalidatesToken() throws Exception {
		Customer customer = new Customer();
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(TOKEN);
		reset.setCredentialsRequestExpiry(DateUtil.addDaysToCurrentDate(2));
		customer.setCredentialsResetRequest(reset);

		when(customerService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(customer);
		when(passwordEncoder.encode("newPassword")).thenReturn("encoded");

		customerFacade.resetPassword("newPassword", TOKEN, STORE);

		assertEquals("encoded", customer.getPassword());
		assertNull(customer.getCredentialsResetRequest());
		verify(customerService).save(customer);
	}

	@Test(expected = ResourceNotFoundException.class)
	public void reusedTokenIsNotFound() throws Exception {
		when(customerService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(null);

		try {
			customerFacade.resetPassword("anotherPassword", TOKEN, STORE);
		} finally {
			verify(customerService, never()).save(any(Customer.class));
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void verifyTokenWithoutResetRequestIsNotFound() throws Exception {
		when(customerService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(new Customer());

		customerFacade.verifyPasswordRequestToken(TOKEN, STORE);
	}
}
