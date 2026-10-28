package com.salesmanager.shop.store.controller.order.facade;

import static org.junit.Assert.assertThrows;

import java.util.Arrays;

import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;

public class OrderFacadeQuantityTest {

	private static ShoppingCartItem item(Integer quantity) {
		ShoppingCartItem item = new ShoppingCartItem();
		item.setQuantity(quantity);
		return item;
	}

	@Test
	public void acceptsPositiveQuantities() throws Exception {
		OrderFacadeImpl.validateCartItemQuantities(Arrays.asList(item(1), item(5)));
	}

	@Test
	public void rejectsCartWithNegativeOrZeroLine() {
		assertThrows(ServiceException.class,
				() -> OrderFacadeImpl.validateCartItemQuantities(Arrays.asList(item(1), item(-1))));
		assertThrows(ServiceException.class,
				() -> OrderFacadeImpl.validateCartItemQuantities(Arrays.asList(item(0))));
	}
}
