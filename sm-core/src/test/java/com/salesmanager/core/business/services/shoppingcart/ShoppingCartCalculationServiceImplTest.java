package com.salesmanager.core.business.services.shoppingcart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;

@ExtendWith(MockitoExtension.class)
class ShoppingCartCalculationServiceImplTest {

	@Mock
	private ShoppingCartService shoppingCartService;
	@Mock
	private OrderService orderService;

	private ShoppingCartCalculationServiceImpl service;
	private ShoppingCart cart;
	private MerchantStore store;
	private Customer customer;
	private Language language;

	@BeforeEach
	void setUp() {
		service = new ShoppingCartCalculationServiceImpl();
		ReflectionTestUtils.setField(service, "shoppingCartService", shoppingCartService);
		ReflectionTestUtils.setField(service, "orderService", orderService);

		cart = new ShoppingCart();
		cart.setLineItems(new HashSet<>());
		store = new MerchantStore();
		customer = new Customer();
		language = new Language("en");
	}

	@Test
	void calculateWithCustomerReturnsOrderServiceTotalsThenPersistsCart() throws Exception {
		OrderTotalSummary summary = summary("42.00");
		when(orderService.calculateShoppingCartTotal(cart, customer, store, language)).thenReturn(summary);

		OrderTotalSummary result = service.calculate(cart, customer, store, language);

		assertThat(result).isSameAs(summary);
		InOrder order = inOrder(orderService, shoppingCartService);
		order.verify(orderService).calculateShoppingCartTotal(cart, customer, store, language);
		order.verify(shoppingCartService).saveOrUpdate(cart);
	}

	@Test
	void calculateWithoutCustomerReturnsOrderServiceTotalsThenPersistsCart() throws Exception {
		OrderTotalSummary summary = summary("0.00");
		when(orderService.calculateShoppingCartTotal(cart, store, language)).thenReturn(summary);

		OrderTotalSummary result = service.calculate(cart, store, language);

		assertThat(result).isSameAs(summary);
		InOrder order = inOrder(orderService, shoppingCartService);
		order.verify(orderService).calculateShoppingCartTotal(cart, store, language);
		order.verify(shoppingCartService).saveOrUpdate(cart);
	}

	@Test
	void emptyCartIsStillCalculatedAndSaved() throws Exception {
		when(orderService.calculateShoppingCartTotal(cart, store, language)).thenReturn(summary("0"));

		OrderTotalSummary result = service.calculate(cart, store, language);

		assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
		verify(shoppingCartService).saveOrUpdate(cart);
	}

	@Test
	void cartIsNotSavedWhenTotalCalculationFails() throws Exception {
		ServiceException failure = new ServiceException("boom");
		when(orderService.calculateShoppingCartTotal(cart, customer, store, language)).thenThrow(failure);

		assertThatThrownBy(() -> service.calculate(cart, customer, store, language)).isSameAs(failure);
		verify(shoppingCartService, never()).saveOrUpdate(any(ShoppingCart.class));
	}

	@Test
	void nullCartIsRejected() {
		assertThatThrownBy(() -> service.calculate(null, customer, store, language))
				.isInstanceOf(NullPointerException.class).hasMessageContaining("cart cannot be null");
		assertThatThrownBy(() -> service.calculate(null, store, language))
				.isInstanceOf(NullPointerException.class).hasMessageContaining("cart cannot be null");
		verifyNoInteractions(orderService, shoppingCartService);
	}

	@Test
	void cartWithNullLineItemsIsRejected() {
		cart.setLineItems(null);

		assertThatThrownBy(() -> service.calculate(cart, customer, store, language))
				.isInstanceOf(NullPointerException.class).hasMessageContaining("line items");
		assertThatThrownBy(() -> service.calculate(cart, store, language))
				.isInstanceOf(NullPointerException.class).hasMessageContaining("line items");
		verifyNoInteractions(orderService, shoppingCartService);
	}

	@Test
	void nullStoreIsRejected() {
		assertThatThrownBy(() -> service.calculate(cart, customer, null, language))
				.isInstanceOf(NullPointerException.class).hasMessageContaining("MerchantStore");
		assertThatThrownBy(() -> service.calculate(cart, null, language))
				.isInstanceOf(NullPointerException.class).hasMessageContaining("MerchantStore");
		verifyNoInteractions(orderService, shoppingCartService);
	}

	@Test
	void nullCustomerIsRejectedByCustomerAwareOverload() {
		assertThatThrownBy(() -> service.calculate(cart, (Customer) null, store, language))
				.isInstanceOf(NullPointerException.class).hasMessageContaining("Customer");
		verifyNoInteractions(orderService, shoppingCartService);
	}

	private static OrderTotalSummary summary(String total) {
		OrderTotalSummary summary = new OrderTotalSummary();
		summary.setTotal(new BigDecimal(total));
		return summary;
	}
}
