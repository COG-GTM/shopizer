package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PlatformUatTest extends ServicesTestSupport {

    @Test
    public void healthAndSwaggerEndpointsAreAvailable() throws Exception {
        ResponseEntity<String> health = testRestTemplate.exchange(
                "/actuator/health", GET, HttpEntity.EMPTY, String.class);
        assertEquals(OK, health.getStatusCode());
        JsonNode healthJson = new ObjectMapper().readTree(health.getBody());
        assertEquals("UP", healthJson.path("status").asText());

        ResponseEntity<String> swagger = testRestTemplate.exchange(
                "/v2/api-docs", GET, HttpEntity.EMPTY, String.class);
        assertEquals(OK, swagger.getStatusCode());
        JsonNode swaggerJson = new ObjectMapper().readTree(swagger.getBody());
        assertEquals("2.0", swaggerJson.path("swagger").asText());
    }
}
