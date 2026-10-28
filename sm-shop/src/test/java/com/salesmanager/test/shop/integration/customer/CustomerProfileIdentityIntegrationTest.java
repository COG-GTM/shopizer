package com.salesmanager.test.shop.integration.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class CustomerProfileIdentityIntegrationTest extends ServicesTestSupport {

    @Test
    public void adminCanUpdateRegisteredCustomerWithoutChangingIdentity() {
        HttpHeaders headers = getHeader();
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress("profile-" + UUID.randomUUID() + "@example.test");
        customer.setPassword("Profile-test-123");
        customer.setLanguage("en");
        Address billing = new Address();
        billing.setFirstName("Original");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);

        ResponseEntity<PersistableCustomer> created = testRestTemplate.postForEntity(
                "/api/v1/customer/register", customer, PersistableCustomer.class);
        assertEquals(OK, created.getStatusCode());
        assertNotNull(created.getBody());
        Long id = created.getBody().getId();
        assertNotNull(id);

        billing.setFirstName("Updated");
        customer.setPassword(null);
        ResponseEntity<PersistableCustomer> updated = testRestTemplate.exchange(
                "/api/v1/private/customer/" + id, HttpMethod.PUT,
                new HttpEntity<>(customer, headers), PersistableCustomer.class);
        assertEquals(OK, updated.getStatusCode());
        assertNotNull(updated.getBody());
        assertEquals(id, updated.getBody().getId());

        ResponseEntity<ReadableCustomer> reloaded = testRestTemplate.exchange(
                "/api/v1/private/customer/" + id, HttpMethod.GET,
                new HttpEntity<>(headers), ReadableCustomer.class);
        assertEquals(OK, reloaded.getStatusCode());
        assertNotNull(reloaded.getBody());
        assertEquals(id, reloaded.getBody().getId());
        assertEquals(customer.getEmailAddress(), reloaded.getBody().getEmailAddress());
        assertEquals("Updated", reloaded.getBody().getBilling().getFirstName());
    }
}
