package com.salesmanager.test.shop.integration.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.zone.Zone;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * AB-542: Drools-backed shipping and promo modules work through the real Spring Boot context.
 * Module beans are invoked reflectively because sm-core-modules interfaces are not on sm-shop's
 * compile classpath.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class DroolsRulesContextIntegrationTest extends ServicesTestSupport {

    private static final String IMPL = "com.salesmanager.core.business.modules.";

    @Autowired
    private ApplicationContext context;

    @Test
    void ruleBeansAreWiredWithDroolsBeanFactory() throws Exception {
        assertEquals(IMPL + "integration.shipping.impl.ShippingDecisionPreProcessorImpl",
                context.getBean("shippingMethodDecisionProcess").getClass().getName());
        assertEquals(IMPL + "integration.shipping.impl.CustomShippingQuoteRules",
                context.getBean("customShippingPriceModule").getClass().getName());
        assertEquals(1, context.getBeansOfType(Class.forName(IMPL + "order.total.PromoCodeCalculatorModule")).size());
        assertEquals(1, context.getBeanNamesForType(
                Class.forName("com.salesmanager.core.business.configuration.DroolsBeanFactory")).length);
    }

    @Test
    void shippingDecisionPreProcessorSelectsModuleFromRules() throws Exception {
        Object decision = context.getBean("shippingMethodDecisionProcess");
        IntegrationModule canadaPost = module("canadapost");
        IntegrationModule ups = module("ups");
        IntegrationModule priceByDistance = module("priceByDistance");
        List<IntegrationModule> all = Arrays.asList(canadaPost, ups, priceByDistance);

        assertSame(priceByDistance, decide(decision, 70, 78, "CA", "QC", ups, all));
        assertSame(canadaPost, decide(decision, 10, 20, "CA", "ON", ups, all));
        assertSame(ups, decide(decision, 70, 78, "US", "NY", ups, all));
    }

    @Test
    @SuppressWarnings("unchecked")
    void customQuotesRulesModulePricesByDistance() throws Exception {
        Object custom = context.getBean("customShippingPriceModule");
        ShippingQuote quote = new ShippingQuote();
        quote.getQuoteInformations().put("DISTANCE_KEY", 250.0);
        Delivery delivery = delivery("CA", "QC");
        delivery.setPostalCode("J4B 8J9");

        List<ShippingOption> options = (List<ShippingOption>) method(custom, "getShippingQuotes").invoke(custom, quote,
                packages(70, 78), BigDecimal.TEN, delivery, null, null, null, module("customQuotesRules"), null,
                Locale.CANADA);
        assertEquals(1, options.size());
        assertEquals(new BigDecimal("140"), options.get(0).getOptionPrice());
        assertEquals("customQuotesRules", options.get(0).getShippingModuleCode());
    }

    @Test
    void promoCodeModuleAppliesNoDiscountForExpiredOrUnknownCoupon() throws Exception {
        Object promo = context.getBean(Class.forName(IMPL + "order.total.PromoCodeCalculatorModule"));
        Method calculate = method(promo, "caculateProductPiceVariation");
        ShoppingCartItem item = new ShoppingCartItem(new Product());
        item.setQuantity(1);
        for (String code : Arrays.asList("", "Test1234", "UNKNOWN")) {
            OrderSummary summary = new OrderSummary();
            summary.setPromoCode(code);
            assertNull(calculate.invoke(promo, summary, item, new Product(), null, new MerchantStore()), code);
        }
    }

    @Test
    void promoCodeEndpointAppliesCodeToCartWithoutDiscount() {
        ReadableProduct product = sampleProduct("drools-promo-" + System.nanoTime());
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
                new HttpEntity<>(item), ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, cart.getStatusCode());
        String code = cart.getBody().getCode();
        BigDecimal totalBefore = cart.getBody().getTotal();

        ResponseEntity<ReadableShoppingCart> promo = testRestTemplate.exchange(
                "/api/v1/cart/" + code + "/promo/Test1234?store=DEFAULT", HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()), ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, promo.getStatusCode());
        assertEquals(code, promo.getBody().getCode());
        assertEquals(0, totalBefore.compareTo(promo.getBody().getTotal()), "expired coupon must not discount");

        ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
                "/api/v1/cart/" + code + "?store=DEFAULT", ReadableShoppingCart.class);
        assertEquals(HttpStatus.OK, read.getStatusCode());
        assertEquals(2, read.getBody().getQuantity());
        assertTrue(read.getBody().getTotal().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(0, totalBefore.compareTo(read.getBody().getTotal()));
    }

    private static IntegrationModule decide(Object decision, double weight, double width, String country, String zone,
            IntegrationModule current, List<IntegrationModule> all) throws Exception {
        ShippingQuote quote = new ShippingQuote();
        quote.setCurrentShippingModule(current);
        quote.setShippingModuleCode(current.getCode());
        method(decision, "prePostProcessShippingQuotes").invoke(decision, quote, packages(weight, width), null,
                delivery(country, zone), null, null, null, current, null, all, Locale.CANADA);
        return quote.getCurrentShippingModule();
    }

    private static Method method(Object bean, String name) {
        return Arrays.stream(bean.getClass().getMethods()).filter(m -> m.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError(name + " not found on " + bean.getClass()));
    }

    private static List<PackageDetails> packages(double weight, double width) {
        PackageDetails pack = new PackageDetails();
        pack.setShippingHeight(20);
        pack.setShippingLength(10);
        pack.setShippingWeight(weight);
        pack.setShippingWidth(width);
        return Collections.singletonList(pack);
    }

    private static Delivery delivery(String countryCode, String zoneCode) {
        Country country = new Country();
        country.setIsoCode(countryCode);
        Zone zone = new Zone();
        zone.setCode(zoneCode);
        Delivery delivery = new Delivery();
        delivery.setCountry(country);
        delivery.setZone(zone);
        return delivery;
    }

    private static IntegrationModule module(String code) {
        IntegrationModule module = new IntegrationModule();
        module.setCode(code);
        return module;
    }
}
