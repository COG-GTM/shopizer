package com.salesmanager.test.shop.uat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.application.ShopApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShopUserJourneysTest {
    @Autowired private TestRestTemplate http;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void adminSignsInAndPrivateResourcesRejectUnauthenticatedRequests() throws Exception {
        String token = login("/api/v1/private/login", "admin@shopizer.com", "password");
        assertTrue(call(HttpMethod.GET, "/api/v1/private/customers", null, token, 200).has("customers"));
        call(HttpMethod.GET, "/api/v1/private/customers", null, null, 401);
        String[] segments = token.split("\\.");
        String signature = segments[2];
        String wrongSignature = (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);
        call(HttpMethod.GET, "/api/v1/private/customers", null,
                segments[0] + "." + segments[1] + "." + wrongSignature, 500);
    }

    @Test
    void customerRegistersSignsInAndReadsOwnProfile() throws Exception {
        ObjectNode customer = customer();
        register(customer);
        String token = login("/api/v1/customer/login", customer.path("emailAddress").asText(), "clear123");
        JsonNode profile = call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, token, 200);
        assertEquals(customer.path("emailAddress").asText(), profile.path("emailAddress").asText());
    }

    @Test
    void visitorBrowsesStoreCategoriesProductsAndProductDetail() throws Exception {
        createCatalogProduct();
        JsonNode store = call(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200);
        assertEquals("DEFAULT", store.path("code").asText());
        JsonNode categories = call(HttpMethod.GET, "/api/v1/category?store=DEFAULT", null, null, 200);
        assertFalse(categories.isNull());
        JsonNode product = listedProduct();
        long id = product.path("id").asLong();
        assertTrue(id > 0, product.toString());
        JsonNode detail = call(HttpMethod.GET, "/api/v2/product/" + product.path("sku").asText(), null, null, 200);
        assertEquals(id, detail.path("id").asLong());
        assertEquals(product.path("sku").asText(), detail.path("sku").asText());
    }

    @Test
    void visitorCreatesCartUpdatesQuantityAndReadsItBack() throws Exception {
        createCatalogProduct();
        JsonNode product = listedProduct();
        JsonNode cart = call(HttpMethod.POST, "/api/v1/cart", item(product, 1), null, 201);
        assertEquals(1, cart.path("quantity").asInt());
        String path = "/api/v1/cart/" + cart.path("code").asText();
        assertEquals(3, call(HttpMethod.PUT, path, item(product, 3), null, 201).path("quantity").asInt());
        JsonNode persisted = call(HttpMethod.GET, path, null, null, 200);
        assertEquals(3, persisted.path("quantity").asInt());
        assertEquals(cart.path("code").asText(), persisted.path("code").asText());
    }

    @Test
    void authenticatedCustomerChecksOutAndReadsOrder() throws Exception {
        createCatalogProduct();
        ObjectNode configuration = json.createObjectNode().put("code", "moneyorder").put("active", true).put("defaultSelected", true);
        configuration.putObject("integrationKeys").put("address", "1 Test Street");
        call(HttpMethod.POST, "/api/v1/private/modules/payment", configuration,
                login("/api/v1/private/login", "admin@shopizer.com", "password"), 200);
        ObjectNode customer = customer();
        register(customer);
        String token = login("/api/v1/customer/login", customer.path("emailAddress").asText(), "clear123");
        JsonNode cart = call(HttpMethod.POST, "/api/v1/cart", item(listedProduct(), 1), null, 201);
        ObjectNode order = json.createObjectNode().put("currency", "CAD").put("customerAgreement", true);
        customer.remove("password");
        customer.remove("repeatPassword");
        order.set("customer", customer);
        order.putObject("payment").put("paymentModule", "moneyorder").put("paymentType", "MONEYORDER")
                .put("transactionType", "AUTHORIZECAPTURE").put("amount", "10.00");
        JsonNode confirmation = call(HttpMethod.POST, "/api/v1/auth/cart/" + cart.path("code").asText() + "/checkout", order, token, 200);
        long id = confirmation.path("id").asLong();
        assertTrue(id > 0, confirmation.toString());
        assertEquals(id, call(HttpMethod.GET, "/api/v1/auth/orders/" + id, null, token, 200).path("id").asLong());
        String admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
        assertEquals(id, call(HttpMethod.GET, "/api/v1/private/orders/" + id, null, admin, 200).path("id").asLong());
    }

    @Test
    void legacyServiceAcceptsAdminBasicAndRejectsInvalidCredentials() throws Exception {
        for (String password : new String[]{"password", "incorrect"}) {
            HttpHeaders headers = headers();
            headers.setBasicAuth("admin@shopizer.com", password);
            ResponseEntity<String> response = http.exchange("/services/private/system/optin", HttpMethod.POST,
                    new HttpEntity<>("{}", headers), String.class);
            assertEquals(password.equals("password") ? 200 : 401, response.getStatusCodeValue(), response.getBody());
            if (password.equals("password")) assertEquals(200, json.readTree(response.getBody()).path("status").asInt());
        }
    }

    @Test
    void healthAndOpenApiArePublicAndReadable() throws Exception {
        assertEquals("UP", call(HttpMethod.GET, "/actuator/health", null, null, 200).path("status").asText());
        assertTrue(call(HttpMethod.GET, "/v3/api-docs", null, null, 200).has("openapi"));
    }

    private void createCatalogProduct() throws Exception {
        String sku = "uat" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode product = json.createObjectNode().put("sku", sku).put("available", true).put("price", 10).put("quantity", 100);
        product.putObject("productSpecifications").put("manufacturer", "DEFAULT");
        product.putArray("descriptions").addObject().put("name", sku).put("language", "en");
        ObjectNode inventory = product.putObject("inventory").put("quantity", 100).put("sku", sku);
        inventory.putObject("price").put("defaultPrice", true).put("price", 10);
        call(HttpMethod.POST, "/api/v1/private/product", product,
                login("/api/v1/private/login", "admin@shopizer.com", "password"), 201);
    }

    private JsonNode listedProduct() throws Exception {
        JsonNode products = call(HttpMethod.GET, "/api/v1/products?store=DEFAULT", null, null, 200).path("products");
        assertTrue(products.isArray() && products.size() > 0, products.toString());
        return products.get(0);
    }

    private ObjectNode item(JsonNode product, int quantity) {
        return json.createObjectNode().put("product", product.path("sku").asText()).put("quantity", quantity);
    }

    private ObjectNode customer() {
        ObjectNode customer = json.createObjectNode().put("emailAddress", "uat" + UUID.randomUUID().toString().replace("-", "") + "@example.test")
                .put("gender", "M").put("language", "en").put("storeCode", "DEFAULT").put("password", "clear123").put("repeatPassword", "clear123");
        customer.putObject("billing").put("firstName", "Uat").put("lastName", "Customer").put("country", "BE")
                .put("city", "Brussels").put("address", "1 Test Street").put("postalCode", "1000").put("phone", "123456789");
        return customer;
    }

    private void register(ObjectNode customer) throws Exception {
        assertTrue(call(HttpMethod.POST, "/api/v1/customer/register", customer, null, 200).path("id").asLong() > 0);
    }

    private String login(String path, String username, String password) throws Exception {
        String token = call(HttpMethod.POST, path, json.createObjectNode().put("username", username).put("password", password), null, 200)
                .path("token").asText();
        assertEquals(3, token.split("\\.").length);
        return token;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private JsonNode call(HttpMethod method, String path, JsonNode body, String token, int status) throws Exception {
        HttpHeaders headers = headers();
        if (token != null) headers.setBearerAuth(token);
        ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(body == null ? null : body.toString(), headers), String.class);
        assertEquals(status, response.getStatusCodeValue(), method + " " + path + ": " + response.getBody());
        if (response.getBody() == null || response.getBody().isEmpty()) return json.nullNode();
        assertNotNull(response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
        return json.readTree(response.getBody());
    }
}
