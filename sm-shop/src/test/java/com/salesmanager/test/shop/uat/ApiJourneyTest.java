package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

class ApiJourneyTest extends ServicesTestSupport {
    @Test
    void adminLoginAndBasicAuthProtectPrivateResources() {
        var headers = getHeader();
        assertTrue(headers.getFirst(HttpHeaders.AUTHORIZATION).length() > 20);
        assertEquals(200, testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getStatusCode().value());
        assertEquals(401, testRestTemplate.getForEntity("/api/v1/private/customers", String.class).getStatusCode().value());
        var valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
                .getForEntity("/services/private/probe", String.class);
        assertEquals(404, valid.getStatusCode().value());
        assertNull(valid.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
        assertEquals(401, testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong")
                .getForEntity("/services/private/probe", String.class).getStatusCode().value());
    }

    @Test
    void customerRegistersLogsInAndReadsOwnProfileAndOrders() {
        String email = "uat-" + UUID.randomUUID() + "@example.com";
        var customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");
        var billing = new Address();
        billing.setFirstName("Uat");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);
        assertEquals(200, testRestTemplate.postForEntity("/api/v1/customer/register", customer, String.class).getStatusCode().value());
        var login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new AuthenticationRequest(email, "clear123"), AuthenticationResponse.class);
        assertEquals(200, login.getStatusCode().value());
        assertNotNull(login.getBody().getToken());
        var headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().getToken());
        var profile = testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(200, profile.getStatusCode().value());
        assertTrue(profile.getBody().contains(email));
        assertEquals(200, testRestTemplate.exchange("/api/v1/auth/orders", HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getStatusCode().value());
    }

    @Test
    void browseAndCreateUpdateReadCart() {
        var product = sampleProduct("uat-cart");
        for (String path : new String[]{"/api/v1/store/DEFAULT", "/api/v1/category", "/api/v1/products?store=DEFAULT",
                "/api/v2/product/" + product.getSku()}) {
            assertEquals(200, testRestTemplate.getForEntity(path, String.class).getStatusCode().value(), path);
        }
        var item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        var created = testRestTemplate.postForEntity("/api/v1/cart", item, ReadableShoppingCart.class);
        assertEquals(201, created.getStatusCode().value());
        String path = "/api/v1/cart/" + created.getBody().getCode();
        item.setQuantity(2);
        var updated = testRestTemplate.exchange(path + "/multi", HttpMethod.POST,
                new HttpEntity<>(new PersistableShoppingCartItem[]{item}), ReadableShoppingCart.class);
        assertEquals(201, updated.getStatusCode().value());
        assertEquals(2, updated.getBody().getQuantity());
        var read = testRestTemplate.getForEntity(path, ReadableShoppingCart.class);
        assertEquals(200, read.getStatusCode().value());
        assertEquals(2, read.getBody().getQuantity());
    }

    @Test
    void healthAndOpenApiArePublic() throws Exception {
        var health = testRestTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(200, health.getStatusCode().value());
        assertEquals("UP", new ObjectMapper().readTree(health.getBody()).path("status").asText());
        var docs = testRestTemplate.getForEntity("/v3/api-docs", String.class);
        assertEquals(200, docs.getStatusCode().value());
        assertTrue(new ObjectMapper().readTree(docs.getBody()).path("openapi").asText().startsWith("3."));
    }
}
