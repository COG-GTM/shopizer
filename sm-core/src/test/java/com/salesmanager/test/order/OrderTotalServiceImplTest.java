package com.salesmanager.test.order;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.order.ordertotal.OrderTotalServiceImpl;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalVariation;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.modules.order.total.OrderTotalPostProcessorModule;

public class OrderTotalServiceImplTest {
    private OrderTotalServiceImpl service;
    private ProductService products;
    private OrderTotalPostProcessorModule module;
    private final OrderSummary summary = new OrderSummary();
    private final Customer customer = new Customer();
    private final MerchantStore store = new MerchantStore();
    private final Language language = new Language();

    @Before
    public void setUp() {
        service = new OrderTotalServiceImpl();
        products = mock(ProductService.class);
        module = mock(OrderTotalPostProcessorModule.class);
        ReflectionTestUtils.setField(service, "productService", products);
        ReflectionTestUtils.setField(service, "orderTotalPostProcessors", Collections.singletonList(module));
    }

    private Product product(String sku) {
        Product product = new Product();
        product.setSku(sku);
        return product;
    }

    private ShoppingCartItem item(String sku, Product product) {
        ShoppingCartItem item = new ShoppingCartItem();
        item.setProduct(product);
        item.setSku(sku);
        return item;
    }

    private OrderTotal total(String value) {
        OrderTotal total = new OrderTotal();
        total.setText("discount");
        total.setValue(new BigDecimal(value));
        return total;
    }

    private void assertVariation(ShoppingCartItem item, Product expected) throws Exception {
        summary.setProducts(Collections.singletonList(item));
        OrderTotal total = total("-2.50");
        when(module.caculateProductPiceVariation(summary, item, expected, customer, store)).thenReturn(total);
        OrderTotalVariation result = service.findOrderTotalVariation(summary, customer, store, language);
        assertEquals(Collections.singletonList(total), result.getVariations());
        assertSame(total, result.getVariations().get(0));
        assertEquals(new BigDecimal("-2.50"), result.getVariations().get(0).getValue());
        assertEquals("discount", result.getVariations().get(0).getText());
        verify(module).caculateProductPiceVariation(summary, item, expected, customer, store);
    }

    @Test
    public void matchingProductIsReused() throws Exception {
        Product product = product("match");
        assertVariation(item("match", product), product);
        verifyZeroInteractions(products);
    }

    @Test
    public void missingProductIsLoaded() throws Exception {
        Product loaded = product("missing");
        when(products.getBySku("missing", store, language)).thenReturn(loaded);
        assertVariation(item("missing", null), loaded);
        verify(products).getBySku("missing", store, language);
    }

    @Test
    public void differentSkuIsLoaded() throws Exception {
        Product loaded = product("wanted");
        when(products.getBySku("wanted", store, language)).thenReturn(loaded);
        assertVariation(item("wanted", product("other")), loaded);
        verify(products).getBySku("wanted", store, language);
    }

    @Test
    public void nullSkuIsLoaded() throws Exception {
        Product loaded = product("loaded");
        when(products.getBySku(null, store, language)).thenReturn(loaded);
        assertVariation(item(null, product(null)), loaded);
        verify(products).getBySku(null, store, language);
    }

    @Test
    public void multipleModulesAndItemsPreserveVariationOrderAndValues() throws Exception {
        OrderTotalPostProcessorModule second = mock(OrderTotalPostProcessorModule.class);
        ReflectionTestUtils.setField(service, "orderTotalPostProcessors", Arrays.asList(module, second));
        ShoppingCartItem firstItem = item("first", product("first"));
        ShoppingCartItem secondItem = item("second", product("second"));
        summary.setProducts(Arrays.asList(firstItem, secondItem));
        OrderTotal first = total("-1"), next = total("-2"), third = total("-4"), last = total("-3");
        when(module.caculateProductPiceVariation(summary, firstItem, firstItem.getProduct(), customer, store)).thenReturn(first);
        when(module.caculateProductPiceVariation(summary, secondItem, secondItem.getProduct(), customer, store)).thenReturn(next);
        when(second.caculateProductPiceVariation(summary, firstItem, firstItem.getProduct(), customer, store)).thenReturn(third);
        when(second.caculateProductPiceVariation(summary, secondItem, secondItem.getProduct(), customer, store)).thenReturn(last);
        OrderTotalVariation result = service.findOrderTotalVariation(summary, customer, store, language);
        assertEquals(Arrays.asList(first, next, third, last), result.getVariations());
        assertEquals(new BigDecimal("-10"), result.getVariations().stream().map(OrderTotal::getValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        verify(second).caculateProductPiceVariation(summary, firstItem, firstItem.getProduct(), customer, store);
        verify(second).caculateProductPiceVariation(summary, secondItem, secondItem.getProduct(), customer, store);
        verifyZeroInteractions(products);
    }

    @Test
    public void emptyCartHasNoVariations() throws Exception {
        assertNull(service.findOrderTotalVariation(summary, customer, store, language).getVariations());
        verifyZeroInteractions(products, module);
    }

    @Test
    public void lookupErrorIsPropagated() throws Exception {
        summary.setProducts(Collections.singletonList(item("missing", null)));
        RuntimeException failure = new RuntimeException("lookup failed");
        when(products.getBySku("missing", store, language)).thenThrow(failure);
        try {
            service.findOrderTotalVariation(summary, customer, store, language);
            fail("Expected lookup failure");
        } catch (RuntimeException actual) {
            assertSame(failure, actual);
        }
        verifyZeroInteractions(module);
    }
}
