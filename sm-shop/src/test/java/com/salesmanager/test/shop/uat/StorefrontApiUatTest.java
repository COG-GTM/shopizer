package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.test.shop.common.ApiJourneySupport;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StorefrontApiUatTest extends ApiJourneySupport {
    private HttpHeaders admin;
    private HttpHeaders customer;
    private String sku;
    private long product;
    @Value("${jwt.secret}")
    private String tokenSecret;

    @BeforeAll
    void fixtures() {
        admin = getHeader();
        sku = unique("uat");
        product = createProduct("DEFAULT", sku, admin);
        String email = unique("customer") + "@test.com";
        ObjectNode registration = object("{\"password\":\"clear123\",\"gender\":\"M\",\"language\":\"en\",\"storeCode\":\"DEFAULT\",\"billing\":{\"firstName\":\"Journey\",\"lastName\":\"Customer\",\"country\":\"CA\",\"zone\":\"QC\",\"address\":\"12 Main Street\",\"city\":\"Montreal\",\"postalCode\":\"H2H2H2\"}}");
        registration.put("emailAddress", email);
        expect(200, HttpMethod.POST, "/api/v1/customer/register", registration, admin);
        ObjectNode login = json.createObjectNode().put("username", email).put("password", "clear123");
        JsonNode response = expect(200, HttpMethod.POST, "/api/v1/customer/login", login, new HttpHeaders());
        String token = response.path("token").asText();
        assertEquals(3, token.split("\\.").length);
        customer = new HttpHeaders();
        customer.setBearerAuth(token);
    }

    @Test
    void adminJwtProtectsPrivateApi() {
        assertEquals(3, admin.getFirst("Authorization").substring(7).split("\\.").length);
        expect(200, HttpMethod.GET, "/api/v1/private/users/1", null, admin);
        expect(401, HttpMethod.GET, "/api/v1/private/users/1", null, new HttpHeaders());
        HttpHeaders bad = new HttpHeaders();
        bad.setBearerAuth(Jwts.builder().setSubject("admin@shopizer.com")
                .setExpiration(new Date(0)).signWith(SignatureAlgorithm.HS512, tokenSecret).compact());
        expect(401, HttpMethod.GET, "/api/v1/private/users/1", null, bad);
    }

    @Test
    void registeredCustomerUsesJwtForProfile() {
        JsonNode profile = expect(200, HttpMethod.GET, "/api/v1/auth/customer/profile", null, customer);
        assertTrue(profile.toString().contains("Journey"));
    }

    @Test
    void browseStoreCategoriesProductsAndDetail() {
        assertEquals("DEFAULT", expect(200, HttpMethod.GET, "/api/v1/store/DEFAULT", null, new HttpHeaders()).path("code").asText());
        expect(200, HttpMethod.GET, "/api/v1/category?store=DEFAULT", null, new HttpHeaders());
        JsonNode products = expect(200, HttpMethod.GET, "/api/v1/products?store=DEFAULT&count=100", null, new HttpHeaders());
        assertTrue(products.path("products").size() > 0);
        assertEquals(product, expect(200, HttpMethod.GET, "/api/v2/product/" + sku, null, new HttpHeaders()).path("id").asLong());
    }

    @Test
    void createAddUpdateAndReadCart() {
        String cart = createCart();
        String secondSku = unique("cartitem");
        createProduct("DEFAULT", secondSku, admin);
        ObjectNode second = json.createObjectNode().put("product", secondSku).put("quantity", 1);
        JsonNode added = expect(201, HttpMethod.PUT, "/api/v1/cart/" + cart, second, customer);
        assertEquals(2, added.path("quantity").asInt());
        second.put("quantity", 3);
        JsonNode updated = expect(201, HttpMethod.POST, "/api/v1/cart/" + cart + "/multi", Collections.singletonList(second), customer);
        assertEquals(4, updated.path("quantity").asInt());
        JsonNode read = expect(200, HttpMethod.GET, "/api/v1/cart/" + cart, null, customer);
        assertEquals(4, read.path("quantity").asInt());
        assertEquals(cart, read.path("code").asText());
    }

    @Test
    void authenticatedCustomerChecksOutAndReadsOrder() {
        expect(200, HttpMethod.POST, "/api/v1/private/modules/payment", object("{\"code\":\"moneyorder\",\"active\":true,\"defaultSelected\":true,\"integrationKeys\":{\"transaction\":\"AUTHORIZECAPTURE\",\"address\":\"12 Main Street, Montreal, QC\"}}"), admin);
        String cart = createCart();
        JsonNode read = expect(200, HttpMethod.GET, "/api/v1/cart/" + cart, null, customer);
        ObjectNode order = object("{\"currency\":\"CAD\",\"customerAgreement\":true,\"payment\":{\"paymentModule\":\"moneyorder\",\"paymentType\":\"MONEYORDER\",\"transactionType\":\"AUTHORIZECAPTURE\"}}");
        ((ObjectNode) order.path("payment")).put("amount", read.path("total").asText());
        JsonNode confirmation = expect(200, HttpMethod.POST, "/api/v1/auth/cart/" + cart + "/checkout", order, customer);
        long id = confirmation.path("id").asLong();
        assertTrue(id > 0);
        JsonNode saved = expect(200, HttpMethod.GET, "/api/v1/auth/orders/" + id, null, customer);
        assertEquals(id, saved.path("id").asLong());
    }

    @Test
    void legacyServicesRequireValidBasicCredentials() {
        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        expect(200, HttpMethod.POST, "/services/private/system/optin", object("{}"), valid);
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
        expect(401, HttpMethod.POST, "/services/private/system/optin", object("{}"), invalid);
    }

    @Test
    void healthAndSwaggerAreAvailable() {
        assertEquals("UP", expect(200, HttpMethod.GET, "/actuator/health", null, new HttpHeaders()).path("status").asText());
        assertEquals("2.0", expect(200, HttpMethod.GET, "/v2/api-docs", null, new HttpHeaders()).path("swagger").asText());
    }

    @Test
    void storefrontShowsAdminCreatedOption() {
        String optionCode = unique("uatcolor");
        String valueCode = unique("uatred");
        long option = createOption("DEFAULT", optionCode, admin);
        long value = createValue("DEFAULT", valueCode, admin);
        long id = expect(201, HttpMethod.POST, attributePath(product, "DEFAULT"), attribute(product, option, value, 0), admin).path("id").asLong();
        JsonNode detail = expect(200, HttpMethod.GET, "/api/v2/product/" + sku, null, new HttpHeaders());
        assertTrue(detail.path("options").size() > 0, detail.toString());
        assertTrue(detail.path("options").toString().contains(optionCode), detail.toString());
        assertTrue(detail.path("options").toString().contains(valueCode), detail.toString());
        assertTrue(id > 0);
    }

    private String createCart() {
        ObjectNode item = json.createObjectNode().put("product", sku).put("quantity", 1);
        JsonNode cart = expect(201, HttpMethod.POST, "/api/v1/cart", item, customer);
        assertEquals(1, cart.path("quantity").asInt());
        assertFalse(cart.path("code").asText().isEmpty());
        return cart.path("code").asText();
    }
}
