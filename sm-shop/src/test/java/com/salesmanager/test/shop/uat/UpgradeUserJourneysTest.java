package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class UpgradeUserJourneysTest extends ServicesTestSupport {

    @Autowired
    private ObjectMapper mapper;

    @Test
    public void adminLoginGrantsPrivateAccessButAnonymousRequestsAreRejected() {
        JsonNode login = call(HttpMethod.POST, "/api/v1/private/login",
                credentials("admin@shopizer.com", "password"), null, 200);
        assertTrue(login.path("id").isNumber());
        assertFalse(login.path("token").asText().isEmpty());
        JsonNode users = call(HttpMethod.GET, "/api/v1/private/users", null,
                bearer(login.path("token").asText()), 200);
        assertTrue(users.path("data").isArray());
        call(HttpMethod.GET, "/api/v1/private/users", null, null, 401);
    }

    @Test
    public void registeredCustomerCanLoginAndReadOwnProfile() {
        String email = "upgrade-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        HttpHeaders customer = registerAndLogin(email);
        JsonNode profile = call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, customer, 200);
        assertEquals(email, profile.path("emailAddress").asText());
        assertTrue(profile.path("id").isNumber());
        call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, null, 401);
        call(HttpMethod.GET, "/api/v1/private/users", null, customer, 401);
    }

    @Test
    public void shopperCanBrowseStoreCategoriesProductsAndDetail() {
        String sku = "browse-" + UUID.randomUUID().toString().substring(0, 8);
        ReadableProduct product = sampleProduct(sku);
        JsonNode store = call(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200);
        assertEquals("DEFAULT", store.path("code").asText());
        JsonNode categories = call(HttpMethod.GET, "/api/v1/category?store=DEFAULT", null, null, 200);
        assertTrue(categories.path("categories").isArray());
        JsonNode products = call(HttpMethod.GET, "/api/v1/products?store=DEFAULT", null, null, 200);
        assertTrue(products.path("products").isArray());
        JsonNode detail = call(HttpMethod.GET, "/api/v2/product/" + sku, null, null, 200);
        assertEquals(sku, detail.path("sku").asText());
        assertEquals(product.getId().longValue(), detail.path("id").asLong());
    }

    @Test
    public void shopperCanCreateUpdateAndReadCartThenCheckoutAsCustomer() {
        String sku = "cart-" + UUID.randomUUID().toString().substring(0, 8);
        sampleProduct(sku);
        ObjectNode item = mapper.createObjectNode().put("product", sku).put("quantity", 1);
        JsonNode cart = call(HttpMethod.POST, "/api/v1/cart", item, null, 201);
        String code = cart.path("code").asText();
        assertFalse(code.isEmpty());
        assertEquals(1, cart.path("quantity").asInt());

        item.put("quantity", 2);
        call(HttpMethod.POST, "/api/v1/cart/" + code + "/multi",
                mapper.createArrayNode().add(item), null, 201);
        JsonNode saved = call(HttpMethod.GET, "/api/v1/cart/" + code, null, null, 200);
        assertEquals(code, saved.path("code").asText());
        assertEquals(2, saved.path("quantity").asInt());
        assertTrue(saved.path("total").isNumber());
        assertTrue(saved.path("products").isArray());

        ObjectNode paymentConfig = mapper.createObjectNode().put("code", "moneyorder")
                .put("active", true).put("defaultSelected", true);
        paymentConfig.putObject("integrationKeys").put("transaction", "AUTHORIZECAPTURE")
                .put("address", "1 Test Street, Montreal");
        paymentConfig.putObject("integrationOptions");
        call(HttpMethod.POST, "/api/v1/private/modules/payment", paymentConfig, getHeader(), 200);

        HttpHeaders customer = registerAndLogin("order-" + UUID.randomUUID().toString().substring(0, 8)
                + "@example.test");
        ObjectNode order = mapper.createObjectNode().put("currency", "CAD").put("customerAgreement", true);
        order.putObject("payment").put("paymentType", "MONEYORDER").put("paymentModule", "moneyorder")
                .put("transactionType", "AUTHORIZECAPTURE").put("amount", saved.path("total").asText());
        JsonNode confirmation = call(HttpMethod.POST, "/api/v1/auth/cart/" + code + "/checkout",
                order, customer, 200);
        long id = confirmation.path("id").asLong();
        assertTrue(id > 0);
        JsonNode persisted = call(HttpMethod.GET, "/api/v1/auth/orders/" + id, null, customer, 200);
        assertEquals(id, persisted.path("id").asLong());
    }

    @Test
    public void servicesBasicAuthenticationStillChallengesInvalidCredentials() {
        String path = "/services/private/upgrade-auth-probe";
        call(HttpMethod.GET, path, null, null, 401);
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "incorrect");
        ResponseEntity<JsonNode> rejected = testRestTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(invalid), JsonNode.class);
        assertEquals(401, rejected.getStatusCodeValue());
        assertNotNull(rejected.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        // No /services controller is registered: successful authentication reaches MVC's 404.
        call(HttpMethod.GET, path, null, valid, 404);
    }

    private HttpHeaders registerAndLogin(String email) {
        ObjectNode customer = mapper.createObjectNode().put("emailAddress", email).put("password", "Upgrade1!")
                .put("gender", "M").put("language", "en").put("storeCode", "DEFAULT");
        customer.putObject("billing").put("firstName", "Upgrade").put("lastName", "Customer")
                .put("country", "CA").put("zone", "QC").put("stateProvince", "Quebec")
                .put("city", "Montreal").put("postalCode", "H2X1Y4").put("address", "1 Test Street");
        call(HttpMethod.POST, "/api/v1/customer/register", customer, null, 200);
        JsonNode login = call(HttpMethod.POST, "/api/v1/customer/login",
                credentials(email, "Upgrade1!"), null, 200);
        assertFalse(login.path("token").asText().isEmpty());
        return bearer(login.path("token").asText());
    }

    private ObjectNode credentials(String username, String password) {
        return mapper.createObjectNode().put("username", username).put("password", password);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private JsonNode call(HttpMethod method, String path, Object body, HttpHeaders headers, int status) {
        HttpHeaders requestHeaders = new HttpHeaders();
        if (headers != null) {
            requestHeaders.putAll(headers);
        }
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = testRestTemplate.exchange(path, method,
                new HttpEntity<>(body, requestHeaders), JsonNode.class);
        assertEquals(method + " " + path, status, response.getStatusCodeValue());
        return response.getBody();
    }
}
