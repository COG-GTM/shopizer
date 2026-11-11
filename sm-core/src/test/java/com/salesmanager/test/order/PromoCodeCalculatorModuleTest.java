package com.salesmanager.test.order;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.order.total.PromoCodeCalculatorModule;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;

/** AB-542: PromoCodeCalculatorModule evaluates PromoCoupon.drl through the real Drools runtime. */
class PromoCodeCalculatorModuleTest {

    private PromoCodeCalculatorModule module;
    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        module = new PromoCodeCalculatorModule();
        pricingService = mock(PricingService.class);
        ReflectionTestUtils.setField(module, "droolsBeanFactory", new DroolsBeanFactory());
        ReflectionTestUtils.setField(module, "pricingService", pricingService);
    }

    @Test
    void blankPromoCodeSkipsRules() throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setPromoCode(" ");
        assertNull(module.caculateProductPiceVariation(summary, item(), new Product(), null, new MerchantStore()));
        verify(pricingService, never()).calculateProductPrice(any(Product.class));
    }

    /** The only coupon rule ("Test1234") expired on 31-Oct-2025, so no discount applies today. */
    @ParameterizedTest
    @ValueSource(strings = { "Test1234", "OTHER", "test1234" })
    void noDiscountWhenRuleDoesNotFire(String promo) throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setPromoCode(promo);
        assertNull(module.caculateProductPiceVariation(summary, item(), new Product(), null, new MerchantStore()));
        verify(pricingService, never()).calculateProductPrice(any(Product.class));
    }

    @Test
    void nullSummaryOrStoreRejected() {
        OrderSummary summary = new OrderSummary();
        summary.setPromoCode("Test1234");
        assertThrows(Exception.class,
                () -> module.caculateProductPiceVariation(null, item(), new Product(), null, new MerchantStore()));
        assertThrows(Exception.class,
                () -> module.caculateProductPiceVariation(summary, item(), new Product(), null, null));
    }

    private static ShoppingCartItem item() {
        ShoppingCartItem item = new ShoppingCartItem(new Product());
        item.setQuantity(1);
        return item;
    }
}
