package com.salesmanager.test.shop.uat;

import com.salesmanager.test.shop.common.UpgradeHttpSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeCustomerJourneyTest extends UpgradeHttpSupport {
    @Test
    void adminAndCustomerAuthenticateWithIsolatedPrivileges() throws Exception {
        String admin = admin();
        assertTrue(request(HttpMethod.GET, "/api/v1/private/customers", null, admin, 200).has("customers"));
        assertEquals(401, exchange(HttpMethod.GET, "/api/v1/private/customers", null, null).getStatusCode().value());
        String email = unique() + "@example.test";
        var registered = request(HttpMethod.POST, "/api/v1/customer/register", customer(email), null, 200);
        String token = login("/api/v1/customer/login", email, PASSWORD);
        var profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, token, 200);
        assertEquals(registered.path("id"), profile.path("id"));
        assertEquals(email, profile.path("emailAddress").asText());
        denied(exchange(HttpMethod.GET, "/api/v1/private/customers", null, token));
    }

    @Test
    void customerBrowsesCreatesAndUpdatesCartThenChecksOutAndReadsOrder() throws Exception {
        String admin = admin();
        String sku = unique();
        long productId = request(HttpMethod.POST, "/api/v1/private/product", product(sku), admin, 201).path("id").asLong();
        assertTrue(request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200).isObject());
        assertTrue(request(HttpMethod.GET, "/api/v1/category", null, null, 200).isObject());
        assertTrue(request(HttpMethod.GET, "/api/v1/products?store=DEFAULT", null, null, 200).path("products").size() > 0);
        assertEquals(productId, request(HttpMethod.GET, "/api/v1/product/" + sku, null, null, 200).path("id").asLong());
        String email = unique() + "@example.test";
        ObjectNode customer = customer(email);
        request(HttpMethod.POST, "/api/v1/customer/register", customer, null, 200);
        String token = login("/api/v1/customer/login", email, PASSWORD);
        var cart = request(HttpMethod.POST, "/api/v1/cart", item(sku, 1), null, 201);
        String path = "/api/v1/cart/" + cart.path("code").asText();
        assertEquals(3, request(HttpMethod.PUT, path, item(sku, 3), null, 201).path("quantity").asInt());
        assertEquals(3, request(HttpMethod.GET, path, null, null, 200).path("quantity").asInt());
        ObjectNode config = json.createObjectNode().put("code", "moneyorder").put("active", true);
        config.putObject("integrationKeys").put("transaction", "AUTHORIZECAPTURE").put("address", "1 Test Street");
        config.putObject("integrationOptions");
        request(HttpMethod.POST, "/api/v1/private/modules/payment", config, admin, 200);
        ObjectNode order = json.createObjectNode().put("currency", "CAD").put("customerAgreement", true);
        order.set("customer", customer);
        order.putObject("payment").put("paymentModule", "moneyorder").put("paymentType", "MONEYORDER")
                .put("transactionType", "AUTHORIZECAPTURE").put("amount", "30.00");
        var confirmation = request(HttpMethod.POST, "/api/v1/auth/cart/" + cart.path("code").asText() + "/checkout",
                order, token, 200);
        long orderId = confirmation.path("id").asLong();
        assertTrue(orderId > 0);
        assertEquals(1, confirmation.path("products").size());
        assertEquals(orderId, request(HttpMethod.GET, "/api/v1/auth/orders/" + orderId, null, token, 200).path("id").asLong());
        assertEquals(orderId, request(HttpMethod.GET, "/api/v1/private/orders/" + orderId, null, admin, 200).path("id").asLong());
    }

    @Test
    void operationalEndpointsAndLegacyBasicAuthRemainAvailable() throws Exception {
        basicAuthentication();
        assertEquals("UP", request(HttpMethod.GET, "/actuator/health", null, null, 200).path("status").asText());
        assertTrue(request(HttpMethod.GET, "/v3/api-docs", null, null, 200).path("openapi").asText().startsWith("3."));
    }
}
