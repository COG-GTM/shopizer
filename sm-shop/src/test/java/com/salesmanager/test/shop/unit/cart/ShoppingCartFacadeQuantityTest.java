package com.salesmanager.test.shop.unit.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartCalculationService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.mapper.cart.ReadableShoppingCartMapper;
import com.salesmanager.shop.model.shoppingcart.CartModificationException;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.shoppingCart.facade.ShoppingCartFacadeImpl;
import com.salesmanager.shop.utils.ImageFilePath;

@ExtendWith(MockitoExtension.class)
class ShoppingCartFacadeQuantityTest {

    @Mock
    private ShoppingCartService shoppingCartService;

    @Mock
    private ShoppingCartCalculationService shoppingCartCalculationService;

    @Mock
    private ProductService productService;

    @Mock
    private PricingService pricingService;

    @Mock
    private ProductAttributeService productAttributeService;

    @Mock
    private ImageFilePath imageFilePath;

    @Mock
    private ReadableShoppingCartMapper readableShoppingCartMapper;

    @InjectMocks
    private ShoppingCartFacadeImpl facade;

    @Test
    void anonymousAddRejectsNegativeAndZeroBeforeServiceAccess() {
        MerchantStore store = new MerchantStore();
        Language language = new Language();

        for (int quantity : new int[] { -1, 0 }) {
            PersistableShoppingCartItem item = item(quantity);

            ServiceRuntimeException exception = assertThrows(ServiceRuntimeException.class,
                    () -> facade.addToCart(item, store, language));

            assertTrue(exception.getCause() instanceof CartModificationException);
            verifyNoInteractions(productService, shoppingCartService);
        }
    }

    @Test
    void customerAddRejectsNegativeAndZeroAfterCartLookup() throws Exception {
        Customer customer = new Customer();
        customer.setId(1L);
        MerchantStore store = new MerchantStore();
        Language language = new Language();
        when(shoppingCartService.getShoppingCart(customer, store)).thenReturn(null);

        for (int quantity : new int[] { -1, 0 }) {
            CartModificationException exception = assertThrows(CartModificationException.class,
                    () -> facade.addToCart(customer, item(quantity), store, language));
            assertTrue(exception.getMessage().contains("Quantity"));
            verifyNoInteractions(productService);
        }

        verify(shoppingCartService, org.mockito.Mockito.times(2)).getShoppingCart(customer, store);
    }

    @Test
    void quantityHelpersEnforceBoundsAndAllowValidValues() {
        assertEquals(5, ((Integer) ReflectionTestUtils.invokeMethod(facade, "summedQuantity", 2, 3)).intValue());
        assertEquals(1, ((Integer) ReflectionTestUtils.invokeMethod(facade, "summedQuantity", null, 1)).intValue());

        assertWrappedCartModification(() -> ReflectionTestUtils.invokeMethod(facade, "summedQuantity",
                Integer.MAX_VALUE, 1));
        assertWrappedCartModification(() -> ReflectionTestUtils.invokeMethod(facade, "summedQuantity", 5, -5));
        assertWrappedCartModification(() -> ReflectionTestUtils.invokeMethod(facade, "summedQuantity", 1, -3));

        ReflectionTestUtils.invokeMethod(facade, "validateQuantity", 0);
        assertWrappedCartModification(() -> ReflectionTestUtils.invokeMethod(facade, "validateQuantity", -1));

        ReflectionTestUtils.invokeMethod(facade, "validateAddQuantity", 1);
        assertWrappedCartModification(() -> ReflectionTestUtils.invokeMethod(facade, "validateAddQuantity", 0));
    }

    private static PersistableShoppingCartItem item(int quantity) {
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("quantity-test");
        item.setQuantity(quantity);
        return item;
    }

    private static void assertWrappedCartModification(Runnable invocation) {
        RuntimeException wrapper = assertThrows(RuntimeException.class, invocation::run);
        Throwable cause = wrapper;
        while (cause != null && !(cause instanceof CartModificationException)) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof CartModificationException);
    }
}
