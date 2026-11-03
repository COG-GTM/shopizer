package com.salesmanager.test.shop.integration.product;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Arrays;
import javax.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.test.shop.common.ApiJourneySupport;

public class ProductAttributeStoreScopeIntegrationTest extends ApiJourneySupport {
    @Inject private JdbcTemplate jdbcTemplate;
    private HttpHeaders superadmin;
    private HttpHeaders admin;
    private String storeB;
    private long ownProduct, foreignProduct, option, value, foreignAttribute;

    @BeforeEach
    void fixtures() {
        superadmin = getHeader();
        storeB = unique("scope");
        ObjectNode store = object("{\"currency\":\"CAD\",\"defaultLanguage\":\"en\",\"supportedLanguages\":[\"en\"],\"email\":\"scope@test.com\",\"phone\":\"4445556666\",\"address\":{\"address\":\"12 Main St\",\"postalCode\":\"12345\",\"country\":\"US\",\"city\":\"Boston\",\"stateProvince\":\"MA\"}}");
        store.put("code", storeB).put("name", storeB);
        expect(200, HttpMethod.POST, "/api/v1/private/store/", store, superadmin);
        ownProduct = createProduct("DEFAULT", unique("own"), superadmin);
        foreignProduct = createProduct(storeB, unique("foreign"), superadmin);
        option = createOption("DEFAULT", unique("color"), superadmin);
        value = createValue("DEFAULT", unique("red"), superadmin);
        long foreignOption = createOption(storeB, unique("color"), superadmin);
        long foreignValue = createValue(storeB, unique("red"), superadmin);
        foreignAttribute = expect(201, HttpMethod.POST, attributePath(foreignProduct, storeB),
                attribute(foreignProduct, foreignOption, foreignValue, 3), superadmin).path("id").asLong();
        String username = unique("scopeadmin") + "@test.com";
        ObjectNode user = object("{\"defaultLanguage\":\"en\",\"firstName\":\"Scope\",\"lastName\":\"Admin\",\"password\":\"Password1\",\"repeatPassword\":\"Password1\",\"groups\":[{\"name\":\"ADMIN\"}]}");
        user.put("userName", username).put("emailAddress", username);
        JsonNode created = expect(200, HttpMethod.POST, "/api/v1/private/user/?store=DEFAULT", user, superadmin);
        assertTrue(created.path("groups").toString().contains("ADMIN"));
        assertFalse(created.path("groups").toString().contains("SUPERADMIN"));
        admin = getHeader(username, "Password1");
    }

    @Test
    void sameStoreCreateReadAndUpdate() {
        long value = createValue("DEFAULT", unique("single"), superadmin);
        long id = expect(201, HttpMethod.POST, attributePath(ownProduct, "DEFAULT"),
                attribute(ownProduct, option, value, 2), admin).path("id").asLong();
        String path = attributePath(ownProduct, id, "DEFAULT");
        JsonNode read = expect(200, HttpMethod.GET, path, null, admin);
        assertEquals(option, read.path("option").path("id").asLong());
        assertEquals(value, read.path("optionValue").path("id").asLong());
        expect(200, HttpMethod.PUT, path, attribute(ownProduct, option, value, 7), admin);
        assertPrice(7, expect(200, HttpMethod.GET, path, null, admin));
    }

    @Test
    void rejectsForeignProductSingleCreateWithoutMutation() {
        JsonNode before = foreignAttributes();
        int storedBefore = storedForeignAttributeCount();
        ResponseEntity<String> response = request(HttpMethod.POST, attributePath(foreignProduct, "DEFAULT"),
                attribute(foreignProduct, option, value, 8), admin);
        assertAll(() -> assertEquals(404, response.getStatusCodeValue(), response.toString()),
                () -> assertEquals(before, foreignAttributes()),
                () -> assertEquals(storedBefore, storedForeignAttributeCount()));
    }

    @Test
    void rejectsForeignProductBulkCreateWithoutMutation() {
        JsonNode before = foreignAttributes();
        int storedBefore = storedForeignAttributeCount();
        long first = createValue("DEFAULT", unique("bulk"), superadmin);
        long second = createValue("DEFAULT", unique("bulk"), superadmin);
        ResponseEntity<String> response = request(HttpMethod.POST, attributes(foreignProduct, "DEFAULT"),
                Arrays.asList(attribute(foreignProduct, option, first, 8), attribute(foreignProduct, option, second, 9)), admin);
        assertAll(() -> assertEquals(404, response.getStatusCodeValue(), response.toString()),
                () -> assertEquals(before, foreignAttributes()),
                () -> assertEquals(storedBefore, storedForeignAttributeCount()));
    }

    @Test
    void rejectsForeignProductUpdateWithoutMutation() {
        JsonNode before = foreignAttributes();
        ResponseEntity<String> response = request(HttpMethod.PUT, attributePath(foreignProduct, foreignAttribute, "DEFAULT"),
                attribute(foreignProduct, option, value, 99), admin);
        assertAll(() -> assertEquals(404, response.getStatusCodeValue(), response.toString()),
                () -> assertEquals(before, foreignAttributes()),
                () -> assertPrice(3, expect(200, HttpMethod.GET, attributePath(foreignProduct, foreignAttribute, storeB), null, superadmin)));
    }

    @Test
    void rejectsForeignAttributeOnOwnProductWithoutMutation() {
        JsonNode before = foreignAttributes();
        ResponseEntity<String> response = request(HttpMethod.PUT, attributePath(ownProduct, foreignAttribute, "DEFAULT"),
                attribute(ownProduct, option, value, 99), admin);
        assertAll(() -> assertEquals(404, response.getStatusCodeValue(), response.toString()),
                () -> assertEquals(before, foreignAttributes()));
    }

    @Test
    void ignoresForeignBodyProductIdAndAttachesOnlyToUrlProduct() {
        JsonNode before = foreignAttributes();
        long id = expect(201, HttpMethod.POST, attributePath(ownProduct, "DEFAULT"),
                attribute(foreignProduct, option, value, 6), admin).path("id").asLong();
        assertPrice(6, expect(200, HttpMethod.GET, attributePath(ownProduct, id, "DEFAULT"), null, admin));
        assertEquals(before, foreignAttributes());
    }

    private int storedForeignAttributeCount() {
        return jdbcTemplate.queryForObject("select count(*) from SALESMANAGER.PRODUCT_ATTRIBUTE where PRODUCT_ID = ?", Integer.class, foreignProduct);
    }

    private JsonNode foreignAttributes() {
        return expect(200, HttpMethod.GET, attributes(foreignProduct, storeB), null, superadmin).path("attributes");
    }

    private void assertPrice(int expected, JsonNode attribute) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(new BigDecimal(attribute.path("productAttributeUnformattedPrice").asText())));
    }
}
