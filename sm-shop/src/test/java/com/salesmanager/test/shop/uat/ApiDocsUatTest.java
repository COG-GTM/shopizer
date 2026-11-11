package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ApiDocsUatTest extends ServicesTestSupport {
    @Test
    public void publicApiDocumentationUsesOpenApiThree() throws Exception {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(new ObjectMapper().readTree(response.getBody()).path("openapi").asText().startsWith("3."));
    }
}
