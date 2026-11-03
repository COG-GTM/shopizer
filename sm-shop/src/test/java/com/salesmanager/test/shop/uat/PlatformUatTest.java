package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PlatformUatTest extends ServicesTestSupport {

  @Test
  public void platformEndpointsAndServicesAuthenticationAreAvailable() {
    ResponseEntity<String> publicServicesResponse = testRestTemplate.getForEntity(
        "/services/public/DEFAULT", String.class);
    assertEquals(HttpStatus.ACCEPTED, publicServicesResponse.getStatusCode());

    HttpHeaders validBasicAuth = new HttpHeaders();
    validBasicAuth.setBasicAuth("admin@shopizer.com", "password");
    ResponseEntity<String> validServicesResponse = testRestTemplate.exchange(
        "/services/private/system/optin/uat-no-such-optin",
        HttpMethod.DELETE,
        new HttpEntity<>(validBasicAuth),
        String.class);
    assertTrue(validServicesResponse.getStatusCode() != HttpStatus.UNAUTHORIZED);

    HttpHeaders invalidBasicAuth = new HttpHeaders();
    invalidBasicAuth.setBasicAuth("admin@shopizer.com", "not-the-password");
    ResponseEntity<String> invalidServicesResponse = testRestTemplate.exchange(
        "/services/private/system/optin/uat-no-such-optin",
        HttpMethod.DELETE,
        new HttpEntity<>(invalidBasicAuth),
        String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, invalidServicesResponse.getStatusCode());

    ResponseEntity<String> healthResponse = testRestTemplate.getForEntity(
        "/actuator/health", String.class);
    assertEquals(HttpStatus.OK, healthResponse.getStatusCode());
    assertTrue(healthResponse.getBody().contains("\"status\":\"UP\""));

    ResponseEntity<String> apiDocsResponse = testRestTemplate.getForEntity(
        "/v2/api-docs", String.class);
    assertEquals(HttpStatus.OK, apiDocsResponse.getStatusCode());
  }
}
