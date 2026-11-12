package com.salesmanager.test.shop.integration.upgrade.ab544;

import com.salesmanager.test.shop.common.UpgradeHttpSupport;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.SystemConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeRuntimeIntegrationTest extends UpgradeHttpSupport {
    @Autowired private EntityManagerFactory emf;

    @Test
    void categoryAndExistingCartMatchWithTrailingSlash() throws Exception {
        var category = request(HttpMethod.GET, "/api/v1/category", null, null, 200);
        assertEquals(category, request(HttpMethod.GET, "/api/v1/category/", null, null, 200));
        String sku = unique();
        long id = request(HttpMethod.POST, "/api/v1/private/product", product(sku), admin(), 201).path("id").asLong();
        try {
            var cart = request(HttpMethod.POST, "/api/v1/cart", item(sku, 1), null, 201);
            String path = "/api/v1/cart/" + cart.path("code").asText();
            var normal = request(HttpMethod.GET, path, null, null, 200);
            var slash = request(HttpMethod.GET, path + "/", null, null, 200);
            assertEquals(normal.path("code"), slash.path("code"));
            assertEquals(normal.path("quantity"), slash.path("quantity"));
            assertEquals(normal.path("products").size(), slash.path("products").size());
        } finally {
            request(HttpMethod.DELETE, "/api/v1/private/product/" + id, null, admin(), 200);
        }
    }

    @Test
    void securityChainsSeparateAdminCustomerAndBasicAuthentication() throws Exception {
        String path = "/api/v1/private/customers";
        assertTrue(request(HttpMethod.GET, path, null, admin(), 200).has("customers"));
        assertEquals(401, exchange(HttpMethod.GET, path, null, null).getStatusCode().value());
        String email = unique() + "@example.test";
        request(HttpMethod.POST, "/api/v1/customer/register", customer(email), null, 200);
        String token = login("/api/v1/customer/login", email, PASSWORD);
        denied(exchange(HttpMethod.GET, path, null, token));
        basicAuthentication();
    }

    @Test
    void springdocPublishesOpenApiAndSwaggerUi() throws Exception {
        var docs = request(HttpMethod.GET, "/v3/api-docs", null, null, 200);
        assertTrue(docs.path("openapi").asText().startsWith("3."));
        assertTrue(docs.path("paths").fieldNames().hasNext());
        assertTrue(docs.path("paths").toString().contains("/api/v1/"));
        var ui = http.getForEntity("/swagger-ui/index.html", String.class);
        assertEquals(200, ui.getStatusCode().value());
        assertTrue(ui.getBody().contains("swagger-ui"));
    }

    @Test
    void h2ReloadsLongTextAndQuotedValueAndUsesJcache() {
        assertEquals("jcache",
                emf.getProperties().get("hibernate.cache.region.factory_class"));
        var em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            String text = "Long text migration é漢字 ".repeat(700);
            MerchantConfiguration longText = new MerchantConfiguration();
            longText.setKey(unique());
            longText.setValue(text);
            SystemConfiguration quoted = new SystemConfiguration();
            quoted.setKey(unique());
            quoted.setValue("quoted VALUE");
            em.persist(longText);
            em.persist(quoted);
            em.flush();
            em.clear();
            assertEquals(text, em.find(MerchantConfiguration.class, longText.getId()).getValue());
            assertEquals("quoted VALUE", em.find(SystemConfiguration.class, quoted.getId()).getValue());
        } finally {
            em.getTransaction().rollback();
            em.close();
        }
    }

    @Test
    void actuatorHealthRemainsUp() throws Exception {
        assertEquals("UP", request(HttpMethod.GET, "/actuator/health", null, null, 200).path("status").asText());
    }
}
