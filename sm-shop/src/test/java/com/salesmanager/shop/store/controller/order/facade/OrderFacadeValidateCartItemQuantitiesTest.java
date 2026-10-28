package com.salesmanager.shop.store.controller.order.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;

public class OrderFacadeValidateCartItemQuantitiesTest {

	@Test
	public void acceptsEmptyAndPositiveCartItemCollections() throws Exception {
		OrderFacadeImpl.validateCartItemQuantities(Collections.emptyList());
		OrderFacadeImpl.validateCartItemQuantities(new HashSet<>(Arrays.asList(item(1), item(2))));
	}

	@Test
	public void rejectsNullCartItemQuantity() {
		assertThrows(ServiceException.class,
				() -> OrderFacadeImpl.validateCartItemQuantities(Collections.singletonList(item(null))));
	}

	@Test
	public void reportsNegativeCartItemQuantity() {
		ServiceException exception = assertThrows(ServiceException.class,
				() -> OrderFacadeImpl.validateCartItemQuantities(Collections.singletonList(item(-1))));

		assertEquals(ServiceException.EXCEPTION_VALIDATION, exception.getExceptionType());
		assertTrue(exception.getMessage().contains("Invalid quantity -1"));
	}

	private static ShoppingCartItem item(Integer quantity) {
		ShoppingCartItem item = new ShoppingCartItem();
		item.setQuantity(quantity);
		return item;
	}
}
