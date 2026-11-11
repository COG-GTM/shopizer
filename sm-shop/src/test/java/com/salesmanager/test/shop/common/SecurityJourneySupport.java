package com.salesmanager.test.shop.common;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.springframework.http.*;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;

public class SecurityJourneySupport extends ServicesTestSupport {
    protected static final String ADMIN_PROFILE = "/api/v1/private/user/profile";

    protected String adminToken() {
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
                new AuthenticationRequest("admin@shopizer.com", "password"), AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getToken());
        return response.getBody().getToken();
    }

    protected HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }

    protected ResponseEntity<String> get(String path, String token) {
        return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    }

    protected void assertDenied(ResponseEntity<?> response) {
        assertTrue(response.getStatusCodeValue() == 401 || response.getStatusCodeValue() == 403,
                () -> "Expected 401/403, got " + response.getStatusCodeValue() + ": " + response.getBody());
    }

    protected String registerAndLoginCustomer() {
        String email = "contract-" + UUID.randomUUID() + "@test.com";
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");
        Address billing = new Address();
        billing.setFirstName("Contract");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);
        ResponseEntity<String> registered = testRestTemplate.postForEntity("/api/v1/customer/register", customer, String.class);
        assertEquals(HttpStatus.OK, registered.getStatusCode(), registered.getBody());
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new AuthenticationRequest(email, "clear123"), AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertNotNull(login.getBody());
        assertNotNull(login.getBody().getToken());
        return login.getBody().getToken();
    }
}
