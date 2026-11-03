package com.salesmanager.test.customer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.repositories.customer.CustomerRepository;
import com.salesmanager.core.business.repositories.user.UserRepository;
import com.salesmanager.core.business.services.customer.CustomerServiceImpl;
import com.salesmanager.core.business.services.user.UserServiceImpl;

@RunWith(MockitoJUnitRunner.class)
public class ResetPasswordWithTokenServiceTest {

	@Mock
	private CustomerRepository customerRepository;

	@Mock
	private UserRepository userRepository;

	@Test
	public void customerResetReturnsTrueAndPassesArgumentsThrough() {
		CustomerServiceImpl service = new CustomerServiceImpl(customerRepository);
		when(customerRepository.resetPasswordWithToken(17L, "customer-token", "encoded-password")).thenReturn(1);

		assertTrue(service.resetPasswordWithToken(17L, "customer-token", "encoded-password"));

		verify(customerRepository).resetPasswordWithToken(17L, "customer-token", "encoded-password");
	}

	@Test
	public void customerResetReturnsFalseWhenNoRowWasUpdated() {
		CustomerServiceImpl service = new CustomerServiceImpl(customerRepository);
		when(customerRepository.resetPasswordWithToken(17L, "customer-token", "encoded-password")).thenReturn(0);

		assertFalse(service.resetPasswordWithToken(17L, "customer-token", "encoded-password"));

		verify(customerRepository).resetPasswordWithToken(17L, "customer-token", "encoded-password");
	}

	@Test
	public void userResetReturnsTrueAndPassesArgumentsThrough() {
		UserServiceImpl service = new UserServiceImpl(userRepository);
		when(userRepository.resetPasswordWithToken(29L, "user-token", "encoded-user-password")).thenReturn(1);

		assertTrue(service.resetPasswordWithToken(29L, "user-token", "encoded-user-password"));

		verify(userRepository).resetPasswordWithToken(29L, "user-token", "encoded-user-password");
	}

	@Test
	public void userResetReturnsFalseWhenNoRowWasUpdated() {
		UserServiceImpl service = new UserServiceImpl(userRepository);
		when(userRepository.resetPasswordWithToken(29L, "user-token", "encoded-user-password")).thenReturn(0);

		assertFalse(service.resetPasswordWithToken(29L, "user-token", "encoded-user-password"));

		verify(userRepository).resetPasswordWithToken(29L, "user-token", "encoded-user-password");
	}
}
