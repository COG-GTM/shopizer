package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public abstract class UatSupport extends ServicesTestSupport {
    protected final ObjectMapper mapper = new ObjectMapper();

    protected ResponseEntity<String> request(String name, HttpMethod method, String path, Object body, HttpHeaders headers) {
        ResponseEntity<String> response = testRestTemplate.exchange(path, method,
                new HttpEntity<>(body, headers), String.class);
        System.out.println("UAT_STATUS " + name + "=" + response.getStatusCodeValue());
        return response;
    }

    protected JsonNode json(ResponseEntity<String> response, int status) throws Exception {
        assertEquals(response.getBody(), status, response.getStatusCodeValue());
        return mapper.readTree(response.getBody());
    }

    protected HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); return headers;
    }

    protected String login(String path, String email, String password) throws Exception {
        JsonNode response = json(request("login:" + path, HttpMethod.POST, path,
                java.util.Map.of("username", email, "password", password), null), 200);
        String token = response.path("token").asText(); assertFalse(token.isEmpty()); return token;
    }

    protected String register() throws Exception {
        String email = "uat-" + UUID.randomUUID() + "@test.com";
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email); customer.setPassword("OldPass123"); customer.setGender("M");
        customer.setLanguage("en"); customer.setStoreCode("DEFAULT");
        Address address = new Address(); address.setFirstName("Uat"); address.setLastName("Test");
        address.setCountry("BE"); address.setAddress("1 Test Street"); address.setCity("Brussels"); address.setPostalCode("1000");
        customer.setBilling(address); customer.setDelivery(address);
        assertEquals(200, request("register", HttpMethod.POST, "/api/v1/customer/register", customer, null).getStatusCodeValue());
        return email;
    }
}
