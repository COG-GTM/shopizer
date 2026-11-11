package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import com.salesmanager.shop.application.ShopApplication;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class OpenApiSecurityAndContractIntegrationTest extends ServicesTestSupport {
    @Test
    public void documentationEndpointsArePublicEvenWithBogusBearerToken() {
        for (String url : Arrays.asList("/v3/api-docs", "/v3/api-docs/default",
                "/v3/api-docs/swagger-config", "/swagger-ui/index.html")) {
            for (boolean bogusToken : new boolean[] {false, true}) {
                HttpHeaders headers = new HttpHeaders();
                if (bogusToken) headers.setBearerAuth("garbage");
                ResponseEntity<String> response = testRestTemplate.exchange(url, HttpMethod.GET,
                        new HttpEntity<>(headers), String.class);
                assertEquals(url + " bogusToken=" + bogusToken, HttpStatus.OK, response.getStatusCode());
            }
        }
    }

    @Test
    public void legacyUiRedirectsWithoutFollowingRedirect() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(testRestTemplate.getRootUri()
                + "/swagger-ui.html").openConnection();
        connection.setInstanceFollowRedirects(false);
        try {
            assertTrue(connection.getResponseCode() >= 300 && connection.getResponseCode() < 400);
            assertEquals("/swagger-ui/index.html", connection.getHeaderField("Location"));
        } finally {
            connection.disconnect();
        }
    }

    @Test
    public void swaggerTwoEndpointIsRemoved() {
        assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
    }

    @Test
    public void privateAndCustomerAuthBoundariesRemainProtected() {
        assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.getForEntity(
                "/api/v1/private/user/profile", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/user/profile", HttpMethod.GET,
                new HttpEntity<>(getHeader()), String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.getForEntity(
                "/api/v1/auth/customer/profile", String.class).getStatusCode());
    }

    private JsonNode docs() throws Exception {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/v3/api-docs/default", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return new ObjectMapper().readTree(response.getBody());
    }

    @Test
    public void cartContractDocumentsQueryDefaultsAndPlainControllerPut() throws Exception {
        JsonNode paths = docs().path("paths");
        JsonNode post = paths.path("/api/v1/cart").path("post");
        assertFalse(post.isMissingNode());
        boolean store = false;
        boolean lang = false;
        for (JsonNode parameter : post.path("parameters")) {
            String name = parameter.path("name").asText();
            assertFalse(name, name.equals("merchantStore") || name.equals("language"));
            if (name.equals("store") || name.equals("lang")) {
                assertEquals("query", parameter.path("in").asText());
                assertEquals(name.equals("store") ? "DEFAULT" : "en", parameter.path("schema").path("default").asText());
                if (name.equals("store")) store = true;
                else lang = true;
            }
        }
        assertTrue("store parameter", store);
        assertTrue("lang parameter", lang);
        assertTrue(paths.path("/api/v1/cart/{code}").has("put"));
    }

    @Test
    public void allOperationsHaveSummariesAndGetsHaveGlobalResponses() throws Exception {
        JsonNode paths = docs().path("paths");
        assertTrue(paths.size() > 0);
        Iterator<Map.Entry<String, JsonNode>> entries = paths.fields();
        int operations = 0;
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> path = entries.next();
            for (String method : Arrays.asList("get", "put", "post", "delete", "options", "head", "patch", "trace")) {
                if (!path.getValue().has(method)) continue;
                operations++;
                JsonNode operation = path.getValue().path(method);
                assertFalse(method + " " + path.getKey(), operation.path("summary").asText().trim().isEmpty());
                if (method.equals("get")) {
                    for (String status : Arrays.asList("401", "403", "500")) {
                        assertTrue(path.getKey() + " response " + status, operation.path("responses").has(status));
                    }
                }
            }
        }
        assertTrue(operations > 0);
    }

    @Test
    public void customerSchemaIncludesPasswordAndJwtSchemeExists() throws Exception {
        JsonNode components = docs().path("components");
        assertTrue(components.path("securitySchemes").has("JWT"));
        JsonNode customer = components.path("schemas").path("Customer");
        assertFalse(customer.isMissingNode());
        assertTrue("Customer schema: " + customer, customer.path("properties").has("password"));
    }
}
