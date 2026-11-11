package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontUatTest {
    @Inject
    private TestRestTemplate http;

    @Test
    void adminJwtProtectsPrivateApi() {
        HttpHeaders admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
        expect(200, request(HttpMethod.GET, "/api/v1/private/customers", null, admin));
        expect(401, request(HttpMethod.GET, "/api/v1/private/customers", null, new HttpHeaders()));
    }

    @Test
    void registeredCustomerCanLoginAndReadProfile() {
        HttpHeaders customer = registerCustomer();
        JsonNode profile = expect(200, request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, customer));
        assertTrue(profile.path("emailAddress").asText().endsWith("@example.com"), profile.toString());
    }

    @Test
    void browseStoreCategoriesProductsAndDetail() {
        JsonNode product = createProduct();
        assertEquals("DEFAULT", expect(200, get("/api/v1/store/DEFAULT")).path("code").asText());
        assertNotNull(expect(200, get("/api/v1/category?store=DEFAULT")));
        JsonNode products = expect(200, get("/api/v1/products?store=DEFAULT"));
        assertTrue(products.path("products").isArray(), products.toString());
        JsonNode detail = expect(200, get("/api/v2/product/" + product.path("sku").asText()));
        assertEquals(product.path("sku").asText(), detail.path("sku").asText());
    }

    @Test
    void cartCreateAddUpdateAndReadBack() {
        JsonNode product = createProduct();
        String sku = product.path("sku").asText();
        String secondSku = createProduct().path("sku").asText();
        JsonNode cart = createCart(sku);
        String url = "/api/v1/cart/" + cart.path("code").asText();
        assertEquals(1, cart.path("quantity").asInt());
        JsonNode added = expect(201, request(HttpMethod.PUT, url,
                Map.of("product", secondSku, "quantity", 1), new HttpHeaders()));
        assertEquals(2, added.path("quantity").asInt());
        JsonNode updated = expect(201, request(HttpMethod.POST, url + "/multi",
                List.of(Map.of("product", sku, "quantity", 3)), new HttpHeaders()));
        assertEquals(4, updated.path("quantity").asInt());
        JsonNode fetched = expect(200, get(url));
        assertEquals(4, fetched.path("quantity").asInt());
    }

    @Test
    void customerChecksOutWithMoneyOrderAndReadsOrder() {
        HttpHeaders admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
        ResponseEntity<JsonNode> configured = request(HttpMethod.POST, "/api/v1/private/modules/payment",
                Map.of("code", "moneyorder", "active", true, "defaultSelected", true,
                        "integrationKeys", Map.of("address", "1 Test Street"), "integrationOptions", Map.of()), admin);
        assertEquals(200, configured.getStatusCodeValue(), configured.toString());
        HttpHeaders customer = registerCustomer();
        JsonNode product = createProduct();
        JsonNode cart = createCart(product.path("sku").asText());
        JsonNode confirmation = expect(200, request(HttpMethod.POST,
                "/api/v1/auth/cart/" + cart.path("code").asText() + "/checkout",
                Map.of("currency", "CAD", "customerAgreement", true, "payment",
                        Map.of("paymentType", "MONEYORDER", "paymentModule", "moneyorder",
                                "transactionType", "AUTHORIZECAPTURE", "amount", "10.00")), customer));
        long id = confirmation.path("id").asLong();
        assertTrue(id > 0, confirmation.toString());
        assertEquals(id, expect(200, request(HttpMethod.GET, "/api/v1/auth/orders/" + id,
                null, customer)).path("id").asLong());
    }

    @Test
    void legacyServicesBasicAuthentication() {
        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        expect(200, request(HttpMethod.POST, "/services/private/system/optin", Map.of(), valid));
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
        expect(401, request(HttpMethod.POST, "/services/private/system/optin", Map.of(), invalid));
    }

    @Test
    void operationalEndpointsAreAvailable() {
        assertEquals("UP", expect(200, get("/actuator/health")).path("status").asText());
        assertEquals("2.0", expect(200, get("/v2/api-docs")).path("swagger").asText());
    }

    private HttpHeaders registerCustomer() {
        String email = "uat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20) + "@example.com";
        Map<String, Object> address = Map.of("firstName", "Uat", "lastName", "Customer", "country", "BE",
                "address", "1 Test Street", "city", "Brussels", "postalCode", "1000", "phone", "123456789");
        expect(200, request(HttpMethod.POST, "/api/v1/customer/register",
                Map.of("emailAddress", email, "password", "clear123", "gender", "M", "language", "en",
                        "storeCode", "DEFAULT", "billing", address, "delivery", address), new HttpHeaders()));
        return login("/api/v1/customer/login", email, "clear123");
    }

    private HttpHeaders login(String path, String username, String password) {
        String token = expect(200, request(HttpMethod.POST, path,
                Map.of("username", username, "password", password), new HttpHeaders())).path("token").asText();
        assertTrue(token.matches("[^.]+\\.[^.]+\\.[^.]+"), "Expected a JWT");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private JsonNode createProduct() {
        String sku = "uat-" + UUID.randomUUID();
        HttpHeaders admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
        JsonNode category = expect(201, request(HttpMethod.POST, "/api/v1/private/category?store=DEFAULT",
                Map.of("code", sku, "visible", true, "depth", 1, "sortOrder", 1,
                        "descriptions", List.of(Map.of("language", "en", "name", sku, "friendlyUrl", sku))), admin));
        JsonNode entity = expect(201, request(HttpMethod.POST, "/api/v1/private/product?store=DEFAULT",
                Map.of("sku", sku, "available", true, "price", 10, "quantity", 100,
                        "productSpecifications", Map.of("manufacturer", "DEFAULT"),
                        "inventory", Map.of("quantity", 100, "sku", sku,
                                "price", Map.of("defaultPrice", true, "price", 10)),
                        "categories", List.of(Map.of("id", category.path("id").asLong())),
                        "descriptions", List.of(Map.of("name", sku, "language", "en"))), admin));
        assertTrue(entity.path("id").asLong() > 0, entity.toString());
        return expect(200, get("/api/v2/product/" + sku));
    }

    private JsonNode createCart(String sku) {
        return expect(201, request(HttpMethod.POST, "/api/v1/cart",
                Map.of("product", sku, "quantity", 1), new HttpHeaders()));
    }

    private ResponseEntity<JsonNode> get(String path) {
        return request(HttpMethod.GET, path, null, new HttpHeaders());
    }

    private ResponseEntity<JsonNode> request(HttpMethod method, String path, Object body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode expect(int status, ResponseEntity<JsonNode> response) {
        assertEquals(status, response.getStatusCodeValue(), response.toString());
        assertNotNull(response.getBody());
        return response.getBody();
    }
}
