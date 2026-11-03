package com.salesmanager.test.shop.integration.product;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.uat.UatSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductReviewAuthorizationIntegrationTest extends UatSupport {

  private static String reviewBody(Long id, Long customerId, Long productId, String description) {
    StringBuilder sb = new StringBuilder("{\"rating\":3,\"language\":\"en\",\"description\":\"");
    sb.append(description).append('"');
    if (id != null) {
      sb.append(",\"id\":").append(id);
    }
    if (customerId != null) {
      sb.append(",\"customerId\":").append(customerId);
    }
    if (productId != null) {
      sb.append(",\"productId\":").append(productId);
    }
    return sb.append('}').toString();
  }

  private ResponseEntity<String> post(String scope, Long productId, String body, HttpHeaders headers) {
    return testRestTemplate.postForEntity(
        "/api/v1/" + scope + "/products/" + productId + "/reviews?store=" + Constants.DEFAULT_STORE,
        new HttpEntity<>(body, headers),
        String.class);
  }

  private JsonNode listReviews(Long productId) {
    ResponseEntity<String> list =
        testRestTemplate.getForEntity(
            "/api/v1/product/" + productId + "/reviews?store=" + Constants.DEFAULT_STORE, String.class);
    assertThat(list.getStatusCode(), is(OK));
    return json(list.getBody());
  }

  @Test
  public void anonymousCustomerReviewIsUnauthorized() {
    ReadableProduct product = sampleProduct("rvauthanonprod");
    ResponseEntity<String> response =
        post("auth", product.getId(), reviewBody(null, 1L, null, "anon"), jsonHeaders());
    assertThat(response.getStatusCode(), is(UNAUTHORIZED));
    assertEquals(0, listReviews(product.getId()).size());
  }

  @Test
  public void customerTokenCannotUsePrivateReviewRoute() {
    ReadableProduct product = sampleProduct("rvauthcustpriv");
    String token = registerAndLogin("rvauth-custpriv@test.com");
    long me = customerProfile(token).get("id").asLong();
    ResponseEntity<String> response =
        post("private", product.getId(), reviewBody(null, me, null, "priv"), bearer(token));
    assertFalse(response.getStatusCode().is2xxSuccessful());
    assertEquals(0, listReviews(product.getId()).size());
  }

  @Test
  public void adminTokenCannotUseCustomerReviewRoute() {
    ReadableProduct product = sampleProduct("rvauthadminauth");
    String token = registerAndLogin("rvauth-adminauth@test.com");
    long customerId = customerProfile(token).get("id").asLong();
    ResponseEntity<String> response =
        post("auth", product.getId(), reviewBody(null, customerId, null, "admin"), getHeader());
    assertFalse(response.getStatusCode().is2xxSuccessful());
    assertEquals(0, listReviews(product.getId()).size());
  }

  @Test
  public void adminCanReviewOnBehalfOfCustomerForPathProduct() {
    ReadableProduct product = sampleProduct("rvauthadminprod");
    ReadableProduct other = sampleProduct("rvauthadminother");
    String token = registerAndLogin("rvauth-onbehalf@test.com");
    long customerId = customerProfile(token).get("id").asLong();

    ResponseEntity<String> created =
        post(
            "private",
            product.getId(),
            reviewBody(null, customerId, other.getId(), "on behalf"),
            getHeader());
    assertThat(created.getStatusCode(), is(CREATED));
    JsonNode review = json(created.getBody());
    assertThat(review.get("customerId").asLong(), is(customerId));
    assertThat(review.get("productId").asLong(), is(product.getId()));

    JsonNode reviews = listReviews(product.getId());
    assertEquals(1, reviews.size());
    assertThat(reviews.get(0).get("customer").get("id").asLong(), is(customerId));
    assertEquals(0, listReviews(other.getId()).size());
  }

  @Test
  public void customerCannotOverwriteOrImpersonateAnotherCustomersReview() {
    ReadableProduct product = sampleProduct("rvauthoverwrite");
    String victimToken = registerAndLogin("rvauth-victim@test.com");
    String attackerToken = registerAndLogin("rvauth-attacker@test.com");
    long victimId = customerProfile(victimToken).get("id").asLong();
    long attackerId = customerProfile(attackerToken).get("id").asLong();

    ResponseEntity<String> victim =
        post("auth", product.getId(), reviewBody(null, null, null, "victim original"), bearer(victimToken));
    assertThat(victim.getStatusCode(), is(CREATED));
    long victimReviewId = json(victim.getBody()).get("id").asLong();

    ResponseEntity<String> attack =
        post(
            "auth",
            product.getId(),
            reviewBody(victimReviewId, victimId, product.getId(), "tampered"),
            bearer(attackerToken));
    assertThat(attack.getStatusCode(), is(CREATED));
    JsonNode attackReview = json(attack.getBody());
    assertNotEquals(victimReviewId, attackReview.get("id").asLong());
    assertThat(attackReview.get("customerId").asLong(), is(attackerId));

    JsonNode reviews = listReviews(product.getId());
    assertEquals(2, reviews.size());
    Map<Long, String> byCustomer = new HashMap<>();
    for (JsonNode r : reviews) {
      byCustomer.put(r.get("customer").get("id").asLong(), r.get("description").asText());
    }
    assertThat(byCustomer.get(victimId), is("victim original"));
    assertThat(byCustomer.get(attackerId), is("tampered"));
  }

  @Test
  public void customerCannotBypassOneReviewPerProductWithBodyProductId() {
    ReadableProduct product = sampleProduct("rvauthdupprod");
    ReadableProduct decoy = sampleProduct("rvauthdupdecoy");
    String token = registerAndLogin("rvauth-dup@test.com");

    ResponseEntity<String> first =
        post("auth", product.getId(), reviewBody(null, null, null, "first"), bearer(token));
    assertThat(first.getStatusCode(), is(CREATED));

    ResponseEntity<String> second =
        post("auth", product.getId(), reviewBody(null, null, decoy.getId(), "second"), bearer(token));
    assertFalse(second.getStatusCode().is2xxSuccessful());

    assertEquals(1, listReviews(product.getId()).size());
    assertEquals(0, listReviews(decoy.getId()).size());
  }
}
