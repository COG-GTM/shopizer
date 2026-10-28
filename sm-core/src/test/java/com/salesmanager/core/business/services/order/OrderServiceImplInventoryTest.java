package com.salesmanager.core.business.services.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.order.OrderRepository;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.payments.TransactionService;
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

class OrderServiceImplInventoryTest {

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
		ReflectionTestUtils.setField(orderService, "customerService", mock(CustomerService.class));
		ReflectionTestUtils.setField(orderService, "transactionService", mock(TransactionService.class));

		store = store(1);
		customer = new Customer();
		customer.setId(5L);
	}

	@Test
	void decrementsPurchasedProductNotProductSharingOrderLineId() throws Exception {
		Product purchased = product(10L, "CHEAP", store, 20);
		Product unrelated = product(77L, "UNRELATED", store, 3);
		when(productService.getBySku("CHEAP", store)).thenReturn(purchased);
		when(productService.getById(anyLong())).thenReturn(unrelated);

		OrderProduct line = line("CHEAP", 2);
		line.setId(77L);

		process(order(line));

		assertEquals(18, quantity(purchased));
		assertEquals(3, quantity(unrelated));
		verify(productService).update(purchased);
		verify(productService, never()).update(unrelated);
	}

	@Test
	void unknownSkuFailsBeforePayment() throws Exception {
		when(productService.getBySku("MISSING", store)).thenThrow(new ServiceException("Cannot get product with sku [MISSING]"));

		ServiceException e = assertThrows(ServiceException.class, () -> process(order(line("MISSING", 1))));

		assertEquals(ServiceException.EXCEPTION_INVENTORY_MISMATCH, e.getExceptionType());
		verify(paymentService, never()).processPayment(any(), any(), any(), any(), any());
		verify(orderRepository, never()).saveAndFlush(any());
	}

	@Test
	void productFromAnotherStoreFailsBeforePayment() throws Exception {
		Product foreign = product(10L, "CHEAP", store(2), 20);
		when(productService.getBySku("CHEAP", store)).thenReturn(foreign);

		ServiceException e = assertThrows(ServiceException.class, () -> process(order(line("CHEAP", 1))));

		assertEquals(ServiceException.EXCEPTION_INVENTORY_MISMATCH, e.getExceptionType());
		assertEquals(20, quantity(foreign));
		verify(paymentService, never()).processPayment(any(), any(), any(), any(), any());
	}

	@Test
	void insufficientStockAcrossLinesFailsBeforePayment() throws Exception {
		Product purchased = product(10L, "CHEAP", store, 3);
		when(productService.getBySku("CHEAP", store)).thenReturn(purchased);

		OrderProduct first = line("CHEAP", 2);
		first.setId(1L);
		OrderProduct second = line("CHEAP", 2);
		second.setId(2L);

		ServiceException e = assertThrows(ServiceException.class, () -> process(order(first, second)));

		assertEquals(ServiceException.EXCEPTION_INVENTORY_MISMATCH, e.getExceptionType());
		assertEquals(3, quantity(purchased));
		verify(paymentService, never()).processPayment(any(), any(), any(), any(), any());
		verify(productService, never()).update(any(Product.class));
	}

	@Test
	void decrementsOnlyAllRegionsAvailability() throws Exception {
		Product purchased = product(10L, "CHEAP", store, 20);
		ProductAvailability regional = availability(101L, purchased, "CA", 7);
		purchased.getAvailabilities().add(regional);
		when(productService.getBySku("CHEAP", store)).thenReturn(purchased);

		process(order(line("CHEAP", 4)));

		assertEquals(16, quantity(purchased));
		assertEquals(7, regional.getProductQuantity().intValue());
	}

	@Test
	void variantSkuDecrementsVariantAvailability() throws Exception {
		Product purchased = product(10L, "SHIRT", store, 20);
		ProductVariant red = new ProductVariant();
		red.setId(300L);
		red.setSku("SHIRTRED");
		ProductAvailability redStock = availability(102L, purchased, Constants.ALL_REGIONS, 5);
		redStock.setProductVariant(red);
		purchased.getAvailabilities().add(redStock);
		when(productService.getBySku("SHIRTRED", store)).thenReturn(purchased);

		process(order(line("SHIRTRED", 1)));

		assertEquals(4, redStock.getProductQuantity().intValue());
		assertEquals(20, quantity(purchased));
	}

	@Test
	void parentSkuOfVariantOnlyProductDecrementsItsOwnStock() throws Exception {
		Product purchased = product(10L, "SHIRT", store, 0);
		purchased.getAvailabilities().clear();
		ProductVariant red = new ProductVariant();
		red.setId(300L);
		red.setSku("SHIRTRED");
		ProductAvailability redStock = availability(102L, purchased, Constants.ALL_REGIONS, 5);
		redStock.setProductVariant(red);
		purchased.getAvailabilities().add(redStock);
		Product unrelated = product(77L, "UNRELATED", store, 3);
		when(productService.getBySku("SHIRT", store)).thenReturn(purchased);
		when(productService.getById(anyLong())).thenReturn(unrelated);

		process(order(line("SHIRT", 2)));

		assertEquals(3, redStock.getProductQuantity().intValue());
		assertEquals(3, quantity(unrelated));
		verify(productService, never()).update(unrelated);
	}

	private void process(Order order) throws ServiceException {
		orderService.processOrder(order, customer, Collections.singletonList(new ShoppingCartItem(new Product())),
				new OrderTotalSummary(), mock(Payment.class), store);
	}

	private static MerchantStore store(int id) {
		MerchantStore s = new MerchantStore();
		s.setId(id);
		s.setCode("store" + id);
		return s;
	}

	private static Product product(Long id, String sku, MerchantStore store, int qty) {
		Product p = new Product();
		p.setId(id);
		p.setSku(sku);
		p.setMerchantStore(store);
		Set<ProductAvailability> availabilities = new HashSet<ProductAvailability>();
		availabilities.add(availability(id * 10, p, Constants.ALL_REGIONS, qty));
		p.setAvailabilities(availabilities);
		return p;
	}

	private static ProductAvailability availability(Long id, Product product, String region, int qty) {
		ProductAvailability a = new ProductAvailability();
		a.setId(id);
		a.setProduct(product);
		a.setRegion(region);
		a.setProductQuantity(qty);
		return a;
	}

	private static int quantity(Product p) {
		return p.getAvailabilities().stream()
				.filter(a -> a.getProductVariant() == null && Constants.ALL_REGIONS.equals(a.getRegion()))
				.findFirst().get().getProductQuantity();
	}

	private static OrderProduct line(String sku, int qty) {
		OrderProduct line = new OrderProduct();
		line.setSku(sku);
		line.setProductName(sku);
		line.setProductQuantity(qty);
		line.setOneTimeCharge(BigDecimal.ONE);
		return line;
	}

	private static Order order(OrderProduct... lines) {
		Order order = new Order();
		order.setOrderProducts(new LinkedHashSet<OrderProduct>(java.util.Arrays.asList(lines)));
		return order;
	}
}
