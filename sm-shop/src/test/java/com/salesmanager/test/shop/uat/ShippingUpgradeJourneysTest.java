package com.salesmanager.test.shop.uat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;

class ShippingUpgradeJourneysTest extends ServicesTestSupport {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void adminCanDiscoverCanadaPostButAnonymousCannot() throws Exception {
        JsonNode modules = request(HttpMethod.GET, "/api/v1/private/modules/shipping", null, getHeader(), 200);
        assertTrue(modules.isArray());
        boolean found = false;
        for (JsonNode module : modules) found |= "canadapost".equals(module.path("code").asText());
        assertTrue(found, modules.toString());
        assertDenied(new HttpHeaders());
    }

    @Test
    void customerCanRegisterLoginAndReadTheirOwnProfile() throws Exception {
        String email = unique("customer") + "@example.test";
        HttpHeaders headers = customer(email);
        JsonNode profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, headers, 200);
        assertEquals(email, profile.path("emailAddress").asText());
        assertTrue(profile.path("id").isIntegralNumber());
    }

    @Test
    void storefrontCanBrowseStoreCategoriesProductsAndDetail() throws Exception {
        ReadableProduct product = sampleProduct(unique("browse"));
        JsonNode store = request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200);
        assertEquals("DEFAULT", store.path("code").asText());
        JsonNode categories = request(HttpMethod.GET, "/api/v1/category/", null, null, 200);
        assertTrue(categories.isArray() || categories.path("categories").isArray(), categories.toString());
        JsonNode products = request(HttpMethod.GET, "/api/v1/products?store=DEFAULT", null, null, 200);
        assertTrue(products.path("products").isArray(), products.toString());
        JsonNode detail = request(HttpMethod.GET, "/api/v2/product/" + product.getSku(), null, null, 200);
        assertEquals(product.getSku(), detail.path("sku").asText());
        assertTrue(detail.path("id").isIntegralNumber());
    }

    @Test
    void shopperCanCreateUpdateAndReadCart() throws Exception {
        ReadableProduct product = sampleProduct(unique("cart"));
        JsonNode cart = createCart(product);
        String code = cart.path("code").asText();
        assertFalse(code.isEmpty());
        JsonNode updated = request(HttpMethod.PUT, "/api/v1/cart/" + code,
                item(product.getSku(), 2), null, 201);
        assertEquals(2, updated.path("quantity").asInt());
        JsonNode read = request(HttpMethod.GET, "/api/v1/cart/" + code, null, null, 200);
        assertEquals(code, read.path("code").asText());
        assertEquals(2, read.path("quantity").asInt());
        assertTrue(read.path("total").isNumber());
    }

    @Test
    void authenticatedCustomerCanCheckoutWithOfflinePaymentAndReadOrder() throws Exception {
        HttpHeaders admin = getHeader();
        JsonNode original = request(HttpMethod.GET, "/api/v1/private/modules/payment/moneyorder", null, admin, 200);
        ((com.fasterxml.jackson.databind.node.ObjectNode) original).with("integrationKeys")
                .put("address", original.path("integrationKeys").path("address").asText("1 Test Street"));
        request(HttpMethod.POST, "/api/v1/private/modules/payment",
                "{\"code\":\"moneyorder\",\"active\":true,\"integrationKeys\":{\"address\":\"1 Test Street\"},\"integrationOptions\":{}}", admin, 200);
        try {
        HttpHeaders headers = customer(unique("checkout") + "@example.test");
        ReadableProduct product = sampleProduct(unique("order"));
        JsonNode cart = createCart(product);
        String code = cart.path("code").asText();
        JsonNode total = request(HttpMethod.GET, "/api/v1/cart/" + code + "/total", null, null, 200);
        String amount = total.path("total").asText().replaceAll("[^0-9.]", "");
        assertFalse(amount.isEmpty(), total.toString());
        String order = "{\"customerAgreement\":true,\"currency\":\"USD\",\"payment\":{"
                + "\"paymentModule\":\"moneyorder\",\"paymentType\":\"MONEYORDER\","
                + "\"transactionType\":\"AUTHORIZECAPTURE\",\"amount\":\"" + amount + "\"}}";
        JsonNode confirmation = request(HttpMethod.POST, "/api/v1/auth/cart/" + code + "/checkout", order, headers, 200);
        assertTrue(confirmation.path("id").isIntegralNumber(), confirmation.toString());
        long id = confirmation.path("id").asLong();
        assertTrue(id > 0);
        JsonNode read = request(HttpMethod.GET, "/api/v1/auth/orders/" + id, null, headers, 200);
        assertEquals(id, read.path("id").asLong());
        } finally {
            request(HttpMethod.POST, "/api/v1/private/modules/payment", original.toString(), admin, 200);
        }
    }

    @Test
    void servicesStillUsesBasicAuthentication() throws Exception {
        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        JsonNode response = request(HttpMethod.POST, "/services/private/system/optin", "{}", valid, 200);
        assertEquals(200, response.path("status").asInt());
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
        request(HttpMethod.POST, "/services/private/system/optin", "{}", invalid, 401);
    }

    @Test
    void healthAndSwaggerRemainAvailableAndXmlNegotiationDoesNotChange() throws Exception {
        JsonNode health = request(HttpMethod.GET, "/actuator/health", null, null, 200);
        assertEquals("UP", health.path("status").asText());
        JsonNode docs = request(HttpMethod.GET, "/v2/api-docs", null, null, 200);
        assertEquals("2.0", docs.path("swagger").asText());
        assertTrue(docs.path("paths").isObject());
        HttpHeaders xml = new HttpHeaders();
        xml.set(HttpHeaders.ACCEPT, "application/xml");
        ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/products?store=DEFAULT",
                HttpMethod.GET, new HttpEntity<>(xml), String.class);
        assertEquals(406, response.getStatusCodeValue());
    }

    private JsonNode createCart(ReadableProduct product) throws Exception {
        return request(HttpMethod.POST, "/api/v1/cart", item(product.getSku(), 1), null, 201);
    }

    private String item(String sku, int quantity) {
        return "{\"product\":\"" + sku + "\",\"quantity\":" + quantity + "}";
    }

    private HttpHeaders customer(String email) throws Exception {
        String registration = "{\"emailAddress\":\"" + email + "\",\"password\":\"clear123\","
                + "\"gender\":\"M\",\"language\":\"en\",\"storeCode\":\"DEFAULT\","
                + "\"billing\":{\"firstName\":\"Upgrade\",\"lastName\":\"Test\",\"country\":\"US\","
                + "\"zone\":\"NY\",\"address\":\"1 Test St\",\"city\":\"Albany\",\"postalCode\":\"12207\"}}";
        request(HttpMethod.POST, "/api/v1/customer/register", registration, null, 200);
        JsonNode login = request(HttpMethod.POST, "/api/v1/customer/login",
                "{\"username\":\"" + email + "\",\"password\":\"clear123\"}", null, 200);
        String token = login.path("token").asText();
        assertEquals(3, token.split("\\.").length);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private void assertDenied(HttpHeaders headers) {
        int status = testRestTemplate.exchange("/api/v1/private/modules/shipping", HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getStatusCodeValue();
        assertTrue(status == 401 || status == 403, "Unexpected status: " + status);
    }

    private JsonNode request(HttpMethod method, String path, String body, HttpHeaders headers, int expected) throws Exception {
        HttpHeaders actualHeaders = new HttpHeaders();
        if (headers != null) actualHeaders.putAll(headers);
        actualHeaders.setContentType(MediaType.APPLICATION_JSON);
        actualHeaders.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        ResponseEntity<String> response = testRestTemplate.exchange(path, method,
                new HttpEntity<>(body, actualHeaders), String.class);
        assertEquals(expected, response.getStatusCodeValue(), method + " " + path + ": " + response.getBody());
        return response.getBody() == null || response.getBody().isEmpty() ? json.nullNode() : json.readTree(response.getBody());
    }

    private static String unique(String prefix) {
        return "upgrade-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
