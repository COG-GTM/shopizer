package com.salesmanager.test.order;

import com.salesmanager.core.business.repositories.order.OrderRepository;
import com.salesmanager.core.business.services.order.OrderServiceImpl;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderInventorySkuUpgradeTest {
    @Test
    void decrementsSkuProductRatherThanUnrelatedOrderLineId() throws Exception {
        OrderServiceImpl service = spy(new OrderServiceImpl(mock(OrderRepository.class)));
        ProductService products = mock(ProductService.class);
        ReflectionTestUtils.setField(service, "productService", products);
        ReflectionTestUtils.setField(service, "paymentService", mock(PaymentService.class));
        doNothing().when(service).create(any(Order.class));
        MerchantStore store = new MerchantStore();
        Customer customer = new Customer();
        customer.setId(12L);
        Order order = new Order();
        OrderProduct line = new OrderProduct();
        line.setId(999L);
        line.setSku("sku-not-999");
        line.setProductQuantity(3);
        order.getOrderProducts().add(line);
        Product product = new Product();
        product.setId(42L);
        ProductAvailability availability = new ProductAvailability();
        availability.setProductQuantity(10);
        product.getAvailabilities().add(availability);
        when(products.getBySku("sku-not-999", store, store.getDefaultLanguage())).thenReturn(product);
        assertSame(order, service.processOrder(order, customer, java.util.List.of(new ShoppingCartItem()),
                new OrderTotalSummary(), new Payment(), store));
        assertEquals(7, availability.getProductQuantity());
        verify(products).getBySku("sku-not-999", store, store.getDefaultLanguage());
        verify(products).update(product);
        verifyNoMoreInteractions(products);
    }
}
