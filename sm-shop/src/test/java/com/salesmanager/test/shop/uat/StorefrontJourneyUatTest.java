package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductPrice;
import com.salesmanager.shop.model.catalog.product.attribute.ReadableProductVariantValue;
import com.salesmanager.shop.model.catalog.product.attribute.ReadableSelectedProductVariant;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box storefront/admin journeys over HTTP (random port, H2), including the
 * store-scoped variant price journey changed by PR #212.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

  @Test
  public void adminLoginGrantsAccessToPrivateApiAndMissingOrBadTokenIsRejected() {
    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
        new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")), AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody().getToken());

    assertEquals(HttpStatus.OK, get("/api/v1/private/customers", bearer(login.getBody().getToken())).getStatusCode());
    assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/customers", new HttpHeaders()).getStatusCode());
    // A malformed token is rejected (currently 500 on both 3.2.7 and this PR, not 401).
    assertFalse(get("/api/v1/private/customers", bearer("not-a-jwt")).getStatusCode().is2xxSuccessful());
  }

  @Test
  public void adminLoginWithWrongPasswordIsRejected() {
    ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/private/login",
        new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong-password")), String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, login.getStatusCode());
  }

  @Test
  public void customerRegistersLogsInAndReadsProfile() {
    String token = registerAndLogin("uat-journey@test.com", "clear123");

    ResponseEntity<String> profile = get("/api/v1/auth/customer/profile", bearer(token));
    assertEquals(HttpStatus.OK, profile.getStatusCode());
    assertTrue(profile.getBody().contains("uat-journey@test.com"));

    assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/auth/customer/profile", new HttpHeaders()).getStatusCode());
  }

  @Test
  public void shopperBrowsesStoreCategoriesAndProducts() {
    ReadableProduct product = sampleProduct("uatbrowse");

    assertEquals(HttpStatus.OK, get("/api/v1/store/" + Constants.DEFAULT_STORE, new HttpHeaders()).getStatusCode());
    assertEquals(HttpStatus.OK, get("/api/v1/category?store=" + Constants.DEFAULT_STORE, new HttpHeaders()).getStatusCode());

    ResponseEntity<String> list = get("/api/v1/products?store=" + Constants.DEFAULT_STORE, new HttpHeaders());
    assertEquals(HttpStatus.OK, list.getStatusCode());
    assertTrue(list.getBody().contains("\"sku\":\"uatbrowse\""));

    ResponseEntity<ReadableProduct> detail = testRestTemplate.exchange("/api/v2/product/" + product.getSku() + "?store=" + Constants.DEFAULT_STORE,
        HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), ReadableProduct.class);
    assertEquals(HttpStatus.OK, detail.getStatusCode());
    assertEquals("uatbrowse", detail.getBody().getSku());
  }

  @Test
  public void shopperPricesVariantOnlyInOwningStore() {
    ReadableProduct product = sampleProduct("uatvariant");
    ReadableProductVariantValue v = new ReadableProductVariantValue();
    v.setOption(999999L);
    v.setValue(999999L);
    ReadableSelectedProductVariant selection = new ReadableSelectedProductVariant();
    selection.setOptions(Collections.singletonList(v));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<ReadableProductPrice> price = testRestTemplate.postForEntity(
        "/api/v2/product/" + product.getId() + "/variation?store=" + Constants.DEFAULT_STORE,
        new HttpEntity<>(selection, headers), ReadableProductPrice.class);
    assertEquals(HttpStatus.OK, price.getStatusCode());
    assertNotNull(price.getBody().getFinalPrice());
    assertNotNull(price.getBody().getOriginalPrice());

    ResponseEntity<String> missing = testRestTemplate.postForEntity(
        "/api/v2/product/" + (product.getId() + 100000) + "/variation?store=" + Constants.DEFAULT_STORE,
        new HttpEntity<>(selection, headers), String.class);
    assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
  }

  @Test
  public void shopperCreatesCartAddsItemsAndReadsItBack() {
    ReadableProduct first = sampleProduct("uatcart1");
    ReadableProduct second = sampleProduct("uatcart2");

    ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart/",
        new HttpEntity<>(item(first.getSku(), 1), jsonHeaders()), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    String code = created.getBody().getCode();
    assertNotNull(code);

    ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
        new HttpEntity<>(item(second.getSku(), 2), jsonHeaders()), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, updated.getStatusCode());

    ResponseEntity<ReadableShoppingCart> read = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.GET,
        new HttpEntity<>(new HttpHeaders()), ReadableShoppingCart.class);
    assertEquals(HttpStatus.OK, read.getStatusCode());
    assertEquals(code, read.getBody().getCode());
    assertEquals(3, read.getBody().getQuantity());
  }

  @Test
  public void servicesBasicAuthAcceptsValidAndRejectsInvalidCredentials() {
    HttpHeaders good = new HttpHeaders();
    good.setBasicAuth("admin@shopizer.com", "password");
    good.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> ok = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
        new HttpEntity<>("{}", good), String.class);
    assertNotEquals(HttpStatus.UNAUTHORIZED, ok.getStatusCode());
    assertNotEquals(HttpStatus.FORBIDDEN, ok.getStatusCode());

    HttpHeaders bad = new HttpHeaders();
    bad.setBasicAuth("admin@shopizer.com", "wrong");
    bad.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> rejected = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
        new HttpEntity<>("{}", bad), String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
  }

  @Test
  public void actuatorHealthIsUpAndApiDocsRespond() {
    ResponseEntity<String> health = get("/actuator/health", new HttpHeaders());
    assertEquals(HttpStatus.OK, health.getStatusCode());
    assertTrue(health.getBody().contains("\"UP\""));

    ResponseEntity<String> docs = get("/v2/api-docs", new HttpHeaders());
    assertEquals(HttpStatus.OK, docs.getStatusCode());
    assertTrue(docs.getBody().contains("/api/v2/product/{id}/variation"));
  }

  private String registerAndLogin(String email, String password) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword(password);
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    Address billing = new Address();
    billing.setFirstName("uat");
    billing.setLastName("journey");
    billing.setCountry("CA");
    customer.setBilling(billing);
    customer.setStoreCode(Constants.DEFAULT_STORE);
    ResponseEntity<String> register = testRestTemplate.postForEntity("/api/v1/customer/register",
        new HttpEntity<>(customer, jsonHeaders()), String.class);
    assertEquals(HttpStatus.OK, register.getStatusCode());

    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody().getToken());
    return login.getBody().getToken();
  }

  private ResponseEntity<String> get(String url, HttpHeaders headers) {
    return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "Bearer " + token);
    return headers;
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static PersistableShoppingCartItem item(String sku, int quantity) {
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(sku);
    item.setQuantity(quantity);
    return item;
  }
}
