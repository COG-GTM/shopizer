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

import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.api.v1.shipping.ShippingConfigurationApi;
import com.salesmanager.shop.store.controller.shipping.facade.ShippingFacade;
import com.salesmanager.shop.utils.AuthorizationUtils;
import com.salesmanager.shop.utils.IntegrationKeysMasker;

@ExtendWith(MockitoExtension.class)
public class ShippingConfigurationApiAuthorizationTest {

	private static final List<String> SHIPPING_GROUPS = Arrays.asList(Constants.GROUP_SUPERADMIN,
			Constants.GROUP_ADMIN, Constants.GROUP_SHIPPING, Constants.GROUP_ADMIN_RETAIL);

	@Mock
	private AuthorizationUtils authorizationUtils;

	@Mock
	private ShippingService shippingService;

	@Mock
	private ShippingFacade shippingFacade;

	@InjectMocks
	private ShippingConfigurationApi api;

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

	private IntegrationModule ups() {
		IntegrationModule module = new IntegrationModule();
		module.setCode("ups");
		return module;
	}

	private IntegrationConfiguration storedUps() {
		IntegrationConfiguration config = new IntegrationConfiguration();
		config.setModuleCode("ups");
		config.setActive(true);
		Map<String, String> keys = new HashMap<>();
		keys.put("accessKey", "AK-123");
		keys.put("userId", "acme");
		keys.put("password", "s3cret");
		config.setIntegrationKeys(keys);
		return config;
	}

	@Test
	public void listModulesRejectsUnauthorizedGroup() {
		denyUser();
		assertThrows(UnauthorizedException.class, () -> api.shippingModules(store, null));
		verifyNoInteractions(shippingService);
	}

	@Test
	public void moduleDetailRejectsUnauthorizedGroup() {
		denyUser();
		assertThrows(UnauthorizedException.class, () -> api.shippingModule("ups", store, null));
		verifyNoInteractions(shippingService);
	}

	@Test
	public void configureRejectsUnauthorizedGroup() {
		denyUser();
		IntegrationModuleConfiguration body = new IntegrationModuleConfiguration();
		body.setCode("ups");
		assertThrows(UnauthorizedException.class, () -> api.configure(body, store));
		verifyNoInteractions(shippingService);
	}

	@Test
	public void moduleDetailMasksCredentialsForAuthorizedUser() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn("shipping-admin");
		when(shippingService.getShippingMethods(store)).thenReturn(Collections.singletonList(ups()));
		when(shippingService.getShippingConfiguration("ups", store)).thenReturn(storedUps());

		IntegrationConfiguration response = api.shippingModule("ups", store, null);

		verify(authorizationUtils).authorizeUser("shipping-admin", SHIPPING_GROUPS, store);
		assertEquals("ups", response.getModuleCode());
		assertEquals(true, response.isActive());
		assertEquals(IntegrationKeysMasker.MASK, response.getIntegrationKeys().get("accessKey"));
		assertEquals(IntegrationKeysMasker.MASK, response.getIntegrationKeys().get("userId"));
		assertEquals(IntegrationKeysMasker.MASK, response.getIntegrationKeys().get("password"));
	}

	@Test
	public void configureKeepsStoredCredentialWhenMaskIsSentBack() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn("shipping-admin");
		when(shippingService.getShippingMethods(store)).thenReturn(Collections.singletonList(ups()));
		Map<String, IntegrationConfiguration> configured = new HashMap<>();
		configured.put("ups", storedUps());
		when(shippingService.getShippingModulesConfigured(store)).thenReturn(configured);

		IntegrationModuleConfiguration body = new IntegrationModuleConfiguration();
		body.setCode("ups");
		body.setActive(true);
		Map<String, String> keys = new HashMap<>();
		keys.put("accessKey", IntegrationKeysMasker.MASK);
		keys.put("userId", "acme-new");
		keys.put("password", IntegrationKeysMasker.MASK);
		body.setIntegrationKeys(keys);

		api.configure(body, store);

		verify(authorizationUtils).authorizeUser("shipping-admin", SHIPPING_GROUPS, store);
		ArgumentCaptor<IntegrationConfiguration> saved = ArgumentCaptor.forClass(IntegrationConfiguration.class);
		verify(shippingService).saveShippingQuoteModuleConfiguration(saved.capture(), any(MerchantStore.class));
		assertEquals("AK-123", saved.getValue().getIntegrationKeys().get("accessKey"));
		assertEquals("acme-new", saved.getValue().getIntegrationKeys().get("userId"));
		assertEquals("s3cret", saved.getValue().getIntegrationKeys().get("password"));
	}

}
