package com.salesmanager.shop.model.customer;

import static org.junit.Assert.*;

import org.junit.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.model.customer.address.Address;
import io.swagger.v3.oas.annotations.media.Schema;

public class CustomerSchemaContractTest {
    @Test
    public void customerSerializationPreservesWireFieldNames() throws Exception {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress("customer@example.com");
        customer.setGender("F");
        customer.setLanguage("en");
        customer.setPassword("test-password");
        customer.setRepeatPassword("test-password");
        customer.setUserName("customer@example.com");
        customer.setStoreCode("DEFAULT");
        Address billing = new Address();
        billing.setFirstName("Ada");
        billing.setLastName("Lovelace");
        billing.setAddress("123 Main Street");
        billing.setCity("London");
        billing.setStateProvince("London");
        billing.setCountry("GB");
        customer.setBilling(billing);
        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(customer));
        assertEquals("customer@example.com", json.path("emailAddress").asText());
        assertEquals("F", json.path("gender").asText());
        assertEquals("en", json.path("language").asText());
        assertEquals("test-password", json.path("password").asText());
        assertEquals("test-password", json.path("repeatPassword").asText());
        assertEquals("customer@example.com", json.path("userName").asText());
        assertEquals("DEFAULT", json.path("storeCode").asText());
        JsonNode address = json.path("billing");
        assertEquals("Ada", address.path("firstName").asText());
        assertEquals("Lovelace", address.path("lastName").asText());
        assertEquals("123 Main Street", address.path("address").asText());
        assertEquals("London", address.path("city").asText());
        assertEquals("London", address.path("stateProvince").asText());
        assertEquals("GB", address.path("country").asText());
    }

    @Test
    public void customerSchemaNameIsPreserved() {
        Schema schema = PersistableCustomer.class.getAnnotation(Schema.class);
        assertNotNull(schema);
        assertEquals("Customer", schema.name());
    }
}
