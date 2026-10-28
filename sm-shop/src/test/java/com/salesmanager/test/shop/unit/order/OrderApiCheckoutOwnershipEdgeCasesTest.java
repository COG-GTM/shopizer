package com.salesmanager.test.shop.unit.order;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.v1.order.OrderApi;
import com.salesmanager.shop.store.controller.order.facade.OrderFacade;

@RunWith(MockitoJUnitRunner.class)
public class OrderApiCheckoutOwnershipEdgeCasesTest {

	private static final String CART_CODE = "0123456789abcdef0123456789abcdef";

	@Mock
	private CustomerService customerService;

	@Mock
	private ShoppingCartService shoppingCartService;

	@Mock
	private OrderFacade orderFacade;

	@Mock
	private com.salesmanager.shop.store.controller.order.facade.v1.OrderFacade orderFacadeV1;

	@InjectMocks
	private OrderApi orderApi;

	private final MerchantStore store = new MerchantStore();
	private final Language language = new Language();
	private final HttpServletRequest request = mock(HttpServletRequest.class);
	private final HttpServletResponse response = mock(HttpServletResponse.class);
	private Customer caller;

	@Before
	public void setUp() throws Exception {
		Principal principal = mock(Principal.class);
		when(principal.getName()).thenReturn("mallory@example.com");
		when(request.getUserPrincipal()).thenReturn(principal);

		caller = new Customer();
		caller.setId(10L);
		when(customerService.getByNick("mallory@example.com")).thenReturn(caller);
	}

	@Test
	public void checkoutWithoutCustomerSendsUnauthorizedAndDoesNotLoadCart() throws Exception {
		when(customerService.getByNick("mallory@example.com")).thenReturn(null);

		assertNull(orderApi.checkout(CART_CODE, new PersistableOrder(), store, language, request, response,
				Locale.ENGLISH));

		verify(response).sendError(eq(401), anyString());
		verify(shoppingCartService, never()).getByCode(anyString(), any(MerchantStore.class));
	}

	@Test
	@SuppressWarnings("removal")
	public void checkoutUsesValueEqualityForCustomerIds() throws Exception {
		caller.setId(Long.valueOf(1000L));
		ShoppingCart cart = cart(new Long(1000L), 1L);
		when(shoppingCartService.getByCode(CART_CODE, store)).thenReturn(cart);

		PersistableOrder order = new PersistableOrder();
		Order modelOrder = new Order();
		modelOrder.setId(99L);
		when(orderFacade.processOrder(order, caller, store, language, Locale.ENGLISH)).thenReturn(modelOrder);
		ReadableOrderConfirmation confirmation = new ReadableOrderConfirmation();
		when(orderFacadeV1.orderConfirmation(modelOrder, caller, store, language)).thenReturn(confirmation);

		assertSame(confirmation, orderApi.checkout(CART_CODE, order, store, language, request, response,
				Locale.ENGLISH));

		verify(orderFacade).processOrder(order, caller, store, language, Locale.ENGLISH);
	}

	@Test
	public void anonymousCartCheckoutSetsCartAndCustomerIdsOnOrder() throws Exception {
		ShoppingCart cart = cart(null, 42L);
		when(shoppingCartService.getByCode(CART_CODE, store)).thenReturn(cart);

		PersistableOrder order = new PersistableOrder();
		Order modelOrder = new Order();
		modelOrder.setId(99L);
		when(orderFacade.processOrder(order, caller, store, language, Locale.ENGLISH)).thenReturn(modelOrder);
		ReadableOrderConfirmation confirmation = new ReadableOrderConfirmation();
		when(orderFacadeV1.orderConfirmation(modelOrder, caller, store, language)).thenReturn(confirmation);

		assertSame(confirmation, orderApi.checkout(CART_CODE, order, store, language, request, response,
				Locale.ENGLISH));

		org.junit.Assert.assertEquals(Long.valueOf(42L), order.getShoppingCartId());
		org.junit.Assert.assertEquals(caller.getId(), order.getCustomerId());
	}

	@Test
	public void rejectedCartDoesNotMutateOrderOrCreateConfirmation() throws Exception {
		when(shoppingCartService.getByCode(CART_CODE, store)).thenReturn(cart(20L, 1L));
		PersistableOrder order = new PersistableOrder();

		try {
			orderApi.checkout(CART_CODE, order, store, language, request, response, Locale.ENGLISH);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			assertNull(order.getShoppingCartId());
			assertNull(order.getCustomerId());
		}

		verify(orderFacadeV1, never()).orderConfirmation(any(Order.class), any(Customer.class),
				any(MerchantStore.class), any(Language.class));
	}

	@Test
	public void processingFailureSendsServiceUnavailableAndReturnsNull() throws Exception {
		when(shoppingCartService.getByCode(CART_CODE, store)).thenReturn(cart(caller.getId(), 1L));
		PersistableOrder order = new PersistableOrder();
		when(orderFacade.processOrder(order, caller, store, language, Locale.ENGLISH))
				.thenThrow(new ServiceException(500, "checkout failed"));

		assertNull(orderApi.checkout(CART_CODE, order, store, language, request, response, Locale.ENGLISH));

		verify(response).sendError(eq(503), anyString());
	}

	private static ShoppingCart cart(Long customerId, Long id) {
		ShoppingCart cart = new ShoppingCart();
		cart.setId(id);
		cart.setShoppingCartCode(CART_CODE);
		cart.setCustomerId(customerId);
		return cart;
	}
}
