package com.salesmanager.test.shop.integration.cart;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

class DroolsBeansIntegrationTest extends ServicesTestSupport {
    @Autowired private DroolsBeanFactory factory;
    @Autowired private ObjectMapper json;

    @Test
    void springConfiguredFactoryFiresShippingDecision() {
        var session = factory.getKieSession();
        try {
            var input = new ShippingInputParameters();
            input.setWeight(61);
            input.setSize(65);
            input.setCountry("CA");
            input.setProvince("ON");
            var decision = new DecisionResponse();
            session.setGlobal("decision", decision);
            session.insert(input);
            assertTrue(session.fireAllRules() > 0);
            assertEquals("canadapost", decision.getModuleName());
        } finally {
            session.dispose();
        }
    }

    @Test
    void cartPromoInventoryAndStoreUseRealBeans() throws Exception {
        var product = sampleProduct("drools-integration");
        var inventory = testRestTemplate.exchange("/api/v1/private/product/" + product.getSku() + "/inventory",
                HttpMethod.GET, new HttpEntity<>(getHeader()), String.class);
        assertEquals(200, inventory.getStatusCode().value());
        assertTrue(inventory.getBody().contains(product.getSku()));
        assertEquals("DEFAULT", fetchStore().getCode());
        var item = new com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        var cart = testRestTemplate.postForEntity("/api/v1/cart", item, ReadableShoppingCart.class);
        assertEquals(201, cart.getStatusCode().value());
        String code = cart.getBody().getCode();
        var promo = testRestTemplate.postForEntity("/api/v1/cart/" + code + "/promo/Test1234", null, String.class);
        assertEquals(201, promo.getStatusCode().value());
        assertEquals(code, json.readTree(promo.getBody()).path("code").asText());
        assertEquals(1, json.readTree(promo.getBody()).path("quantity").asInt());
    }
}
