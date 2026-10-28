package com.salesmanager.shop.store.controller.shoppingCart.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.salesmanager.shop.model.shoppingcart.CartModificationException;

public class ShoppingCartFacadeQuantityBoundaryTest {

	@Test
	public void validatesMinimumIntegerAndUpdateQuantity() throws Exception {
		CartModificationException exception = assertThrows(CartModificationException.class,
				() -> ShoppingCartFacadeImpl.validateQuantity(Integer.MIN_VALUE, false));

		assertTrue(exception.getMessage().contains("Invalid"));
		ShoppingCartFacadeImpl.validateQuantity(1, true);
	}

	@Test
	public void addsQuantitiesWithoutOverflowOrInvalidTotals() throws Exception {
		assertEquals(Integer.MAX_VALUE, ShoppingCartFacadeImpl.addQuantities(Integer.MAX_VALUE - 1, 1));
		assertEquals(1, ShoppingCartFacadeImpl.addQuantities(5, -4));

		assertInvalidAddition(0, 0);
		assertInvalidAddition(null, 0);
		assertInvalidAddition(Integer.MIN_VALUE, Integer.MIN_VALUE);
	}

	private static void assertInvalidAddition(Integer current, int addition) {
		CartModificationException exception = assertThrows(CartModificationException.class,
				() -> ShoppingCartFacadeImpl.addQuantities(current, addition));

		assertTrue(exception.getMessage().contains("Invalid"));
	}
}
