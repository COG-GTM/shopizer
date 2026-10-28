package com.salesmanager.test.shop.integration.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import java.util.Arrays;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class ProductReviewApiIntegrationTest extends ServicesTestSupport {

  private static final String DEFAULT_STORE = "DEFAULT";

  @Value("${jwt.secret}")
  private String jwtSecret;

  private static final class CustomerSession {
    private final String token;
    private final Long id;

    private CustomerSession(String token, Long id) {
      this.token = token;
      this.id = id;
    }
  }

  private String unique(String prefix) {
    return prefix + System.nanoTime();
  }

  private CustomerSession registerAndLogin(String email) {
    String password = "clear123";
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword(password);
    customer.setGender("M");
    customer.setLanguage("en");
    customer.setStoreCode(DEFAULT_STORE);
    Address billing = new Address();
    billing.setFirstName("review");
    billing.setLastName("customer");
    billing.setCountry("CA");
    customer.setBilling(billing);

    ResponseEntity<PersistableCustomer> registration =
        testRestTemplate.postForEntity(
            "/api/v1/customer/register",
            new HttpEntity<>(customer, getHeader()),
            PersistableCustomer.class);
    assertEquals(HttpStatus.OK, registration.getStatusCode());

    ResponseEntity<AuthenticationResponse> login =
        testRestTemplate.postForEntity(
            "/api/v1/customer/login",
            new HttpEntity<>(new AuthenticationRequest(email, password)),
            AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody());
    assertNotNull(login.getBody().getToken());

    HttpHeaders headers = headers(login.getBody().getToken());
    ResponseEntity<ReadableCustomer> profile =
        testRestTemplate.exchange(
            "/api/v1/auth/customer/profile",
            GET,
            new HttpEntity<>(headers),
            ReadableCustomer.class);
    assertEquals(HttpStatus.OK, profile.getStatusCode());
    assertNotNull(profile.getBody());
    return new CustomerSession(login.getBody().getToken(), profile.getBody().getId());
  }

  private HttpHeaders headers(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return headers;
  }

  private String expiredToken() {
    return Jwts.builder()
        .setSubject("expired")
        .setExpiration(new Date(0))
        .signWith(SignatureAlgorithm.HS512, jwtSecret)
        .compact();
  }

  private ReadableProduct sampleProduct() {
    ReadableProduct product = sampleProduct(unique("review"));
    assertNotNull(product);
    assertNotNull(product.getId());
    return product;
  }

  private PersistableProductReview review(Long customerId, Long productId, Double rating) {
    PersistableProductReview review = new PersistableProductReview();
    review.setCustomerId(customerId);
    review.setProductId(productId);
    review.setRating(rating);
    review.setDescription("review-" + System.nanoTime());
    return review;
  }

  private ResponseEntity<PersistableProductReview> postAuthenticatedReview(
      Long productId, String storeCode, String token, PersistableProductReview review) {
    return testRestTemplate.exchange(
        "/api/v1/auth/products/" + productId + "/reviews?store=" + storeCode,
        POST,
        new HttpEntity<>(review, headers(token)),
        PersistableProductReview.class);
  }

  private ReadableProductReview[] reviews(Long productId) {
    ResponseEntity<ReadableProductReview[]> response =
        testRestTemplate.exchange(
            "/api/v1/product/" + productId + "/reviews?store=" + DEFAULT_STORE,
            GET,
            new HttpEntity<>(headers(null)),
            ReadableProductReview[].class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  @Test
  public void authReviewIgnoresBodyCustomerId() {
    CustomerSession caller = registerAndLogin(unique("caller") + "@test.com");
    CustomerSession victim = registerAndLogin(unique("victim") + "@test.com");
    ReadableProduct product = sampleProduct();

    ResponseEntity<PersistableProductReview> response =
        postAuthenticatedReview(
            product.getId(),
            DEFAULT_STORE,
            caller.token,
            review(victim.id, 999999L, 4D));

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(caller.id, response.getBody().getCustomerId());
    assertEquals(product.getId(), response.getBody().getProductId());
    ReadableProductReview[] savedReviews = reviews(product.getId());
    assertEquals(1, savedReviews.length);
    assertEquals(caller.id, savedReviews[0].getCustomer().getId());
    assertFalse(Arrays.stream(savedReviews).anyMatch(r -> victim.id.equals(r.getCustomer().getId())));
  }

  @Test
  public void authReviewWithoutCustomerIdIsAccepted() {
    CustomerSession caller = registerAndLogin(unique("caller") + "@test.com");
    ReadableProduct product = sampleProduct();

    ResponseEntity<PersistableProductReview> response =
        postAuthenticatedReview(
            product.getId(), DEFAULT_STORE, caller.token, review(null, product.getId(), 4D));

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(caller.id, response.getBody().getCustomerId());
    assertEquals(1, reviews(product.getId()).length);
    assertEquals(caller.id, reviews(product.getId())[0].getCustomer().getId());
  }

  @Test
  public void authReviewDuplicateRejected() {
    CustomerSession caller = registerAndLogin(unique("caller") + "@test.com");
    ReadableProduct product = sampleProduct();

    ResponseEntity<PersistableProductReview> first =
        postAuthenticatedReview(
            product.getId(), DEFAULT_STORE, caller.token, review(null, product.getId(), 4D));
    ResponseEntity<PersistableProductReview> duplicate =
        postAuthenticatedReview(
            product.getId(), DEFAULT_STORE, caller.token, review(null, 999999L, 3D));

    assertEquals(HttpStatus.CREATED, first.getStatusCode());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, duplicate.getStatusCode());
    assertEquals(1, reviews(product.getId()).length);
  }

  @Test
  public void authReviewRequiresToken() {
    ReadableProduct product = sampleProduct();
    PersistableProductReview review = review(null, product.getId(), 3D);

    ResponseEntity<String> noToken =
        testRestTemplate.exchange(
            "/api/v1/auth/products/" + product.getId() + "/reviews?store=" + DEFAULT_STORE,
            POST,
            new HttpEntity<>(review, headers(null)),
            String.class);
    ResponseEntity<String> badToken =
        testRestTemplate.exchange(
            "/api/v1/auth/products/" + product.getId() + "/reviews?store=" + DEFAULT_STORE,
            POST,
            new HttpEntity<>(review, headers(expiredToken())),
            String.class);

    assertEquals(HttpStatus.UNAUTHORIZED, noToken.getStatusCode());
    assertEquals(HttpStatus.UNAUTHORIZED, badToken.getStatusCode());
    assertEquals(0, reviews(product.getId()).length);
  }

  @Test
  public void authReviewOtherStoreForbidden() {
    String storeCode = unique("store");
    PersistableAddress address = new PersistableAddress();
    address.setAddress("121212 simple address");
    address.setPostalCode("12345");
    address.setCountry("US");
    address.setCity("FT LD");
    address.setStateProvince("FL");
    PersistableMerchantStore newStore = new PersistableMerchantStore();
    newStore.setCode(storeCode);
    newStore.setCurrency("CAD");
    newStore.setDefaultLanguage("en");
    newStore.setEmail(unique("store") + "@test.com");
    newStore.setName(storeCode);
    newStore.setPhone("444-555-6666");
    newStore.setSupportedLanguages(Arrays.asList("en"));
    newStore.setAddress(address);
    ResponseEntity<Void> storeResponse =
        testRestTemplate.postForEntity(
            "/api/v1/private/store/",
            new HttpEntity<>(newStore, getHeader()),
            Void.class);
    assertEquals(HttpStatus.OK, storeResponse.getStatusCode());

    CustomerSession caller = registerAndLogin(unique("caller") + "@test.com");
    ReadableProduct product = sampleProduct();
    ResponseEntity<PersistableProductReview> response =
        postAuthenticatedReview(
            product.getId(), storeCode, caller.token, review(null, product.getId(), 4D));

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    assertEquals(0, reviews(product.getId()).length);
  }

  @Test
  public void privateReviewKeepsBodyCustomer() {
    CustomerSession victim = registerAndLogin(unique("victim") + "@test.com");
    ReadableProduct product = sampleProduct();
    ResponseEntity<PersistableProductReview> response =
        testRestTemplate.exchange(
            "/api/v1/private/products/" + product.getId() + "/reviews?store=" + DEFAULT_STORE,
            POST,
            new HttpEntity<>(review(victim.id, product.getId(), 4D), getHeader()),
            PersistableProductReview.class);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    ReadableProductReview[] savedReviews = reviews(product.getId());
    assertEquals(1, savedReviews.length);
    assertEquals(victim.id, savedReviews[0].getCustomer().getId());
  }

  @Test
  public void privateReviewWithoutCustomerIdIs400() {
    ReadableProduct product = sampleProduct();
    ResponseEntity<String> response =
        testRestTemplate.exchange(
            "/api/v1/private/products/" + product.getId() + "/reviews?store=" + DEFAULT_STORE,
            POST,
            new HttpEntity<>(review(null, product.getId(), 4D), getHeader()),
            String.class);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals(0, reviews(product.getId()).length);
  }

  @Test
  public void authReviewRatingAboveMaxRejected() {
    CustomerSession caller = registerAndLogin(unique("caller") + "@test.com");
    ReadableProduct product = sampleProduct();

    ResponseEntity<String> response =
        testRestTemplate.exchange(
            "/api/v1/auth/products/" + product.getId() + "/reviews?store=" + DEFAULT_STORE,
            POST,
            new HttpEntity<>(review(null, product.getId(), 6D), headers(caller.token)),
            String.class);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    assertFalse(response.getStatusCode().is2xxSuccessful());
    assertEquals(0, reviews(product.getId()).length);
  }
}
