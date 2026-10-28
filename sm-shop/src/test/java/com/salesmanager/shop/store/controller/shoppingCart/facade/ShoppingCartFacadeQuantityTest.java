package com.salesmanager.shop.store.controller.shoppingCart.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import com.salesmanager.shop.model.shoppingcart.CartModificationException;

public class ShoppingCartFacadeQuantityTest {

	@Test
	public void addRejectsNonPositiveQuantity() throws Exception {
		ShoppingCartFacadeImpl.validateQuantity(1, false);
		assertThrows(CartModificationException.class, () -> ShoppingCartFacadeImpl.validateQuantity(0, false));
		assertThrows(CartModificationException.class, () -> ShoppingCartFacadeImpl.validateQuantity(-1, false));
	}

	@Test
	public void updateAllowsZeroButRejectsNegativeQuantity() throws Exception {
		ShoppingCartFacadeImpl.validateQuantity(0, true);
		ShoppingCartFacadeImpl.validateQuantity(3, true);
		assertThrows(CartModificationException.class, () -> ShoppingCartFacadeImpl.validateQuantity(-1, true));
		assertThrows(CartModificationException.class,
				() -> ShoppingCartFacadeImpl.validateQuantity(Integer.MIN_VALUE, true));
	}

	@Test
	public void summedQuantityMustStayPositiveAndNotOverflow() throws Exception {
		assertEquals(3, ShoppingCartFacadeImpl.addQuantities(1, 2));
		assertEquals(2, ShoppingCartFacadeImpl.addQuantities(null, 2));
		assertThrows(CartModificationException.class, () -> ShoppingCartFacadeImpl.addQuantities(1, -1));
		assertThrows(CartModificationException.class, () -> ShoppingCartFacadeImpl.addQuantities(1, -5));
		assertThrows(CartModificationException.class,
				() -> ShoppingCartFacadeImpl.addQuantities(Integer.MAX_VALUE, 1));
	}
}
