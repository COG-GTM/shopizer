package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class CustomerAccountJourneyTest extends ServicesTestSupport {

    @Test
    public void customerCanRegisterLoginAndReadTheirOwnProfile() {
        String email = "journey-" + UUID.randomUUID() + "@example.test";
        String password = "Journey-test-123";
        String body = "{\"emailAddress\":\"" + email + "\",\"password\":\"" + password
                + "\",\"language\":\"en\",\"storeCode\":\"DEFAULT\","
                + "\"billing\":{\"firstName\":\"Journey\",\"lastName\":\"Customer\",\"country\":\"BE\"}}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> registered = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(body, headers), JsonNode.class);
        assertEquals(OK, registered.getStatusCode());
        assertNotNull(registered.getBody());
        assertTrue(registered.getBody().path("id").isIntegralNumber());
        long id = registered.getBody().path("id").longValue();
        assertTrue(id > 0);

        ResponseEntity<JsonNode> login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new AuthenticationRequest(email, password), JsonNode.class);
        assertEquals(OK, login.getStatusCode());
        assertNotNull(login.getBody());
        assertEquals(id, login.getBody().path("id").longValue());
        assertTrue(login.getBody().path("token").isTextual());
        assertTrue(!login.getBody().path("token").textValue().isEmpty());
        headers.setBearerAuth(login.getBody().path("token").textValue());

        ResponseEntity<JsonNode> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertEquals(OK, profile.getStatusCode());
        assertNotNull(profile.getBody());
        assertEquals(id, profile.getBody().path("id").longValue());
        assertEquals(email, profile.getBody().path("emailAddress").textValue());
        assertEquals("Journey", profile.getBody().path("billing").path("firstName").textValue());
    }

    @Test
    public void adminLoginGrantsPrivateAccessWhileMissingTokenIsDenied() {
        ResponseEntity<JsonNode> login = testRestTemplate.postForEntity("/api/v1/private/login",
                new AuthenticationRequest("admin@shopizer.com", "password"), JsonNode.class);
        assertEquals(OK, login.getStatusCode());
        assertNotNull(login.getBody());
        assertTrue(login.getBody().path("id").isIntegralNumber());
        assertTrue(login.getBody().path("token").isTextual());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().path("token").textValue());
        ResponseEntity<JsonNode> customers = testRestTemplate.exchange("/api/v1/private/customers",
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertEquals(OK, customers.getStatusCode());
        assertNotNull(customers.getBody());

        assertDenied(testRestTemplate.getForEntity("/api/v1/private/customers", String.class));
    }

    @Test
    public void shopperCanBrowseAndCreateUpdateAndReloadCart() {
        String sku = "journey-" + UUID.randomUUID().toString().substring(0, 8);
        ReadableProduct product = sampleProduct(sku);
        assertNotNull(product.getId());
        assertEquals(OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", JsonNode.class).getStatusCode());
        assertEquals(OK, testRestTemplate.getForEntity("/api/v1/category", JsonNode.class).getStatusCode());
        ResponseEntity<JsonNode> products = testRestTemplate.getForEntity("/api/v1/products", JsonNode.class);
        assertEquals(OK, products.getStatusCode());
        assertNotNull(products.getBody());
        assertTrue(products.getBody().path("products").isArray());
        ResponseEntity<JsonNode> detail = testRestTemplate.getForEntity("/api/v2/product/" + sku, JsonNode.class);
        assertEquals(OK, detail.getStatusCode());
        assertNotNull(detail.getBody());
        assertEquals(sku, detail.getBody().path("sku").textValue());

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sku);
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
                "/api/v1/cart", item, ReadableShoppingCart.class);
        assertEquals(CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        String code = created.getBody().getCode();
        assertNotNull(code);
        assertEquals(1, created.getBody().getQuantity());

        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
                "/api/v1/cart/" + code, HttpMethod.PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
        assertEquals(CREATED, updated.getStatusCode());
        ResponseEntity<ReadableShoppingCart> reloaded = testRestTemplate.getForEntity(
                "/api/v1/cart/" + code, ReadableShoppingCart.class);
        assertEquals(OK, reloaded.getStatusCode());
        assertNotNull(reloaded.getBody());
        assertEquals(code, reloaded.getBody().getCode());
        assertEquals(2, reloaded.getBody().getQuantity());
        assertEquals(sku, reloaded.getBody().getProducts().get(0).getSku());
    }

    @Test
    public void healthAndApiDocumentationRemainAvailable() {
        ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
        assertEquals(OK, health.getStatusCode());
        assertNotNull(health.getBody());
        assertEquals("UP", health.getBody().path("status").textValue());
        ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
        assertEquals(OK, docs.getStatusCode());
        assertNotNull(docs.getBody());
        assertEquals("2.0", docs.getBody().path("swagger").textValue());
        assertTrue(docs.getBody().path("paths").has("/api/v1/customer/register"));
    }

    private void assertDenied(ResponseEntity<?> response) {
        int status = response.getStatusCodeValue();
        assertTrue("Expected 401/403 but received " + status, status == 401 || status == 403);
    }
}
