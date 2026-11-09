package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.model.customer.CustomerEntity;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;

import io.swagger.v3.oas.annotations.media.Schema;

class OpenApiModelContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void customerDescriptionsUseOpenApiSchemas() throws Exception {
        Schema schema = PersistableCustomer.class.getAnnotation(Schema.class);
        assertNotNull(schema);
        assertEquals("Customer", schema.name());
        assertEquals("Customer model object", schema.description());
        assertDescriptions(CustomerEntity.class, Map.of(
                "emailAddress", "Customer email address. Required for registration",
                "billing", "Customer billing address", "gender", "Customer gender M | F",
                "language", "2 letters language code en | fr | ..."));
        assertDescriptions(PersistableCustomer.class, Map.of("password", "Customer password"));
        assertDescriptions(Address.class, Map.of(
                "firstName", "Customer billing first name", "lastName", "Customer billing last name",
                "address", "Customer billing or shipping address", "city", "Customer billing or shipping city",
                "stateProvince", "Customer billing or shipping state / province (if no 2 letter codes, example: North estate)",
                "zone", "Customer billing or shipping state / province (2 letter code CA, ON...)",
                "country", "Customer billing or shipping country code (2 letter code US, CA, UK, IT, IN, CN...)"));
    }

    @Test
    void emptyDtosKeepBaseJsonPropertyNames() {
        Set<String> customer = Set.of("id", "emailAddress", "billing", "delivery", "gender", "language",
                "firstName", "lastName", "provider", "storeCode", "userName", "rating", "ratingCount");
        assertEquals(customer, fields(mapper.valueToTree(new CustomerEntity())));
        Set<String> persistable = new HashSet<>(customer);
        persistable.addAll(Set.of("password", "repeatPassword", "attributes", "groups"));
        assertEquals(persistable, fields(mapper.valueToTree(new PersistableCustomer())));
        assertEquals(Set.of("postalCode", "countryCode", "firstName", "lastName", "bilstateOther", "company",
                "phone", "address", "city", "stateProvince", "billingAddress", "latitude", "longitude", "zone", "country"),
                fields(mapper.valueToTree(new Address())));
    }

    @Test
    void populatedCustomerKeepsNestedValuesAndTypes() throws Exception {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress("buyer@example.com");
        customer.setPassword("example-password");
        Address address = new Address();
        address.setFirstName("Buyer");
        address.setCountry("CA");
        address.setBillingAddress(true);
        customer.setBilling(address);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(customer));
        assertEquals("buyer@example.com", json.path("emailAddress").textValue());
        assertEquals("example-password", json.path("password").textValue());
        assertEquals("Buyer", json.at("/billing/firstName").textValue());
        assertEquals("CA", json.at("/billing/country").textValue());
        assertTrue(json.at("/billing/billingAddress").booleanValue());
        assertTrue(json.path("ratingCount").isIntegralNumber());
        assertTrue(json.path("delivery").isNull());
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static void assertDescriptions(Class<?> type, Map<String, String> descriptions) throws Exception {
        for (Map.Entry<String, String> entry : descriptions.entrySet()) {
            Schema schema = type.getDeclaredField(entry.getKey()).getAnnotation(Schema.class);
            assertNotNull(schema, type.getSimpleName() + "." + entry.getKey());
            assertEquals(entry.getValue(), schema.description());
        }
    }
}
