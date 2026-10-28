package com.salesmanager.test.shop.unit.order;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
public class OrderApiCheckoutOwnershipTest {

	private static final String CART_CODE = "0123456789abcdef0123456789abcdef";
	private static final Long CALLER_ID = 10L;
	private static final Long OTHER_CUSTOMER_ID = 20L;

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
		caller.setId(CALLER_ID);
		when(customerService.getByNick("mallory@example.com")).thenReturn(caller);
	}

	@Test
	public void checkoutOfAnotherCustomersCartIsRejectedAsNotFound() throws Exception {
		when(shoppingCartService.getByCode(CART_CODE, store)).thenReturn(cart(OTHER_CUSTOMER_ID));

		try {
			orderApi.checkout(CART_CODE, new PersistableOrder(), store, language, request, response, Locale.ENGLISH);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			// 404, indistinguishable from an unknown cart code
		}

		verify(orderFacade, never()).processOrder(any(PersistableOrder.class), any(Customer.class),
				any(MerchantStore.class), any(Language.class), any(Locale.class));
		verify(response, never()).sendError(anyInt(), anyString());
	}

	@Test
	public void checkoutOfUnknownCartIsRejectedAsNotFound() throws Exception {
		when(shoppingCartService.getByCode(CART_CODE, store)).thenReturn(null);

		try {
			orderApi.checkout(CART_CODE, new PersistableOrder(), store, language, request, response, Locale.ENGLISH);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
		}

		verify(orderFacade, never()).processOrder(any(PersistableOrder.class), any(Customer.class),
				any(MerchantStore.class), any(Language.class), any(Locale.class));
	}

	@Test
	public void checkoutOfOwnCartIsProcessed() throws Exception {
		assertCheckoutProcessed(cart(CALLER_ID));
	}

	@Test
	public void checkoutOfAnonymousCartIsProcessedForCaller() throws Exception {
		assertCheckoutProcessed(cart(null));
	}

	private void assertCheckoutProcessed(ShoppingCart cart) throws Exception {
		when(shoppingCartService.getByCode(CART_CODE, store)).thenReturn(cart);
		PersistableOrder order = new PersistableOrder();
		Order modelOrder = new Order();
		modelOrder.setId(99L);
		when(orderFacade.processOrder(order, caller, store, language, Locale.ENGLISH)).thenReturn(modelOrder);
		ReadableOrderConfirmation confirmation = new ReadableOrderConfirmation();
		when(orderFacadeV1.orderConfirmation(modelOrder, caller, store, language)).thenReturn(confirmation);

		ReadableOrderConfirmation result = orderApi.checkout(CART_CODE, order, store, language, request, response,
				Locale.ENGLISH);

		assertSame(confirmation, result);
		verify(orderFacade).processOrder(order, caller, store, language, Locale.ENGLISH);
	}

	private static ShoppingCart cart(Long customerId) {
		ShoppingCart cart = new ShoppingCart();
		cart.setId(1L);
		cart.setShoppingCartCode(CART_CODE);
		cart.setCustomerId(customerId);
		return cart;
	}
}
