package com.salesmanager.test.shop.uat;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminAndOpsJourneyTest extends UatSupport {

  @Test
  public void adminLoginGivesJwtThatOpensPrivateApi() {
    ResponseEntity<AuthenticationResponse> login =
        testRestTemplate.postForEntity(
            "/api/v1/private/login",
            new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
            AuthenticationResponse.class);
    assertThat(login.getStatusCode(), is(OK));
    assertNotNull(login.getBody().getToken());

    ResponseEntity<String> customers =
        testRestTemplate.exchange(
            "/api/v1/private/customers",
            HttpMethod.GET,
            new HttpEntity<>(bearer(login.getBody().getToken())),
            String.class);
    assertThat(customers.getStatusCode(), is(OK));
    assertTrue(json(customers.getBody()).has("customers"));
  }

  @Test
  public void adminLoginWithBadPasswordIsRejected() {
    ResponseEntity<String> login =
        testRestTemplate.postForEntity(
            "/api/v1/private/login",
            new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong")),
            String.class);
    assertFalse(login.getStatusCode().is2xxSuccessful());
  }

  @Test
  public void privateApiWithoutTokenIsUnauthorized() {
    ResponseEntity<String> response =
        testRestTemplate.exchange(
            "/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
    assertThat(response.getStatusCode(), is(UNAUTHORIZED));
  }

  @Test
  public void privateApiWithBadTokenIsRejected() {
    ResponseEntity<String> response =
        testRestTemplate.exchange(
            "/api/v1/private/customers",
            HttpMethod.GET,
            new HttpEntity<>(bearer("not.a.jwt")),
            String.class);
    assertFalse(response.getStatusCode().is2xxSuccessful());
  }

  @Test
  public void privateApiWithCustomerTokenIsRejected() {
    String token = registerAndLogin("uat-admin-probe@test.com");
    ResponseEntity<String> response =
        testRestTemplate.exchange(
            "/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    assertFalse(response.getStatusCode().is2xxSuccessful());
  }

  @Test
  public void servicesPrivateRejectsInvalidBasicAuth() {
    HttpHeaders headers = jsonHeaders();
    headers.setBasicAuth("admin@shopizer.com", "wrong");
    ResponseEntity<String> response =
        testRestTemplate.exchange(
            "/services/private/system/optin",
            HttpMethod.POST,
            new HttpEntity<>("{}", headers),
            String.class);
    assertThat(response.getStatusCode(), is(UNAUTHORIZED));
  }

  @Test
  public void servicesPrivateAcceptsValidBasicAuth() {
    HttpHeaders headers = jsonHeaders();
    headers.setBasicAuth("admin@shopizer.com", "password");
    ResponseEntity<String> response =
        testRestTemplate.exchange(
            "/services/private/system/optin",
            HttpMethod.POST,
            new HttpEntity<>("{}", headers),
            String.class);
    assertFalse(response.getStatusCode() == UNAUTHORIZED);
    assertFalse(response.getStatusCode().value() == 403);
  }

  @Test
  public void servicesPublicStoreIsReachableWithoutAuth() {
    ResponseEntity<String> response =
        testRestTemplate.getForEntity("/services/public/DEFAULT", String.class);
    assertTrue(response.getStatusCode().is2xxSuccessful());
  }

  @Test
  public void actuatorHealthIsUp() {
    ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health", String.class);
    assertThat(response.getStatusCode(), is(OK));
    assertThat(json(response.getBody()).get("status").asText(), is("UP"));
  }

  @Test
  public void apiDocsListReviewEndpoints() {
    ResponseEntity<String> response = testRestTemplate.getForEntity("/v2/api-docs", String.class);
    assertThat(response.getStatusCode(), is(OK));
    JsonNode paths = json(response.getBody()).get("paths");
    assertNotNull(paths);
    assertThat(paths.size(), greaterThan(0));
    JsonNode authReview = paths.get("/api/v1/auth/products/{id}/reviews");
    JsonNode privateReview = paths.get("/api/v1/private/products/{id}/reviews");
    assertNotNull("auth review route documented", authReview);
    assertNotNull("private review route documented", privateReview);
    assertNotNull(authReview.get("post"));
    assertNotNull(privateReview.get("post"));
    assertThat(paths.toString(), containsString("/api/v1/product/{id}/reviews"));
  }
}
