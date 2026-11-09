package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.Pr350IntegrationSupport;

class CheckoutHttpUatTest extends Pr350IntegrationSupport {
    @Test
    void customerCheckoutReportsUnavailableSeedPaymentAndCanListOrders() {
        ReadableProduct product = sampleProduct("uat-checkout");
        String email = "checkout-" + UUID.randomUUID() + "@example.com";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(Map.of("emailAddress", email, "password", "clear123", "gender", "M",
                        "language", "en", "storeCode", "DEFAULT", "billing",
                        Map.of("firstName", "Test", "lastName", "Buyer", "country", "BE",
                                "address", "1 Test Street", "city", "Brussels", "postalCode", "1000")), headers), JsonNode.class);
        assertEquals(200, registered.getStatusCode().value(), String.valueOf(registered.getBody()));
        ResponseEntity<JsonNode> login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new HttpEntity<>(Map.of("username", email, "password", "clear123"), headers), JsonNode.class);
        assertEquals(200, login.getStatusCode().value(), String.valueOf(login.getBody()));
        headers.setBearerAuth(login.getBody().path("token").asText());
        ResponseEntity<JsonNode> cart = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(Map.of("product", product.getSku(), "quantity", 1), headers), JsonNode.class);
        assertEquals(201, cart.getStatusCode().value(), String.valueOf(cart.getBody()));
        String code = cart.getBody().path("code").asText();
        assertFalse(code.isBlank());
        ResponseEntity<JsonNode> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", JsonNode.class);
        String currency = store.getBody().path("currency").asText();
        ResponseEntity<JsonNode> checkout = testRestTemplate.postForEntity("/api/v1/auth/cart/" + code + "/checkout",
                new HttpEntity<>(Map.of("currency", currency, "customerAgreement", true,
                        "payment", Map.of("paymentType", "MONEYORDER", "transactionType", "AUTHORIZECAPTURE",
                                "paymentModule", "moneyorder", "amount", cart.getBody().path("total").asText())), headers), JsonNode.class);
        assertEquals(503, checkout.getStatusCode().value(), String.valueOf(checkout.getBody()));
        assertEquals("Service Unavailable", checkout.getBody().path("error").asText());
        ResponseEntity<JsonNode> order = testRestTemplate.exchange("/api/v1/auth/orders",
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertEquals(200, order.getStatusCode().value(), String.valueOf(order.getBody()));
        assertNotNull(order.getBody());
    }
}
