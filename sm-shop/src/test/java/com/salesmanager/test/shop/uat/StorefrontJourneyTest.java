package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.shoppingcart.*;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.test.shop.common.MigrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

class StorefrontJourneyTest extends MigrationTestSupport {
    @Test void adminLoginAndAuthorizationJourney() {
        String path = "/api/v1/private/users";
        ok(path, getHeader());
        assertEquals(401, get(path, new HttpHeaders()).getStatusCode().value());
    }

    @Test void customerRegistrationLoginAndProfileJourney() {
        var profile = ok("/api/v1/auth/customer/profile", registerCustomer());
        assertTrue(profile.path("id").asLong() > 0);
    }

    @Test void browseStoreCategoriesProductsAndDetailJourney() {
        var product = sampleProduct(unique("browse"));
        assertEquals("DEFAULT", ok("/api/v1/store/DEFAULT", new HttpHeaders()).path("code").asText());
        ok("/api/v1/category", new HttpHeaders());
        var products = ok("/api/v1/products", new HttpHeaders());
        assertTrue(products.path("recordsTotal").asLong() > 0);
        assertEquals(product.getId().longValue(), ok("/api/v2/product/" + product.getSku(), new HttpHeaders()).path("id").asLong());
    }

    @Test void createUpdateAndRetrieveCartJourney() {
        var product = sampleProduct(unique("cartuat"));
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        var created = testRestTemplate.postForEntity("/api/v1/cart", item, ReadableShoppingCart.class);
        assertEquals(201, created.getStatusCode().value());
        String path = "/api/v1/cart/" + created.getBody().getCode();
        item.setQuantity(3);
        var updated = testRestTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
        assertTrue(updated.getStatusCode().is2xxSuccessful());
        assertEquals(3, updated.getBody().getQuantity());
        assertEquals(3, ok(path, new HttpHeaders()).path("quantity").asInt());
    }

    @Test void authenticatedCheckoutAndReadOrderJourney() {
        var configured = testRestTemplate.postForEntity("/api/v1/private/modules/payment",
                new HttpEntity<>(java.util.Map.of("code", "moneyorder", "active", true,
                        "integrationKeys", java.util.Map.of("transaction", "AUTHORIZECAPTURE", "address", "123 Test Street")), getHeader()), String.class);
        assertTrue(configured.getStatusCode().is2xxSuccessful(), String.valueOf(configured));
        var customer = registerCustomer();
        var product = sampleProduct(unique("checkout"));
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        var created = testRestTemplate.postForEntity("/api/v1/cart", item, ReadableShoppingCart.class);
        assertEquals(201, created.getStatusCode().value());
        var cart = created.getBody();
        PersistableOrder order = new PersistableOrder();
        order.setCurrency(fetchStore().getCurrency());
        order.setCustomerAgreement(true);
        PersistablePayment payment = new PersistablePayment();
        payment.setPaymentModule("moneyorder");
        payment.setPaymentType("MONEYORDER");
        payment.setTransactionType("AUTHORIZECAPTURE");
        payment.setAmount(cart.getTotal().toPlainString());
        order.setPayment(payment);
        var checkout = testRestTemplate.postForEntity("/api/v1/auth/cart/" + cart.getCode() + "/checkout",
                new HttpEntity<>(order, customer), JsonNode.class);
        assertEquals(200, checkout.getStatusCode().value(), String.valueOf(checkout.getBody()));
        long id = checkout.getBody().path("id").asLong();
        assertTrue(id > 0);
        assertEquals(id, ok("/api/v1/auth/orders/" + id, customer).path("id").asLong());
    }

    @Test void servicesBasicAuthenticationJourney() {
        String path = "/services/private/migration-probe";
        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        int validStatus = get(path, valid).getStatusCode().value();
        assertEquals(404, validStatus);
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "not-password");
        assertEquals(401, get(path, invalid).getStatusCode().value());
    }

    @Test void healthAndApiDocumentationJourney() {
        assertEquals("UP", ok("/actuator/health", new HttpHeaders()).path("status").asText());
        assertTrue(ok("/v3/api-docs", new HttpHeaders()).has("openapi"));
    }
}
