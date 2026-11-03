package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AdminJourneyUatTest extends ServicesTestSupport {

  @Test
  public void adminLoginAuthorizesPrivateCustomerListAndRejectsInvalidTokens() {
    ResponseEntity<AuthenticationResponse> loginResponse = testRestTemplate.postForEntity(
        "/api/v1/private/login",
        new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
        AuthenticationResponse.class);

    assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
    assertTrue(loginResponse.getBody().getToken() != null && !loginResponse.getBody().getToken().isEmpty());

    ResponseEntity<String> authorizedResponse = testRestTemplate.exchange(
        "/api/v1/private/customers",
        HttpMethod.GET,
        new HttpEntity<>(getHeader()),
        String.class);
    assertEquals(HttpStatus.OK, authorizedResponse.getStatusCode());

    ResponseEntity<String> anonymousResponse = testRestTemplate.getForEntity(
        "/api/v1/private/customers", String.class);
    assertUnauthorizedOrForbidden(anonymousResponse.getStatusCode());

    HttpHeaders invalidHeaders = new HttpHeaders();
    invalidHeaders.setBearerAuth("not-a-valid-token");
    ResponseEntity<String> invalidTokenResponse = testRestTemplate.exchange(
        "/api/v1/private/customers",
        HttpMethod.GET,
        new HttpEntity<>(invalidHeaders),
        String.class);
    assertFalse(invalidTokenResponse.getStatusCode().is2xxSuccessful());
  }

  private static void assertUnauthorizedOrForbidden(HttpStatus status) {
    assertTrue(status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
  }
}
