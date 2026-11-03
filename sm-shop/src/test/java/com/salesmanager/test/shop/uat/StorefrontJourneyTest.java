package com.salesmanager.test.shop.uat;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneyTest extends UatSupport {

  @Test
  public void customerRegistersLogsInAndReadsProfile() {
    String token = registerAndLogin("uat-profile@test.com");
    JsonNode profile = customerProfile(token);
    assertThat(profile.get("emailAddress").asText(), is("uat-profile@test.com"));
    assertNotNull(profile.get("id"));
  }

  @Test
  public void customerApiWithoutTokenIsUnauthorized() {
    ResponseEntity<String> response =
        testRestTemplate.exchange(
            "/api/v1/auth/customer/profile",
            HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()),
            String.class);
    assertThat(response.getStatusCode(), is(UNAUTHORIZED));
  }

  @Test
  public void shopperBrowsesStoreCategoriesAndProducts() {
    ReadableProduct seeded = sampleProduct("uatbrowseprod");

    ResponseEntity<String> store =
        testRestTemplate.getForEntity("/api/v1/store/" + Constants.DEFAULT_STORE, String.class);
    assertThat(store.getStatusCode(), is(OK));
    assertThat(json(store.getBody()).get("code").asText(), is(Constants.DEFAULT_STORE));

    ResponseEntity<String> categories =
        testRestTemplate.getForEntity("/api/v1/category?store=" + Constants.DEFAULT_STORE, String.class);
    assertThat(categories.getStatusCode(), is(OK));
    assertThat(json(categories.getBody()).get("categories").size(), greaterThan(0));

    ResponseEntity<String> products =
        testRestTemplate.getForEntity("/api/v1/products?store=" + Constants.DEFAULT_STORE, String.class);
    assertThat(products.getStatusCode(), is(OK));
    assertThat(json(products.getBody()).get("products").size(), greaterThan(0));

    ResponseEntity<String> detail =
        testRestTemplate.getForEntity(
            "/api/v2/product/" + seeded.getSku() + "?store=" + Constants.DEFAULT_STORE, String.class);
    assertThat(detail.getStatusCode(), is(OK));
    assertThat(json(detail.getBody()).get("id").asLong(), is(seeded.getId()));
  }

  @Test
  public void shopperCreatesCartUpdatesItAndReadsItBack() {
    ReadableProduct product = sampleProduct("uatcartprod");

    String item = "{\"product\":\"" + product.getSku() + "\",\"quantity\":1}";
    ResponseEntity<String> created =
        testRestTemplate.postForEntity(
            "/api/v1/cart/", new HttpEntity<>(item, jsonHeaders()), String.class);
    assertThat(created.getStatusCode(), is(CREATED));
    String code = json(created.getBody()).get("code").asText();
    assertEquals(1, json(created.getBody()).get("quantity").asInt());

    String update = "{\"product\":\"" + product.getSku() + "\",\"quantity\":3}";
    ResponseEntity<String> updated =
        testRestTemplate.exchange(
            "/api/v1/cart/" + code, HttpMethod.PUT, new HttpEntity<>(update, jsonHeaders()), String.class);
    assertFalse(updated.getStatusCode().isError());

    ResponseEntity<String> read = testRestTemplate.getForEntity("/api/v1/cart/" + code, String.class);
    assertThat(read.getStatusCode(), is(OK));
    JsonNode cart = json(read.getBody());
    assertThat(cart.get("code").asText(), is(code));
    assertThat(cart.get("quantity").asInt(), greaterThanOrEqualTo(1));
  }

  @Test
  public void customerReviewsAProductAndSeesItListed() {
    ReadableProduct product = sampleProduct("uatreviewprod");
    String token = registerAndLogin("uat-reviewer@test.com");
    long customerId = customerProfile(token).get("id").asLong();

    String body = "{\"rating\":4,\"description\":\"uat great product\",\"language\":\"en\"}";
    ResponseEntity<String> created =
        testRestTemplate.postForEntity(
            "/api/v1/auth/products/" + product.getId() + "/reviews?store=" + Constants.DEFAULT_STORE,
            new HttpEntity<>(body, bearer(token)),
            String.class);
    assertThat(created.getStatusCode(), is(CREATED));
    JsonNode review = json(created.getBody());
    assertThat(review.get("customerId").asLong(), is(customerId));
    assertThat(review.get("productId").asLong(), is(product.getId()));
    assertNotNull(review.get("id"));

    ResponseEntity<String> list =
        testRestTemplate.getForEntity(
            "/api/v1/product/" + product.getId() + "/reviews?store=" + Constants.DEFAULT_STORE,
            String.class);
    assertThat(list.getStatusCode(), is(OK));
    JsonNode reviews = json(list.getBody());
    assertEquals(1, reviews.size());
    assertThat(reviews.get(0).get("description").asText(), is("uat great product"));
    assertThat(reviews.get(0).get("customer").get("id").asLong(), is(customerId));

    ResponseEntity<String> second =
        testRestTemplate.postForEntity(
            "/api/v1/auth/products/" + product.getId() + "/reviews?store=" + Constants.DEFAULT_STORE,
            new HttpEntity<>(body, bearer(token)),
            String.class);
    assertFalse(second.getStatusCode().is2xxSuccessful());
  }
}
