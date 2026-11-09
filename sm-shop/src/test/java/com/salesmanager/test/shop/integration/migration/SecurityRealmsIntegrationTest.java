package com.salesmanager.test.shop.integration.migration;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import com.salesmanager.test.shop.common.MigrationTestSupport;

class SecurityRealmsIntegrationTest extends MigrationTestSupport {
    @Test void adminRealmRequiresValidAdminToken() {
        String path = "/api/v1/private/users";
        assertEquals(401, get(path, new HttpHeaders()).getStatusCode().value());
        ok(path, getHeader());
    }

    @Test void customerRealmAuthenticatesAndCannotEnterAdminRealm() {
        assertEquals(401, get("/api/v1/auth/customer/profile", new HttpHeaders()).getStatusCode().value());
        HttpHeaders customer = registerCustomer();
        assertTrue(ok("/api/v1/auth/customer/profile", customer).has("id"));
        int status = get("/api/v1/private/users", customer).getStatusCode().value();
        assertTrue(status == 401 || status == 403, "Customer in admin realm: " + status);
    }

    @Test void preflightDoesNotRequireJwt() {
        for (String path : new String[]{"/api/v1/private/users", "/api/v1/auth/customer/profile"}) {
            HttpHeaders headers = new HttpHeaders();
            headers.setOrigin("http://localhost:3000");
            headers.setAccessControlRequestMethod(HttpMethod.GET);
            var response = testRestTemplate.exchange(path, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
            assertNotEquals(401, response.getStatusCode().value(), path);
        }
    }

    @Test void servicesRealmRejectsMissingAndInvalidBasicCredentials() {
        String path = "/services/private/migration-probe";
        var missing = get(path, new HttpHeaders());
        assertEquals(401, missing.getStatusCode().value());
        assertEquals("Basic realm=\"rest-customer-realm\"", missing.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
        assertEquals(401, get(path, invalid).getStatusCode().value());
        assertEquals(202, get("/services/public/DEFAULT", new HttpHeaders()).getStatusCode().value());
    }
}
