package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

  @Test
  public void adminLoginJourney() {
    String token = adminToken();
    assertEquals(OK, testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
        bearer(token), String.class).getStatusCode());
    int missingTokenStatus = protectedStatus("/api/v1/private/customers", null);
    assertTrue(missingTokenStatus == 401 || missingTokenStatus == 403);
    int badTokenStatus = protectedStatus("/api/v1/private/customers", "garbage");
    assertTrue("malformed token must be rejected, got " + badTokenStatus, badTokenStatus >= 400);
  }

  @Test
  public void customerRegisterLoginJourney() {
    String email = uniqueEmail();
    String password = "clear123";
    ResponseEntity<AuthenticationResponse> registered = testRestTemplate.postForEntity(
        "/api/v1/customer/register", new HttpEntity<>(customer(email, password)), AuthenticationResponse.class);
    assertEquals(OK, registered.getStatusCode());
    assertNotNull(registered.getBody());

    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
    assertEquals(OK, login.getStatusCode());
    assertNotNull(login.getBody());

    ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
        HttpMethod.GET, bearer(login.getBody().getToken()), String.class);
    assertEquals(OK, profile.getStatusCode());
    assertTrue(profile.getBody().contains(email));
  }

  @Test
  public void browseJourney() {
    ReadableProduct product = sampleProduct("uatBrowse" + System.nanoTime());
    ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
        "/api/v1/store/DEFAULT", ReadableMerchantStore.class);
    assertEquals(OK, store.getStatusCode());
    assertEquals(Constants.DEFAULT_STORE, store.getBody().getCode());
    assertEquals(OK, testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class)
        .getStatusCode());

    ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
        "/api/v1/products?store=DEFAULT", ReadableProductList.class);
    assertEquals(OK, products.getStatusCode());
    assertTrue(products.getBody().getRecordsTotal() >= 1 || !products.getBody().getProducts().isEmpty());

    ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
        "/api/v2/product/" + product.getSku(), ReadableProduct.class);
    assertEquals(OK, detail.getStatusCode());
    assertEquals(product.getId(), detail.getBody().getId());
  }

  @Test
  public void cartJourney() {
    ReadableProduct first = sampleProduct("uatCartFirst" + System.nanoTime());
    ReadableProduct second = sampleProduct("uatCartSecond" + System.nanoTime());
    PersistableShoppingCartItem firstItem = cartItem(first.getSku(), 1);
    ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
        "/api/v1/cart", new HttpEntity<>(firstItem), ReadableShoppingCart.class);
    assertEquals(CREATED, created.getStatusCode());
    assertNotNull(created.getBody());
    String code = created.getBody().getCode();

    ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + code,
        HttpMethod.PUT, new HttpEntity<>(cartItem(second.getSku(), 2)), ReadableShoppingCart.class);
    assertEquals(CREATED, updated.getStatusCode());
    assertEquals(3, updated.getBody().getQuantity());

    ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
        "/api/v1/cart/" + code, ReadableShoppingCart.class);
    assertEquals(OK, fetched.getStatusCode());
    assertEquals(3, fetched.getBody().getQuantity());
    assertEquals(2, fetched.getBody().getProducts().size());
  }

  @Test
  public void servicesBasicAuthJourney() {
    HttpHeaders valid = new HttpHeaders();
    valid.setBasicAuth("admin@shopizer.com", "password");
    ResponseEntity<String> notFound = testRestTemplate.exchange("/services/private/does-not-exist",
        HttpMethod.GET, new HttpEntity<>(valid), String.class);
    assertEquals(NOT_FOUND, notFound.getStatusCode());

    HttpHeaders invalid = new HttpHeaders();
    invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
    assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/services/private/does-not-exist",
        HttpMethod.GET, new HttpEntity<>(invalid), String.class).getStatusCode());
    int noCredentialsStatus = testRestTemplate.getForEntity("/services/private/does-not-exist",
        String.class).getStatusCodeValue();
    assertTrue("unauthenticated request must not succeed, got " + noCredentialsStatus,
        noCredentialsStatus < 200 || noCredentialsStatus >= 300);
  }

  @Test
  public void opsJourney() {
    ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
    assertEquals(OK, health.getStatusCode());
    assertTrue(health.getBody().contains("\"status\":\"UP\""));

    ResponseEntity<String> swagger = testRestTemplate.getForEntity("/v2/api-docs", String.class);
    assertEquals(OK, swagger.getStatusCode());
    assertTrue(swagger.getBody().contains("\"swagger\""));
  }

  private int protectedStatus(String path, String token) {
    HttpHeaders headers = new HttpHeaders();
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class)
        .getStatusCodeValue();
  }

  private String adminToken() {
    ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
        new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
        AuthenticationResponse.class);
    assertEquals(OK, response.getStatusCode());
    return response.getBody().getToken();
  }

  private PersistableCustomer customer(String email, String password) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword(password);
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    customer.setStoreCode(Constants.DEFAULT_STORE);
    Address billing = new Address();
    billing.setFirstName("UAT");
    billing.setLastName("Customer");
    billing.setCountry("BE");
    customer.setBilling(billing);
    return customer;
  }

  private PersistableShoppingCartItem cartItem(String sku, int quantity) {
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(sku);
    item.setQuantity(quantity);
    return item;
  }

  private HttpEntity<Void> bearer(String token) {
    return new HttpEntity<>(bearerHeaders(token));
  }

  private HttpHeaders bearerHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private String uniqueEmail() {
    return "journey-" + System.nanoTime() + "@test.com";
  }
}
