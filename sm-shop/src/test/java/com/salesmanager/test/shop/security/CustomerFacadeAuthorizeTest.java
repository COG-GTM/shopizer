package com.salesmanager.test.shop.security;

import static org.junit.Assert.fail;

import java.security.Principal;
import java.util.Collections;

import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.facade.customer.CustomerFacadeImpl;
import com.salesmanager.shop.store.security.user.JWTUser;

public class CustomerFacadeAuthorizeTest {

	private final CustomerFacadeImpl customerFacade = new CustomerFacadeImpl();

	@Test
	public void matchingJwtCustomerIsAuthorized() {
		JWTUser user = user(1L, "alice");

		customerFacade.authorize(customer(1L, "alice"), authentication(user));
	}

	@Test(expected = UnauthorizedException.class)
	public void differentCustomerIdWithSameNickIsRejected() {
		customerFacade.authorize(customer(2L, "alice"), authentication(user(1L, "alice")));
	}

	@Test
	public void nonAuthenticationPrincipalWithMatchingNameIsAuthorized() {
		Principal principal = () -> "alice";

		customerFacade.authorize(customer(2L, "alice"), principal);
	}

	@Test(expected = UnauthorizedException.class)
	public void differentPrincipalNameIsRejected() {
		customerFacade.authorize(customer(1L, "alice"), () -> "bob");
	}

	@Test
	public void jwtPrincipalWithoutIdIsAuthorizedByName() {
		customerFacade.authorize(customer(2L, "alice"), authentication(user(null, "alice")));
	}

	private Customer customer(Long id, String nick) {
		Customer customer = new Customer();
		customer.setId(id);
		customer.setNick(nick);
		return customer;
	}

	private JWTUser user(Long id, String username) {
		return new JWTUser(id, username, "Alice", "Doe", username, "x", Collections.emptyList(), true, null);
	}

	private UsernamePasswordAuthenticationToken authentication(JWTUser user) {
		return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
	}
}
