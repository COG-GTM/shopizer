package com.salesmanager.shop.store.facade.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.shop.store.api.exception.GenericRuntimeException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.utils.DateUtil;

@RunWith(MockitoJUnitRunner.class)
public class CustomerFacadeImplResetPasswordEdgeCasesTest {

	private static final String STORE = "DEFAULT";
	private static final String TOKEN = "reset-token";

	@Mock
	private CustomerService customerService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private CustomerFacadeImpl customerFacade;

	@Test
	public void secondResetWithSameTokenIsRejected() throws Exception {
		Customer customer = customerWithToken(DateUtil.addDaysToCurrentDate(2));
		when(customerService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(customer);
		when(passwordEncoder.encode("firstPassword")).thenReturn("first-encoded");

		customerFacade.resetPassword("firstPassword", TOKEN, STORE);

		try {
			customerFacade.resetPassword("secondPassword", TOKEN, STORE);
			fail("Expected a reused token to be rejected");
		} catch (ResourceNotFoundException expected) {
			assertTrue(expected.getErrorMessage().startsWith("Customer not found"));
		}

		assertEquals("first-encoded", customer.getPassword());
		verify(customerService, times(1)).save(customer);
	}

	@Test
	public void verifyAfterResetIsRejected() throws Exception {
		Customer customer = customerWithToken(DateUtil.addDaysToCurrentDate(2));
		when(customerService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(customer);
		when(passwordEncoder.encode("firstPassword")).thenReturn("first-encoded");

		customerFacade.resetPassword("firstPassword", TOKEN, STORE);

		try {
			customerFacade.verifyPasswordRequestToken(TOKEN, STORE);
			fail("Expected a cleared token to be rejected");
		} catch (ResourceNotFoundException expected) {
			assertTrue(expected.getErrorMessage().startsWith("Customer not found"));
		}
	}

	@Test
	public void unknownTokenMessageNamesCustomer() throws Exception {
		when(customerService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(null);

		try {
			customerFacade.verifyPasswordRequestToken(TOKEN, STORE);
			fail("Expected an unknown token to be rejected");
		} catch (ResourceNotFoundException expected) {
			assertTrue(expected.getErrorMessage().startsWith("Customer not found"));
		}
	}

	@Test
	public void expiredTokenIsRejectedWithoutSave() throws Exception {
		Customer customer = customerWithToken(DateUtil.addDaysToCurrentDate(-2));
		CredentialsReset reset = customer.getCredentialsResetRequest();
		when(customerService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(customer);

		try {
			customerFacade.resetPassword("newPassword", TOKEN, STORE);
			fail("Expected an expired token to be rejected");
		} catch (GenericRuntimeException expected) {
			assertSame(reset, customer.getCredentialsResetRequest());
		}

		verify(customerService, never()).save(any(Customer.class));
	}

	@Test
	public void missingExpiryIsRejected() throws Exception {
		Customer customer = customerWithToken(null);
		CredentialsReset reset = customer.getCredentialsResetRequest();
		when(customerService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(customer);

		try {
			customerFacade.verifyPasswordRequestToken(TOKEN, STORE);
			fail("Expected a token without expiry to be rejected");
		} catch (GenericRuntimeException expected) {
			assertSame(reset, customer.getCredentialsResetRequest());
			assertNull(reset.getCredentialsRequestExpiry());
		}

		verify(customerService, never()).save(any(Customer.class));
	}

	@Test(expected = ServiceRuntimeException.class)
	public void lookupFailureIsWrappedAsServiceRuntimeException() throws Exception {
		when(customerService.getByPasswordResetToken(STORE, TOKEN)).thenThrow(new RuntimeException("lookup failed"));

		customerFacade.verifyPasswordRequestToken(TOKEN, STORE);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void saveFailureIsWrapped() throws Exception {
		Customer customer = customerWithToken(DateUtil.addDaysToCurrentDate(2));
		when(customerService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(customer);
		when(passwordEncoder.encode("newPassword")).thenReturn("encoded");
		doThrow(new ServiceException("x")).when(customerService).save(customer);

		customerFacade.resetPassword("newPassword", TOKEN, STORE);
	}

	private Customer customerWithToken(Date expiry) {
		Customer customer = new Customer();
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(TOKEN);
		reset.setCredentialsRequestExpiry(expiry);
		customer.setCredentialsResetRequest(reset);
		return customer;
	}
}
