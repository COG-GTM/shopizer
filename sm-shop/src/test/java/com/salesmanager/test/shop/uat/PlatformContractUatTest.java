package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class PlatformContractUatTest extends ServicesTestSupport {

  @Test
  public void servicesResourcesUseBasicAuthAndExposePublicStore() {
    String endpoint = "/services/private/system/optin";
    ResponseEntity<String> noCredentials = testRestTemplate.exchange(
        endpoint, HttpMethod.POST, new HttpEntity<>("{}"), String.class);
    assertEquals(HttpStatus.FOUND, noCredentials.getStatusCode());

    HttpHeaders invalidCredentials = new HttpHeaders();
    invalidCredentials.setBasicAuth("uat-invalid", "bad-password");
    ResponseEntity<String> badCredentials = testRestTemplate.exchange(
        endpoint, HttpMethod.POST, new HttpEntity<>("{}", invalidCredentials), String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, badCredentials.getStatusCode());

    HttpHeaders validCredentials = new HttpHeaders();
    validCredentials.setBasicAuth("admin@shopizer.com", "password");
    ResponseEntity<String> valid = testRestTemplate.exchange(
        endpoint, HttpMethod.POST, new HttpEntity<>("{}", validCredentials), String.class);
    assertEquals(HttpStatus.OK, valid.getStatusCode());

    assertEquals(HttpStatus.ACCEPTED, testRestTemplate.getForEntity(
        "/services/public/DEFAULT", String.class).getStatusCode());
  }

  @Test
  public void actuatorAndApiDocsAreAvailable() throws Exception {
    ResponseEntity<JsonNode> health = testRestTemplate.getForEntity(
        "/actuator/health", JsonNode.class);
    assertEquals(HttpStatus.OK, health.getStatusCode());
    assertEquals("UP", health.getBody().path("status").asText());

    ResponseEntity<JsonNode> apiDocs = testRestTemplate.getForEntity(
        "/v2/api-docs", JsonNode.class);
    assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
    assertNotNull(apiDocs.getBody().get("paths"));
  }
}
