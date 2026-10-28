package com.salesmanager.shop.store.api.v1.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.store.api.exception.GenericRuntimeException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;

@RunWith(MockitoJUnitRunner.class)
public class OrderApiAnonymousCheckoutGuardTest {

	@Mock
	private ShoppingCartService shoppingCartService;

	@InjectMocks
	private OrderApi orderApi;

	@Test
	public void positiveCustomerIdIsRejectedBeforeCustomerPopulation() throws Exception {
		MerchantStore store = new MerchantStore();
		when(shoppingCartService.getByCode(eq("cart"), any(MerchantStore.class)))
				.thenReturn(new ShoppingCart());
		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		order.setCustomer(new PersistableCustomer());
		order.setCustomerId(7L);

		ServiceRuntimeException exception = assertThrows(ServiceRuntimeException.class,
				() -> orderApi.checkout("cart", order, store, new Language()));

		assertTrue(exception.getCause() instanceof GenericRuntimeException);
		assertEquals("customerId is not accepted for anonymous checkout",
				((GenericRuntimeException) exception.getCause()).getErrorMessage());
	}
}
