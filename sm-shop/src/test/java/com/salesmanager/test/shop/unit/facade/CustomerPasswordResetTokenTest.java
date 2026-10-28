package com.salesmanager.test.shop.unit.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.shop.store.api.exception.GenericRuntimeException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.facade.customer.CustomerFacadeImpl;
import com.salesmanager.shop.utils.DateUtil;

@RunWith(MockitoJUnitRunner.class)
public class CustomerPasswordResetTokenTest {

	private static final String STORE = "DEFAULT";
	private static final String TOKEN = "3f1c2b7e-1111-2222-3333-444455556666";

	@Mock
	private CustomerService customerService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private CustomerFacadeImpl customerFacade;

	private Customer customer;

	@Before
	public void setUp() throws Exception {
		customer = new Customer();
		customer.setId(1L);
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(TOKEN);
		reset.setCredentialsRequestExpiry(DateUtil.addDaysToCurrentDate(2));
		customer.setCredentialsResetRequest(reset);

		when(passwordEncoder.encode(anyString())).thenAnswer(i -> "encoded:" + i.getArgument(0));
		// mirrors CustomerRepository.findByResetPasswordToken: match on the stored token
		when(customerService.getByPasswordResetToken(anyString(), anyString())).thenAnswer(i -> {
			CredentialsReset stored = customer.getCredentialsResetRequest();
			if (stored != null && i.getArgument(1).equals(stored.getCredentialsRequest())) {
				return customer;
			}
			return null;
		});
	}

	@Test
	public void resetPasswordClearsToken() throws Exception {
		customerFacade.resetPassword("NewPassword1", TOKEN, STORE);

		assertEquals("encoded:NewPassword1", customer.getPassword());
		assertNull(customer.getCredentialsResetRequest());
		verify(customerService).save(customer);
	}

	@Test
	public void resetTokenCannotBeReplayed() throws Exception {
		customerFacade.resetPassword("NewPassword1", TOKEN, STORE);

		try {
			customerFacade.resetPassword("AttackerPassword1", TOKEN, STORE);
			fail("Replayed reset token must be rejected");
		} catch (ServiceRuntimeException expected) {
			// token no longer resolves to a customer
		}

		assertEquals("encoded:NewPassword1", customer.getPassword());
		verify(customerService, times(1)).save(any(Customer.class));
	}

	@Test
	public void usedTokenFailsVerification() throws Exception {
		customerFacade.resetPassword("NewPassword1", TOKEN, STORE);

		try {
			customerFacade.verifyPasswordRequestToken(TOKEN, STORE);
			fail("Used reset token must not verify");
		} catch (ServiceRuntimeException expected) {
		}
	}

	@Test
	public void expiredTokenIsRejected() throws Exception {
		customer.getCredentialsResetRequest().setCredentialsRequestExpiry(new Date(System.currentTimeMillis() - 1000L));

		try {
			customerFacade.resetPassword("NewPassword1", TOKEN, STORE);
			fail("Expired reset token must be rejected");
		} catch (GenericRuntimeException expected) {
		}

		verify(customerService, never()).save(any(Customer.class));
	}
}
