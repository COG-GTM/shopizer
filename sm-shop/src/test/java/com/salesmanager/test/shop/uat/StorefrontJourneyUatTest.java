package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.util.Date;
import java.util.Map;

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
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

  private static final String DEFAULT_STORE = "DEFAULT";

  @Value("${jwt.secret}")
  private String jwtSecret;

  private static final class CustomerSession {
    private final String token;
    private final ReadableCustomer profile;

    private CustomerSession(String token, ReadableCustomer profile) {
      this.token = token;
      this.profile = profile;
    }
  }

  private String unique(String prefix) {
    return prefix + System.nanoTime();
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

  private CustomerSession registerAndLogin(String email) {
    String password = "clear123";
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword(password);
    customer.setGender("M");
    customer.setLanguage("en");
    customer.setStoreCode(DEFAULT_STORE);
    Address billing = new Address();
    billing.setFirstName("journey");
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
    ResponseEntity<ReadableCustomer> profile =
        testRestTemplate.exchange(
            "/api/v1/auth/customer/profile",
            GET,
            new HttpEntity<>(headers(login.getBody().getToken())),
            ReadableCustomer.class);
    assertEquals(HttpStatus.OK, profile.getStatusCode());
    assertNotNull(profile.getBody());
    return new CustomerSession(login.getBody().getToken(), profile.getBody());
  }

  private ReadableProduct sampleProduct() {
    ReadableProduct product = sampleProduct(unique("journey"));
    assertNotNull(product);
    assertNotNull(product.getId());
    return product;
  }

  private PersistableProductReview review(Long customerId, Long productId) {
    PersistableProductReview review = new PersistableProductReview();
    review.setCustomerId(customerId);
    review.setProductId(productId);
    review.setRating(4D);
    review.setDescription("journey-" + System.nanoTime());
    return review;
  }

  @Test
  public void adminLoginThenPrivateCall() {
    ResponseEntity<AuthenticationResponse> login =
        testRestTemplate.postForEntity(
            "/api/v1/private/login",
            new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
            AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody());
    assertNotNull(login.getBody().getToken());

    ResponseEntity<String> privateCall =
        testRestTemplate.exchange(
            "/api/v1/private/customers",
            GET,
            new HttpEntity<>(headers(login.getBody().getToken())),
            String.class);
    ResponseEntity<String> noToken =
        testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
    ResponseEntity<String> badToken =
        testRestTemplate.exchange(
            "/api/v1/private/customers",
            GET,
            new HttpEntity<>(headers(expiredToken())),
            String.class);

    assertEquals(HttpStatus.OK, privateCall.getStatusCode());
    assertEquals(HttpStatus.UNAUTHORIZED, noToken.getStatusCode());
    assertEquals(HttpStatus.UNAUTHORIZED, badToken.getStatusCode());
  }

  @Test
  public void customerRegisterLoginProfile() {
    String email = unique("customer") + "@test.com";

    CustomerSession customer = registerAndLogin(email);

    assertEquals(email, customer.profile.getEmailAddress());
  }

  @Test
  public void browseCatalog() {
    ResponseEntity<ReadableMerchantStore> store =
        testRestTemplate.getForEntity("/api/v1/store/DEFAULT", ReadableMerchantStore.class);
    ResponseEntity<ReadableCategoryList> categories =
        testRestTemplate.getForEntity(
            "/api/v1/category?store=DEFAULT", ReadableCategoryList.class);
    ReadableProduct createdProduct = sampleProduct();
    ResponseEntity<ReadableProductList> products =
        testRestTemplate.getForEntity(
            "/api/v1/products?store=DEFAULT", ReadableProductList.class);
    ResponseEntity<ReadableProduct> product =
        testRestTemplate.getForEntity(
            "/api/v2/product/" + createdProduct.getSku() + "?store=DEFAULT",
            ReadableProduct.class);

    assertEquals(HttpStatus.OK, store.getStatusCode());
    assertNotNull(store.getBody());
    assertEquals(DEFAULT_STORE, store.getBody().getCode());
    assertEquals(HttpStatus.OK, categories.getStatusCode());
    assertEquals(HttpStatus.OK, products.getStatusCode());
    assertNotNull(products.getBody());
    assertFalse(products.getBody().getProducts().isEmpty());
    assertEquals(HttpStatus.OK, product.getStatusCode());
    assertNotNull(product.getBody());
    assertEquals(createdProduct.getSku(), product.getBody().getSku());
  }

  @Test
  public void cartJourney() {
    ReadableProduct product = sampleProduct();
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(product.getSku());
    item.setQuantity(1);
    ResponseEntity<ReadableShoppingCart> created =
        testRestTemplate.exchange(
            "/api/v1/cart?store=DEFAULT",
            POST,
            new HttpEntity<>(item, headers(null)),
            ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    assertNotNull(created.getBody());

    item.setQuantity(2);
    ResponseEntity<ReadableShoppingCart> updated =
        testRestTemplate.exchange(
            "/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT",
            PUT,
            new HttpEntity<>(item, headers(null)),
            ReadableShoppingCart.class);
    ResponseEntity<ReadableShoppingCart> read =
        testRestTemplate.getForEntity(
            "/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT",
            ReadableShoppingCart.class);

    assertTrue(
        updated.getStatusCode() == HttpStatus.CREATED
            || updated.getStatusCode() == HttpStatus.OK);
    assertEquals(HttpStatus.OK, read.getStatusCode());
    assertNotNull(read.getBody());
    assertEquals(2, read.getBody().getQuantity());
    assertEquals(product.getSku(), read.getBody().getProducts().get(0).getSku());
  }

  @Test
  public void productReviewJourney() {
    CustomerSession customer = registerAndLogin(unique("review") + "@test.com");
    ReadableProduct product = sampleProduct();
    ResponseEntity<PersistableProductReview> response =
        testRestTemplate.exchange(
            "/api/v1/auth/products/" + product.getId() + "/reviews?store=DEFAULT",
            POST,
            new HttpEntity<>(review(null, product.getId()), headers(customer.token)),
            PersistableProductReview.class);
    ResponseEntity<ReadableProductReview[]> reviews =
        testRestTemplate.getForEntity(
            "/api/v1/product/" + product.getId() + "/reviews?store=DEFAULT",
            ReadableProductReview[].class);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(HttpStatus.OK, reviews.getStatusCode());
    assertNotNull(reviews.getBody());
    assertEquals(1, reviews.getBody().length);
    assertEquals(customer.profile.getId(), reviews.getBody()[0].getCustomer().getId());
  }

  @Test
  public void servicesBasicAuth() {
    HttpHeaders validHeaders = headers(null);
    validHeaders.setBasicAuth("admin@shopizer.com", "password");
    ResponseEntity<String> valid =
        testRestTemplate.exchange(
            "/services/private/system/optin",
            POST,
            new HttpEntity<>("{}", validHeaders),
            String.class);
    HttpHeaders invalidHeaders = headers(null);
    invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong");
    ResponseEntity<String> invalid =
        testRestTemplate.exchange(
            "/services/private/system/optin",
            POST,
            new HttpEntity<>("{}", invalidHeaders),
            String.class);

    assertFalse(valid.getStatusCode() == HttpStatus.UNAUTHORIZED);
    assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
  }

  @Test
  public void healthAndApiDocs() {
    ResponseEntity<Map> health =
        testRestTemplate.getForEntity("/actuator/health", Map.class);
    boolean healthy =
        health.getStatusCode() == HttpStatus.OK
            && health.getBody() != null
            && "UP".equals(health.getBody().get("status"));
    if (!healthy) {
      System.out.println("Actuator health response: " + health.getBody());
      ResponseEntity<Map> ping =
          testRestTemplate.getForEntity("/actuator/health/ping", Map.class);
      assertEquals(HttpStatus.OK, ping.getStatusCode());
      assertNotNull(ping.getBody());
      assertEquals("UP", ping.getBody().get("status"));
    }

    ResponseEntity<String> apiDocs =
        testRestTemplate.getForEntity("/v2/api-docs", String.class);
    assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
  }
}
