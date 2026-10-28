package com.salesmanager.test.shop.unit.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.api.v1.payment.PaymentApi;
import com.salesmanager.shop.utils.AuthorizationUtils;
import com.salesmanager.shop.utils.IntegrationKeysMasker;

@ExtendWith(MockitoExtension.class)
public class PaymentApiAuthorizationTest {

	private static final List<String> PAYMENT_GROUPS = Arrays.asList(Constants.GROUP_SUPERADMIN,
			Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL);

	@Mock
	private AuthorizationUtils authorizationUtils;

	@Mock
	private PaymentService paymentService;

	@InjectMocks
	private PaymentApi api;

	private MerchantStore store;

	@BeforeEach
	public void setUp() {
		store = new MerchantStore();
		store.setCode("ACME");
	}

	private void denyUser() {
		when(authorizationUtils.authenticatedUser()).thenReturn("order-clerk");
		doThrow(new UnauthorizedException()).when(authorizationUtils).authorizeUser(eq("order-clerk"), anyList(),
				eq(store));
	}

	private IntegrationModule stripe() {
		IntegrationModule module = new IntegrationModule();
		module.setCode("stripe");
		return module;
	}

	private IntegrationConfiguration storedStripe() {
		IntegrationConfiguration config = new IntegrationConfiguration();
		config.setModuleCode("stripe");
		config.setActive(true);
		Map<String, String> keys = new HashMap<>();
		keys.put("publishableKey", "pk_live_abc");
		keys.put("secretKey", "sk_live_xyz");
		config.setIntegrationKeys(keys);
		return config;
	}

	@Test
	public void listModulesRejectsUnauthorizedGroup() {
		denyUser();
		assertThrows(UnauthorizedException.class, () -> api.paymentModules(store, null));
		verifyNoInteractions(paymentService);
	}

	@Test
	public void moduleDetailRejectsUnauthorizedGroup() {
		denyUser();
		assertThrows(UnauthorizedException.class, () -> api.paymentModule("stripe", store, null));
		verifyNoInteractions(paymentService);
	}

	@Test
	public void configureRejectsUnauthorizedGroup() {
		denyUser();
		IntegrationModuleConfiguration body = new IntegrationModuleConfiguration();
		body.setCode("stripe");
		assertThrows(UnauthorizedException.class, () -> api.configure(body, store));
		verifyNoInteractions(paymentService);
	}

	@Test
	public void moduleDetailMasksCredentialsForAuthorizedUser() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn("store-admin");
		when(paymentService.getPaymentMethodByCode(store, "stripe")).thenReturn(stripe());
		when(paymentService.getPaymentConfiguration("stripe", store)).thenReturn(storedStripe());

		IntegrationModuleConfiguration response = api.paymentModule("stripe", store, null);

		verify(authorizationUtils).authorizeUser("store-admin", PAYMENT_GROUPS, store);
		assertEquals(IntegrationKeysMasker.MASK, response.getIntegrationKeys().get("publishableKey"));
		assertEquals(IntegrationKeysMasker.MASK, response.getIntegrationKeys().get("secretKey"));
	}

	@Test
	public void configureKeepsStoredCredentialWhenMaskIsSentBack() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn("store-admin");
		when(paymentService.getPaymentMethods(store)).thenReturn(Collections.singletonList(stripe()));
		Map<String, IntegrationConfiguration> configured = new HashMap<>();
		configured.put("stripe", storedStripe());
		when(paymentService.getPaymentModulesConfigured(store)).thenReturn(configured);

		IntegrationModuleConfiguration body = new IntegrationModuleConfiguration();
		body.setCode("stripe");
		body.setActive(true);
		Map<String, String> keys = new HashMap<>();
		keys.put("publishableKey", "pk_live_new");
		keys.put("secretKey", IntegrationKeysMasker.MASK);
		body.setIntegrationKeys(keys);

		api.configure(body, store);

		verify(authorizationUtils).authorizeUser("store-admin", PAYMENT_GROUPS, store);
		ArgumentCaptor<IntegrationConfiguration> saved = ArgumentCaptor.forClass(IntegrationConfiguration.class);
		verify(paymentService).savePaymentModuleConfiguration(saved.capture(), any(MerchantStore.class));
		assertEquals("pk_live_new", saved.getValue().getIntegrationKeys().get("publishableKey"));
		assertEquals("sk_live_xyz", saved.getValue().getIntegrationKeys().get("secretKey"));
	}

}
