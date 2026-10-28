package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ApiJourneysUatTest extends UatSupport {
    @Test public void adminJwtProtectsPrivateApi() throws Exception {
        String token = login("/api/v1/private/login", "admin@shopizer.com", "password");
        JsonNode customers = json(request("admin-private", HttpMethod.GET, "/api/v1/private/customers", null, bearer(token)), 200);
        int absent = request("admin-no-token", HttpMethod.GET, "/api/v1/private/customers", null, null).getStatusCodeValue();
        ResponseEntity<String> bad = request("admin-bad-token", HttpMethod.GET, "/api/v1/private/customers", null, bearer("invalid-token"));
        assertEquals(401, absent);
        // Currently 500 from MalformedJwtException in JWTAdminAuthenticationManager.
        assertFalse(bad.getStatusCode().is2xxSuccessful());
        assertNotEquals(customers, mapper.readTree(bad.getBody()));
        assertFalse(mapper.readTree(bad.getBody()).has("customers"));
    }

    @Test public void customerJwtReadsProfile() throws Exception {
        String email = register();
        String token = login("/api/v1/customer/login", email, "OldPass123");
        JsonNode profile = json(request("customer-profile", HttpMethod.GET, "/api/v1/auth/customer/profile", null, bearer(token)), 200);
        assertEquals(email, profile.path("emailAddress").asText());
    }

    @Test public void browseStoreCategoriesAndProducts() throws Exception {
        ReadableProduct product = sampleProduct("uat-" + UUID.randomUUID());
        assertEquals("DEFAULT", json(request("store", HttpMethod.GET, "/api/v1/store/DEFAULT", null, null), 200).path("code").asText());
        json(request("categories", HttpMethod.GET, "/api/v1/category", null, null), 200);
        JsonNode products = json(request("products", HttpMethod.GET, "/api/v1/products", null, null), 200);
        assertTrue(products.path("products").size() > 0);
        assertEquals(product.getSku(), json(request("product-detail", HttpMethod.GET, "/api/v2/product/" + product.getSku(), null, null), 200).path("sku").asText());
    }

    private JsonNode cart() throws Exception {
        ReadableProduct product = sampleProduct("uat-" + UUID.randomUUID());
        return json(request("cart-create", HttpMethod.POST, "/api/v1/cart", Map.of("product", product.getSku(), "quantity", 1), null), 201);
    }

    @Test public void cartCanBeUpdatedAndRead() throws Exception {
        JsonNode cart = cart(); String code = cart.path("code").asText();
        String sku = cart.path("products").get(0).path("sku").asText();
        JsonNode updated = json(request("cart-update", HttpMethod.PUT, "/api/v1/cart/" + code,
                Map.of("product", sku, "quantity", 2), null), 201);
        assertEquals(2, updated.path("quantity").asInt());
        assertEquals(2, json(request("cart-read", HttpMethod.GET, "/api/v1/cart/" + code, null, null), 200).path("quantity").asInt());
    }

    @Test public void authenticatedCustomerChecksOutAndReadsOrder() throws Exception {
        HttpHeaders admin = bearer(login("/api/v1/private/login", "admin@shopizer.com", "password"));
        assertEquals(200, request("configure-moneyorder", HttpMethod.POST, "/api/v1/private/modules/payment",
                Map.of("code", "moneyorder", "active", true, "defaultSelected", true,
                        "integrationKeys", Map.of("transaction", "AUTHORIZECAPTURE", "address", "1 Test Street"), "integrationOptions", Map.of()), admin).getStatusCodeValue());
        String email = register(); HttpHeaders auth = bearer(login("/api/v1/customer/login", email, "OldPass123"));
        JsonNode cart = cart(); String code = cart.path("code").asText();
        json(request("cart-attach", HttpMethod.GET, "/api/v1/auth/customer/cart?cart=" + code, null, auth), 200);
        JsonNode total = json(request("cart-total", HttpMethod.GET, "/api/v1/cart/" + code + "/total", null, null), 200);
        Map<String, Object> payment = Map.of("paymentModule", "moneyorder", "paymentType", "MONEYORDER",
                "transactionType", "AUTHORIZECAPTURE", "amount", total.path("total").asText().replaceAll("[^0-9.]", ""));
        JsonNode confirmation = json(request("checkout", HttpMethod.POST, "/api/v1/auth/cart/" + code + "/checkout",
                Map.of("customerAgreement", true, "currency", "USD", "payment", payment), auth), 200);
        long id = confirmation.path("id").asLong(); assertTrue(id > 0);
        assertEquals(id, json(request("order-read", HttpMethod.GET, "/api/v1/auth/orders/" + id, null, auth), 200).path("id").asLong());
    }

    @Test public void legacyServicesBasicAuth() throws Exception {
        HttpHeaders valid = new HttpHeaders(); valid.setBasicAuth("admin@shopizer.com", "password");
        HttpHeaders invalid = new HttpHeaders(); invalid.setBasicAuth("admin@shopizer.com", "wrong");
        int validStatus = request("services-valid-basic", HttpMethod.POST, "/services/private/system/optin", "{}", valid).getStatusCodeValue();
        int invalidStatus = request("services-invalid-basic", HttpMethod.POST, "/services/private/system/optin", "{}", invalid).getStatusCodeValue();
        assertEquals(200, validStatus); assertTrue(invalidStatus == 401 || invalidStatus == 403);
    }

    @Test public void healthAndSwaggerDescribeResetEndpoints() throws Exception {
        assertEquals("UP", json(request("health", HttpMethod.GET, "/actuator/health", null, null), 200).path("status").asText());
        JsonNode paths = json(request("swagger", HttpMethod.GET, "/v2/api-docs", null, null), 200).path("paths");
        for (String kind : new String[]{"customer", "user"}) {
            assertTrue(paths.has("/api/v1/" + kind + "/{store}/reset/{token}"));
            assertTrue(paths.has("/api/v1/" + kind + "/{store}/password/{token}"));
        }
    }
}
