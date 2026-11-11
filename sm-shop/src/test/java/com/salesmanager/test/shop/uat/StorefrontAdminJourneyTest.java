package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@ExtendWith(SpringExtension.class)
public class StorefrontAdminJourneyTest extends ServicesTestSupport {
    private final ObjectMapper json = new ObjectMapper();

    private ResponseEntity<String> request(HttpMethod method, String url, String body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return testRestTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode expect(ResponseEntity<String> response, int status) throws Exception {
        assertEquals(status, response.getStatusCodeValue(), response.getBody());
        assertNotNull(response.getBody());
        return json.readTree(response.getBody());
    }

    private HttpHeaders login(String endpoint, String username, String password) throws Exception {
        JsonNode response = expect(request(HttpMethod.POST, endpoint,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", new HttpHeaders()), 200);
        assertFalse(response.path("token").asText().isEmpty());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.path("token").asText());
        return headers;
    }

    private HttpHeaders customer() throws Exception {
        String email = "journey-" + UUID.randomUUID() + "@example.com";
        expect(request(HttpMethod.POST, "/api/v1/customer/register",
                "{\"emailAddress\":\"" + email + "\",\"password\":\"clear123\",\"gender\":\"M\","
                + "\"language\":\"en\",\"storeCode\":\"DEFAULT\",\"billing\":{\"firstName\":\"Journey\","
                + "\"lastName\":\"Customer\",\"country\":\"CA\",\"zone\":\"QC\",\"city\":\"Montreal\","
                + "\"address\":\"123 Test Street\",\"postalCode\":\"H2H2H2\"}}", new HttpHeaders()), 200);
        return login("/api/v1/customer/login", email, "clear123");
    }

    @Test
    public void adminLoginProtectsPrivateProfile() throws Exception {
        HttpHeaders headers = login("/api/v1/private/login", "admin@shopizer.com", "password");
        expect(request(HttpMethod.GET, "/api/v1/private/user/profile", null, headers), 200);
        ResponseEntity<String> anonymous = request(HttpMethod.GET, "/api/v1/private/user/profile", null, new HttpHeaders());
        assertTrue(anonymous.getStatusCodeValue() == 401 || anonymous.getStatusCodeValue() == 403, anonymous.toString());
    }

    @Test
    public void registeredCustomerCanReadProfile() throws Exception {
        JsonNode profile = expect(request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, customer()), 200);
        assertTrue(profile.path("emailAddress").asText().startsWith("journey-"));
    }

    @Test
    public void browseAndUpdateCart() throws Exception {
        String sku = "journey-" + UUID.randomUUID();
        sampleProduct(sku);
        expect(request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, new HttpHeaders()), 200);
        assertTrue(expect(request(HttpMethod.GET, "/api/v1/category/", null, new HttpHeaders()), 200).path("categories").isArray());
        JsonNode products = expect(request(HttpMethod.GET, "/api/v1/products", null, new HttpHeaders()), 200);
        assertTrue(products.path("products").size() > 0);
        JsonNode product = expect(request(HttpMethod.GET, "/api/v2/product/" + sku, null, new HttpHeaders()), 200);
        assertEquals(sku, product.path("sku").asText());
        String body = "{\"product\":\"" + sku + "\",\"quantity\":1}";
        JsonNode cart = expect(request(HttpMethod.POST, "/api/v1/cart", body, new HttpHeaders()), 201);
        assertEquals(1, cart.path("quantity").asInt());
        String code = cart.path("code").asText();
        assertFalse(code.isEmpty());
        JsonNode updated = expect(request(HttpMethod.PUT, "/api/v1/cart/" + code,
                "{\"product\":\"" + sku + "\",\"quantity\":2}", new HttpHeaders()), 201);
        assertEquals(2, updated.path("quantity").asInt());
        assertEquals(2, expect(request(HttpMethod.GET, "/api/v1/cart/" + code, null, new HttpHeaders()), 200).path("quantity").asInt());
    }

    @Test
    public void servicesBasicAuthentication() throws Exception {
        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        expect(request(HttpMethod.POST, "/services/private/system/optin", "{}", valid), 200);
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
        assertEquals(401, request(HttpMethod.POST, "/services/private/system/optin", "{}", invalid).getStatusCodeValue());
    }

    @Test
    public void publicOperationalEndpoints() throws Exception {
        assertEquals("UP", expect(request(HttpMethod.GET, "/actuator/health", null, new HttpHeaders()), 200).path("status").asText());
        assertEquals("2.0", expect(request(HttpMethod.GET, "/v2/api-docs", null, new HttpHeaders()), 200).path("swagger").asText());
    }

}
