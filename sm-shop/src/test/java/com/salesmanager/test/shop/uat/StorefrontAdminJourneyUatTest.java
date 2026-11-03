package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.UserPassword;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

  private static final String INITIAL_PASSWORD = "Password1";
  private static final String CHANGED_PASSWORD = "Password2";

  @Test
  public void adminLoginPrivateEndpointAndPublicCatalogJourney() throws Exception {
    AuthenticationResponse login = adminLogin("admin@shopizer.com", "password");
    assertNotNull(login.getToken());
    assertEquals(HttpStatus.OK, get("/api/v1/private/users/1", bearer(login.getToken())).getStatusCode());

    HttpStatus absentTokenStatus = get("/api/v1/private/users/1", new HttpHeaders()).getStatusCode();
    HttpHeaders garbage = bearer("not-a-jwt");
    HttpStatus garbageTokenStatus = get("/api/v1/private/users/1", garbage).getStatusCode();
    assertEquals(HttpStatus.UNAUTHORIZED, absentTokenStatus);
    assertFalse(garbageTokenStatus.toString(), garbageTokenStatus.is2xxSuccessful());

    assertEquals(HttpStatus.OK, get("/api/v1/store/DEFAULT", loginHeader(login)).getStatusCode());
    assertEquals(HttpStatus.OK, get("/api/v1/category?store=DEFAULT", loginHeader(login)).getStatusCode());
    assertEquals(HttpStatus.OK, get("/api/v1/products?store=DEFAULT", loginHeader(login)).getStatusCode());

    ReadableProduct product = sampleProduct("pr311-uat-" + UUID.randomUUID().toString().replace("-", ""));
    assertNotNull(product);
    assertEquals(HttpStatus.OK,
        get("/api/v2/product/" + product.getSku() + "?store=DEFAULT", loginHeader(login)).getStatusCode());
  }

  @Test
  public void customerRegistrationLoginAndAuthenticatedProfileJourney() {
    String username = uniqueUsername();
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(username);
    customer.setUserName(username);
    customer.setPassword("Customer1");
    customer.setRepeatPassword("Customer1");
    customer.setFirstName("UAT");
    customer.setLastName("Customer");
    customer.setLanguage("en");
    customer.setStoreCode("DEFAULT");
    Address billing = new Address();
    billing.setFirstName("UAT");
    billing.setLastName("Customer");
    billing.setCountry("US");
    customer.setBilling(billing);
    ResponseEntity<ReadableCustomer> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
        new HttpEntity<PersistableCustomer>(customer, getHeader()), ReadableCustomer.class);
    assertEquals(registration.toString(), HttpStatus.OK, registration.getStatusCode());

    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
        new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(username, "Customer1")),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody());
    assertEquals(HttpStatus.OK,
        get("/api/v1/auth/customer/profile", bearer(login.getBody().getToken())).getStatusCode());
  }

  @Test
  public void cartCreateUpdateAndReadJourney() throws Exception {
    ReadableProduct firstProduct = sampleProduct("pr311-cart-a-" + UUID.randomUUID().toString().replace("-", ""));
    ReadableProduct secondProduct = sampleProduct("pr311-cart-b-" + UUID.randomUUID().toString().replace("-", ""));
    ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
        new HttpEntity<PersistableShoppingCartItem>(cartItem(firstProduct.getSku(), 1)), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    assertNotNull(created.getBody());

    ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
        "/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
        new HttpEntity<PersistableShoppingCartItem>(cartItem(secondProduct.getSku(), 2)), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, updated.getStatusCode());
    assertEquals(3, updated.getBody().getQuantity());

    ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
        "/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
    assertEquals(HttpStatus.OK, fetched.getStatusCode());
    assertNotNull(fetched.getBody());
    assertEquals(3, fetched.getBody().getQuantity());
  }

  @Test
  public void serviceBasicAuthActuatorAndApiDocsJourney() {
    HttpHeaders validBasic = new HttpHeaders();
    validBasic.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
    HttpHeaders invalidBasic = new HttpHeaders();
    invalidBasic.setBasicAuth("invalid@example.test", "wrong", StandardCharsets.UTF_8);
    String optInRequest = "{\"request\":\"test\"}";
    ResponseEntity<String> valid = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
        new HttpEntity<String>(optInRequest, validBasic), String.class);
    ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
        new HttpEntity<String>(optInRequest, invalidBasic), String.class);
    assertTrue(valid.toString(), valid.getStatusCode().is2xxSuccessful());
    assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());

    ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
    assertEquals(HttpStatus.OK, health.getStatusCode());
    assertTrue(health.getBody(), health.getBody().contains("\"status\":\"UP\""));
    assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
  }

  @Test
  public void staffDisableReenableAndPasswordChangeJourney() throws Exception {
    String username = uniqueUsername();
    ReadableUser staff = createAdminUser(username, INITIAL_PASSWORD);
    AuthenticationResponse initialLogin = adminLogin(username, INITIAL_PASSWORD);
    assertEquals(HttpStatus.OK,
        get("/api/v1/private/users/" + staff.getId(), bearer(initialLogin.getToken())).getStatusCode());

    setEnabled(staff.getId(), false);
    assertEquals(HttpStatus.UNAUTHORIZED,
        get("/api/v1/private/users/" + staff.getId(), bearer(initialLogin.getToken())).getStatusCode());
    assertEquals(HttpStatus.UNAUTHORIZED, rawAdminLogin(username, INITIAL_PASSWORD).getStatusCode());

    setEnabled(staff.getId(), true);
    assertEquals(HttpStatus.OK, rawAdminLogin(username, INITIAL_PASSWORD).getStatusCode());
    changePassword(staff.getId(), INITIAL_PASSWORD, CHANGED_PASSWORD);
    assertEquals(HttpStatus.UNAUTHORIZED,
        get("/api/v1/private/users/" + staff.getId(), bearer(initialLogin.getToken())).getStatusCode());
  }

  private ReadableUser createAdminUser(String username, String password) throws Exception {
    PersistableUser user = new PersistableUser();
    user.setDefaultLanguage("en");
    user.setEmailAddress(username);
    user.setUserName(username);
    user.setFirstName("Staff");
    user.setLastName("UAT");
    user.setPassword(password);
    user.setRepeatPassword(password);
    user.setActive(true);
    PersistableGroup group = new PersistableGroup();
    group.setName("ADMIN");
    user.getGroups().add(group);
    ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
        new HttpEntity<PersistableUser>(user, getHeader()), ReadableUser.class);
    assertEquals(response.toString(), HttpStatus.OK, response.getStatusCode());
    return response.getBody();
  }

  private void setEnabled(Long userId, boolean enabled) {
    PersistableUser update = new PersistableUser();
    update.setActive(enabled);
    ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/user/" + userId + "/enabled",
        HttpMethod.PATCH, new HttpEntity<PersistableUser>(update, getHeader()), Void.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  private void changePassword(Long userId, String oldPassword, String newPassword) {
    UserPassword password = new UserPassword();
    password.setPassword(oldPassword);
    password.setChangePassword(newPassword);
    ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/user/" + userId + "/password",
        HttpMethod.PATCH, new HttpEntity<UserPassword>(password, getHeader()), Void.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  private AuthenticationResponse adminLogin(String username, String password) {
    ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
        new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(username, password)),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private ResponseEntity<String> rawAdminLogin(String username, String password) {
    return testRestTemplate.postForEntity("/api/v1/private/login",
        new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(username, password)), String.class);
  }

  private PersistableShoppingCartItem cartItem(String sku, int quantity) {
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(sku);
    item.setQuantity(quantity);
    return item;
  }

  private ResponseEntity<String> get(String path, HttpHeaders headers) {
    return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<String>(headers), String.class);
  }

  private HttpHeaders loginHeader(AuthenticationResponse login) {
    return bearer(login.getToken());
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "Bearer " + token);
    return headers;
  }

  private String uniqueUsername() {
    return "pr311-uat-" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
  }
}
