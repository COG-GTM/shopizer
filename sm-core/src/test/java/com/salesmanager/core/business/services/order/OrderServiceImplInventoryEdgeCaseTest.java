package com.salesmanager.core.business.services.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.order.OrderRepository;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;

class OrderServiceImplInventoryEdgeCaseTest {

	private OrderRepository orderRepository;
	private ProductService productService;
	private PaymentService paymentService;
	private OrderServiceImpl orderService;
	private MerchantStore store;
	private Customer customer;

	@BeforeEach
	void setUp() {
		orderRepository = mock(OrderRepository.class);
		productService = mock(ProductService.class);
		paymentService = mock(PaymentService.class);
		orderService = new OrderServiceImpl(orderRepository);
		ReflectionTestUtils.setField(orderService, "productService", productService);
		ReflectionTestUtils.setField(orderService, "paymentService", paymentService);
		store = new MerchantStore();
		store.setId(1);
		customer = new Customer();
		customer.setId(5L);
	}

	@Test
	void zeroQuantityFailsBeforePaymentOrSave() throws Exception {
		assertInvalidLine("PARENT", 0);
	}

	@Test
	void negativeQuantityFailsBeforePaymentOrSave() throws Exception {
		assertInvalidLine("PARENT", -1);
	}

	@Test
	void blankSkuFailsBeforePaymentOrSave() throws Exception {
		assertInvalidLine("  ", 1);
	}

	@Test
	void nullSkuFailsBeforePaymentOrSave() throws Exception {
		assertInvalidLine(null, 1);
	}

	@Test
	void parentSkuUsesProductStockInsteadOfUnrelatedVariant() throws Exception {
		Product product = product(10L, "PARENT", 8);
		ProductAvailability parent = product.getAvailabilities().iterator().next();
		ProductAvailability variantStock = availability(101L, product, Constants.ALL_REGIONS, 12);
		ProductVariant variant = new ProductVariant();
		variant.setId(100L);
		variant.setSku("OTHER-VARIANT");
		variantStock.setProductVariant(variant);
		product.getAvailabilities().add(variantStock);

		process(line(1L, "PARENT", 3));

		assertEquals(5, parent.getProductQuantity());
		assertEquals(12, variantStock.getProductQuantity());
		verify(productService).update(product);
	}

	@Test
	void allRegionsStockWinsEvenWhenSpecificRegionIsFirst() throws Exception {
		Product product = product(10L, "PARENT", 8);
		ProductAvailability allRegions = product.getAvailabilities().iterator().next();
		ProductAvailability regional = availability(101L, product, "CA", 12);
		product.setAvailabilities(new LinkedHashSet<>(Arrays.asList(regional, allRegions)));

		process(line(1L, "PARENT", 3));

		assertEquals(5, allRegions.getProductQuantity());
		assertEquals(12, regional.getProductQuantity());
	}

	@Test
	void twoProductsAreEachUpdatedOnce() throws Exception {
		Product first = product(10L, "FIRST", 8);
		Product second = product(20L, "SECOND", 12);

		process(line(1L, "FIRST", 2), line(2L, "SECOND", 3));

		assertEquals(6, first.getAvailabilities().iterator().next().getProductQuantity());
		assertEquals(9, second.getAvailabilities().iterator().next().getProductQuantity());
		verify(productService).update(first);
		verify(productService).update(second);
	}

	@Test
	void repeatedSkuSumsQuantitiesAndUpdatesProductOnce() throws Exception {
		Product product = product(10L, "PARENT", 8);

		process(line(1L, "PARENT", 2), line(2L, "PARENT", 3));

		assertEquals(3, product.getAvailabilities().iterator().next().getProductQuantity());
		verify(productService).update(product);
	}

	@Test
	void exactStockSucceedsAndLeavesZero() throws Exception {
		Product product = product(10L, "PARENT", 8);

		process(line(1L, "PARENT", 8));

		assertEquals(0, product.getAvailabilities().iterator().next().getProductQuantity());
		verify(productService).update(product);
		verify(paymentService).processPayment(any(), any(), any(), any(), any());
		verify(orderRepository).saveAndFlush(any());
	}

	@Test
	void paymentFailureLeavesInventoryUntouched() throws Exception {
		Product product = product(10L, "PARENT", 8);
		ServiceException failure = new ServiceException("Payment declined");
		when(paymentService.processPayment(any(), any(), any(), any(), any())).thenThrow(failure);

		assertSame(failure, assertThrows(ServiceException.class, () -> process(line(1L, "PARENT", 2))));

		assertEquals(8, product.getAvailabilities().iterator().next().getProductQuantity());
		verify(productService, never()).update(any(Product.class));
		verify(orderRepository, never()).saveAndFlush(any());
	}

	private void assertInvalidLine(String sku, int quantity) throws Exception {
		ServiceException failure = assertThrows(ServiceException.class, () -> process(line(1L, sku, quantity)));
		assertEquals(ServiceException.EXCEPTION_INVENTORY_MISMATCH, failure.getExceptionType());
		verify(paymentService, never()).processPayment(any(), any(), any(), any(), any());
		verify(orderRepository, never()).saveAndFlush(any());
		verify(orderRepository, never()).save(any());
	}

	private Product product(Long id, String sku, int quantity) throws ServiceException {
		Product product = new Product();
		product.setId(id);
		product.setSku(sku);
		product.setMerchantStore(store);
		product.setAvailabilities(new LinkedHashSet<>(Collections.singletonList(
				availability(id * 10, product, Constants.ALL_REGIONS, quantity))));
		when(productService.getBySku(sku, store)).thenReturn(product);
		return product;
	}

	private ProductAvailability availability(Long id, Product product, String region, int quantity) {
		ProductAvailability availability = new ProductAvailability();
		availability.setId(id);
		availability.setProduct(product);
		availability.setMerchantStore(store);
		availability.setRegion(region);
		availability.setProductQuantity(quantity);
		return availability;
	}

	private OrderProduct line(Long id, String sku, int quantity) {
		OrderProduct line = new OrderProduct();
		line.setId(id);
		line.setSku(sku);
		line.setProductQuantity(quantity);
		return line;
	}

	private void process(OrderProduct... lines) throws ServiceException {
		Order order = new Order();
		order.setOrderProducts(new LinkedHashSet<>(Arrays.asList(lines)));
		orderService.processOrder(order, customer, Collections.singletonList(new ShoppingCartItem(new Product())),
				new OrderTotalSummary(), mock(Payment.class), store);
	}
}
