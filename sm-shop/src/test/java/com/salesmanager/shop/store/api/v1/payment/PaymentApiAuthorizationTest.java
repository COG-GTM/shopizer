package com.salesmanager.shop.store.api.v1.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.utils.AuthorizationUtils;

@RunWith(MockitoJUnitRunner.class)
public class PaymentApiAuthorizationTest {

	private static final String USER_NAME = "authorized@example.com";
	private static final String MODULE_CODE = "moneyorder";

	@Mock
	private PaymentService paymentService;

	@Mock
	private AuthorizationUtils authorizationUtils;

	@InjectMocks
	private PaymentApi paymentApi;

	private MerchantStore store;

	@Before
	public void setUp() {
		store = new MerchantStore();
	}

	@Test
	public void paymentModulesDoesNotCallServiceWhenAuthorizationFails() {
		assertAuthorizationFailure(() -> paymentApi.paymentModules(store, null));
	}

	@Test
	public void paymentModuleDoesNotCallServiceWhenAuthorizationFails() {
		assertAuthorizationFailure(() -> paymentApi.paymentModule(MODULE_CODE, store, null));
	}

	@Test
	public void configureDoesNotCallServiceWhenAuthorizationFails() {
		assertAuthorizationFailure(() -> paymentApi.configure(configuration(), store));
	}

	@Test
	public void paymentModulesDoesNotCallAuthorizationWhenAuthenticationFails() {
		assertAuthenticationFailure(() -> paymentApi.paymentModules(store, null));
	}

	@Test
	public void paymentModuleDoesNotCallAuthorizationWhenAuthenticationFails() {
		assertAuthenticationFailure(() -> paymentApi.paymentModule(MODULE_CODE, store, null));
	}

	@Test
	public void configureDoesNotCallAuthorizationWhenAuthenticationFails() {
		assertAuthenticationFailure(() -> paymentApi.configure(configuration(), store));
	}

	@Test
	public void paymentModulesAuthorizesBeforeReadingPaymentMethods() throws Exception {
		doReturn(Collections.emptyList()).when(paymentService).getPaymentMethods(store);
		doReturn(Collections.<String, IntegrationConfiguration>emptyMap()).when(paymentService)
				.getPaymentModulesConfigured(store);
		when(authorizationUtils.authenticatedUser()).thenReturn(USER_NAME);

		paymentApi.paymentModules(store, null);

		assertAuthorizedBeforePaymentService("list");
		verify(paymentService).getPaymentMethods(store);
	}

	@Test
	public void paymentModuleAuthorizesBeforeReadingModule() throws Exception {
		IntegrationModule module = new IntegrationModule();
		module.setCode(MODULE_CODE);
		when(paymentService.getPaymentMethodByCode(store, MODULE_CODE)).thenReturn(module);
		when(paymentService.getPaymentConfiguration(MODULE_CODE, store)).thenReturn(null);
		when(authorizationUtils.authenticatedUser()).thenReturn(USER_NAME);

		paymentApi.paymentModule(MODULE_CODE, store, null);

		assertAuthorizedBeforePaymentService("single");
		verify(paymentService).getPaymentMethodByCode(store, MODULE_CODE);
	}

	@Test
	public void configureAuthorizesBeforeReadingPaymentMethods() throws Exception {
		IntegrationModule module = new IntegrationModule();
		module.setCode(MODULE_CODE);
		doReturn(Collections.singletonList(module)).when(paymentService).getPaymentMethods(store);
		doReturn(Collections.<String, IntegrationConfiguration>emptyMap()).when(paymentService)
				.getPaymentModulesConfigured(store);
		when(authorizationUtils.authenticatedUser()).thenReturn(USER_NAME);

		paymentApi.configure(configuration(), store);

		assertAuthorizedBeforePaymentService("configure");
		verify(paymentService).getPaymentMethods(store);
	}

	private void assertAuthorizationFailure(Runnable request) {
		when(authorizationUtils.authenticatedUser()).thenReturn(USER_NAME);
		doThrow(new UnauthorizedException()).when(authorizationUtils)
				.authorizeUser(eq(USER_NAME), anyList(), same(store));

		assertUnauthorized(request);

		verify(authorizationUtils).authorizeUser(eq(USER_NAME), anyList(), same(store));
		verifyNoInteractions(paymentService);
	}

	private void assertAuthenticationFailure(Runnable request) {
		when(authorizationUtils.authenticatedUser()).thenThrow(new UnauthorizedException());

		assertUnauthorized(request);

		verify(authorizationUtils, never()).authorizeUser(anyString(), anyList(), same(store));
		verifyNoInteractions(paymentService);
	}

	private void assertAuthorizedBeforePaymentService(String serviceCall) throws Exception {
		ArgumentCaptor<String> userName = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<List> groups = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor<MerchantStore> authorizedStore = ArgumentCaptor.forClass(MerchantStore.class);
		InOrder order = inOrder(authorizationUtils, paymentService);
		order.verify(authorizationUtils).authenticatedUser();
		order.verify(authorizationUtils).authorizeUser(userName.capture(), groups.capture(),
				authorizedStore.capture());
		if ("single".equals(serviceCall)) {
			order.verify(paymentService).getPaymentMethodByCode(store, MODULE_CODE);
		} else {
			order.verify(paymentService).getPaymentMethods(store);
		}

		assertEquals(USER_NAME, userName.getValue());
		assertSame(store, authorizedStore.getValue());
		Set<String> expectedGroups = new HashSet<>(Arrays.asList(Constants.GROUP_SUPERADMIN,
				Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL));
		assertEquals(expectedGroups, new HashSet<>(groups.getValue()));
		assertFalse(groups.getValue().contains(Constants.GROUP_ADMIN_ORDER));
		assertFalse(groups.getValue().contains(Constants.GROUP_ADMIN_CATALOGUE));
		assertFalse(groups.getValue().contains(Constants.GROUP_SHIPPING));
	}

	private void assertUnauthorized(Runnable request) {
		try {
			request.run();
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			// Expected.
		}
	}

	private IntegrationModuleConfiguration configuration() {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode(MODULE_CODE);
		configuration.setActive(true);
		return configuration;
	}
}
