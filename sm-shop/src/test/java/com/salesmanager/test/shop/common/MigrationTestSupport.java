package com.salesmanager.test.shop.common;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import org.springframework.http.*;

public class MigrationTestSupport extends ServicesTestSupport {
    protected String unique(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", ""); }

    protected ResponseEntity<JsonNode> get(String path, HttpHeaders headers) {
        return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    protected JsonNode ok(String path, HttpHeaders headers) {
        var response = get(path, headers);
        assertEquals(200, response.getStatusCode().value(), path + ": " + response.getBody());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    protected HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    protected HttpHeaders registerCustomer() {
        String email = unique("m") + "@example.com";
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");
        Address billing = new Address();
        billing.setFirstName("Migration");
        billing.setLastName("Customer");
        billing.setCountry("CA");
        billing.setZone("QC");
        billing.setAddress("123 Test Street");
        billing.setCity("Montreal");
        billing.setPostalCode("H2X1Y4");
        billing.setPhone("5145551234");
        customer.setBilling(billing);
        var registered = testRestTemplate.postForEntity("/api/v1/customer/register", customer, JsonNode.class);
        assertEquals(200, registered.getStatusCode().value(), String.valueOf(registered.getBody()));
        var login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new AuthenticationRequest(email, "clear123"), AuthenticationResponse.class);
        assertEquals(200, login.getStatusCode().value());
        assertNotNull(login.getBody());
        assertNotNull(login.getBody().getToken());
        return bearer(login.getBody().getToken());
    }
}
