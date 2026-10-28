package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.UserPassword;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class AdminJwtJourneyTest extends ServicesTestSupport {

    private final ObjectMapper json = new ObjectMapper();

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private JsonNode call(HttpMethod method, String path, Object body, String token, int status) throws Exception {
        ResponseEntity<String> response = testRestTemplate.exchange(path, method,
                new HttpEntity<>(body, headers(token)), String.class);
        assertEquals(method + " " + path + ": " + response.getBody(), status, response.getStatusCodeValue());
        return response.hasBody() ? json.readTree(response.getBody()) : json.nullNode();
    }

    private String login(String username, String password) throws Exception {
        JsonNode login = call(HttpMethod.POST, "/api/v1/private/login",
                new AuthenticationRequest(username, password), null, 200);
        assertTrue(login.get("id").isIntegralNumber());
        assertTrue(login.get("token").isTextual());
        assertFalse(login.get("token").asText().isEmpty());
        return login.get("token").asText();
    }

    private String adminToken() throws Exception {
        return login("admin@shopizer.com", "password");
    }

    private JsonNode createAdmin(String username, String token) throws Exception {
        PersistableUser user = new PersistableUser();
        user.setUserName(username);
        user.setEmailAddress(username);
        user.setFirstName("UAT");
        user.setLastName("Admin");
        user.setDefaultLanguage("en");
        user.setActive(true);
        user.setPassword("Password1");
        user.setRepeatPassword("Password1");
        PersistableGroup group = new PersistableGroup();
        group.setName("ADMIN");
        user.getGroups().add(group);
        return call(HttpMethod.POST, "/api/v1/private/user/", user, token, 200);
    }

    @Test
    public void adminLoginPreservesContractAndPrivateEndpointRequiresToken() throws Exception {
        String token = adminToken();
        JsonNode admin = call(HttpMethod.GET, "/api/v1/private/users/1", null, token, 200);
        assertEquals(1L, admin.get("id").asLong());
        call(HttpMethod.GET, "/api/v1/private/users/1", null, null, 401);
        JsonNode rejected = call(HttpMethod.POST, "/api/v1/private/login",
                new AuthenticationRequest("admin@shopizer.com", "incorrect"), null, 401);
        assertEquals("Bad credentials", rejected.get("message").asText());
    }

    @Test
    public void passwordRotationAlsoRevokesPreviouslyRefreshedToken() throws Exception {
        String operator = adminToken();
        String username = "rotate-" + UUID.randomUUID() + "@example.com";
        long id = createAdmin(username, operator).get("id").asLong();
        String original = login(username, "Password1");
        JsonNode refreshed = call(HttpMethod.GET, "/api/v1/auth/refresh", null, original, 200);
        assertEquals(id, refreshed.get("id").asLong());
        String replacement = refreshed.get("token").asText();
        call(HttpMethod.GET, "/api/v1/private/users/" + id, null, replacement, 200);

        UserPassword password = new UserPassword();
        password.setPassword("Password1");
        password.setChangePassword("Password2");
        call(HttpMethod.PATCH, "/api/v1/private/user/" + id + "/password", password, operator, 200);
        for (String stale : new String[] {original, replacement}) {
            call(HttpMethod.GET, "/api/v1/private/users/" + id, null, stale, 401);
            call(HttpMethod.GET, "/api/v1/auth/refresh", null, stale, 401);
        }
        call(HttpMethod.POST, "/api/v1/private/login", new AuthenticationRequest(username, "Password1"), null, 401);
        String fresh = login(username, "Password2");
        call(HttpMethod.GET, "/api/v1/private/users/" + id, null, fresh, 200);
        call(HttpMethod.GET, "/api/v1/auth/refresh", null, fresh, 200);
    }

    @Test
    public void disabledAccountCannotRefreshOrLoginAndReactivationRestoresLogin() throws Exception {
        String operator = adminToken();
        String username = "disable-" + UUID.randomUUID() + "@example.com";
        long id = createAdmin(username, operator).get("id").asLong();
        String token = login(username, "Password1");
        call(HttpMethod.GET, "/api/v1/private/users/" + id, null, token, 200);
        PersistableUser state = new PersistableUser();
        state.setActive(false);
        call(HttpMethod.PATCH, "/api/v1/private/user/" + id + "/enabled", state, operator, 200);
        call(HttpMethod.GET, "/api/v1/private/users/" + id, null, token, 401);
        call(HttpMethod.GET, "/api/v1/auth/refresh", null, token, 401);
        JsonNode disabled = call(HttpMethod.POST, "/api/v1/private/login",
                new AuthenticationRequest(username, "Password1"), null, 401);
        assertEquals("Bad credentials", disabled.get("message").asText());
        state.setActive(true);
        call(HttpMethod.PATCH, "/api/v1/private/user/" + id + "/enabled", state, operator, 200);
        call(HttpMethod.GET, "/api/v1/private/users/" + id, null, login(username, "Password1"), 200);
    }

    private String customerToken() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");
        Address billing = new Address();
        billing.setFirstName("UAT");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        billing.setCity("Brussels");
        billing.setAddress("1 Test Street");
        billing.setPostalCode("1000");
        customer.setBilling(billing);
        call(HttpMethod.POST, "/api/v1/customer/register", customer, null, 200);
        JsonNode login = call(HttpMethod.POST, "/api/v1/customer/login",
                new AuthenticationRequest(email, "clear123"), null, 200);
        assertTrue(login.get("token").isTextual());
        return login.get("token").asText();
    }

    @Test
    public void customerCanRegisterLoginAndAccessOwnProfile() throws Exception {
        String token = customerToken();
        JsonNode profile = call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, token, 200);
        assertTrue(profile.get("id").isIntegralNumber());
        call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, null, 401);
    }

    private JsonNode cart(String sku) throws Exception {
        return call(HttpMethod.POST, "/api/v1/cart", json.createObjectNode().put("product", sku).put("quantity", 1),
                null, 201);
    }

    @Test
    public void storefrontCanBrowseAndCreateUpdateReadCart() throws Exception {
        ReadableProduct product = sampleProduct("uat-browse-" + UUID.randomUUID());
        assertEquals("DEFAULT", call(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200).get("code").asText());
        call(HttpMethod.GET, "/api/v1/category", null, null, 200);
        JsonNode products = call(HttpMethod.GET, "/api/v1/products", null, null, 200);
        assertTrue(products.get("products").isArray());
        assertEquals(product.getId().longValue(), call(HttpMethod.GET, "/api/v2/product/" + product.getSku(),
                null, null, 200).get("id").asLong());
        String code = cart(product.getSku()).get("code").asText();
        call(HttpMethod.PUT, "/api/v1/cart/" + code,
                json.createObjectNode().put("product", product.getSku()).put("quantity", 2), null, 201);
        JsonNode cart = call(HttpMethod.GET, "/api/v1/cart/" + code, null, null, 200);
        assertEquals(code, cart.get("code").asText());
        assertEquals(2, cart.get("quantity").asInt());
        assertTrue(cart.get("total").isNumber());
    }

    @Test
    public void authenticatedCustomerCanCheckoutOfflineAndReadOwnOrder() throws Exception {
        ObjectNode configuration = json.createObjectNode().put("code", "moneyorder")
                .put("active", true).put("defaultSelected", true);
        configuration.putObject("integrationKeys").put("transaction", "AUTHORIZECAPTURE").put("address", "1 Test Street");
        configuration.putObject("integrationOptions");
        call(HttpMethod.POST, "/api/v1/private/modules/payment", configuration, adminToken(), 200);
        String token = customerToken();
        ReadableProduct product = sampleProduct("uat-checkout-" + UUID.randomUUID());
        JsonNode cart = cart(product.getSku());
        String code = cart.get("code").asText();
        String currency = call(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200).get("currency").asText();
        ObjectNode order = json.createObjectNode().put("currency", currency).put("customerAgreement", true);
        order.putObject("payment").put("paymentModule", "moneyorder").put("paymentType", "MONEYORDER")
                .put("transactionType", "AUTHORIZECAPTURE").put("amount", cart.get("total").asText());
        JsonNode confirmation = call(HttpMethod.POST, "/api/v1/auth/cart/" + code + "/checkout", order, token, 200);
        assertTrue(confirmation.get("id").asLong() > 0);
        JsonNode saved = call(HttpMethod.GET, "/api/v1/auth/orders/" + confirmation.get("id").asLong(), null, token, 200);
        assertEquals(confirmation.get("id"), saved.get("id"));
    }

    @Test
    public void servicesBasicAuthenticationStillAcceptsValidAndRejectsInvalidCredentials() {
        ResponseEntity<String> valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
                .postForEntity("/services/private/system/optin", "{}", String.class);
        assertEquals(200, valid.getStatusCodeValue());
        ResponseEntity<String> invalid = testRestTemplate.withBasicAuth("admin@shopizer.com", "incorrect")
                .postForEntity("/services/private/system/optin", "{}", String.class);
        assertEquals(401, invalid.getStatusCodeValue());
    }

    @Test
    public void healthAndExistingSwaggerEndpointRemainAvailable() throws Exception {
        assertEquals("UP", call(HttpMethod.GET, "/actuator/health", null, null, 200).get("status").asText());
        JsonNode docs = call(HttpMethod.GET, "/v2/api-docs", null, null, 200);
        assertEquals("2.0", docs.get("swagger").asText());
        assertNotNull(docs.get("paths").get("/api/v1/private/login"));
    }
}
