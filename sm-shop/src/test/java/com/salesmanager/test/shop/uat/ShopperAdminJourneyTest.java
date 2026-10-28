package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.manufacturer.ReadableManufacturerList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ShopperAdminJourneyTest extends ServicesTestSupport {
  @Test
  public void adminLoginAndProtectedRead() {
    HttpHeaders admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
    assertEquals(HttpStatus.OK, get("/api/v1/private/users/1", admin).getStatusCode());
    int status = get("/api/v1/private/users/1", new HttpHeaders()).getStatusCodeValue();
    assertTrue("Unauthenticated admin read: " + status, status == 401 || status == 403);
  }

  @Test
  public void customerRegistrationLoginAndProfile() {
    HttpHeaders customer = registerAndLogin();
    ResponseEntity<JsonNode> profile = get("/api/v1/auth/customer/profile", customer);
    assertEquals(HttpStatus.OK, profile.getStatusCode());
    assertNotNull(profile.getBody().get("id"));
  }

  @Test
  public void browseStoreCategoriesProductsAndDetail() {
    ReadableProduct product = sampleProduct(unique("browse"));
    assertEquals(HttpStatus.OK, get("/api/v1/store/DEFAULT", new HttpHeaders()).getStatusCode());
    ResponseEntity<JsonNode> categories = get("/api/v1/category?store=DEFAULT", new HttpHeaders());
    assertEquals(HttpStatus.OK, categories.getStatusCode());
    assertNotNull(categories.getBody());
    ResponseEntity<JsonNode> products = get("/api/v1/products?store=DEFAULT&count=100", new HttpHeaders());
    assertEquals(HttpStatus.OK, products.getStatusCode());
    assertTrue(products.getBody().path("products").toString().contains(product.getSku()));
    ResponseEntity<JsonNode> detail = get("/api/v2/product/" + product.getSku(), new HttpHeaders());
    assertEquals(HttpStatus.OK, detail.getStatusCode());
    assertEquals(product.getId().longValue(), detail.getBody().path("id").asLong());
  }

  @Test
  public void cartCreateAddAndReadBack() {
    ReadableShoppingCart cart = createCart();
    ReadableProduct second = sampleProduct(unique("second"));
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(second.getSku());
    item.setQuantity(2);
    ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + cart.getCode(),
        HttpMethod.PUT, new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, updated.getStatusCode());
    assertEquals(3, updated.getBody().getQuantity());
    ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity("/api/v1/cart/" + cart.getCode(),
        ReadableShoppingCart.class);
    assertEquals(HttpStatus.OK, read.getStatusCode());
    assertEquals(3, read.getBody().getQuantity());
    assertEquals(cart.getCode(), read.getBody().getCode());
  }

  @Test
  public void servicesBasicAuthentication() {
    HttpHeaders valid = new HttpHeaders();
    valid.setBasicAuth("admin@shopizer.com", "password");
    valid.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> accepted = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
        new HttpEntity<>("{}", valid), String.class);
    System.out.println("UAT services valid credentials status=" + accepted.getStatusCodeValue());
    assertEquals(HttpStatus.OK, accepted.getStatusCode());
    HttpHeaders invalid = new HttpHeaders();
    invalid.setBasicAuth("admin@shopizer.com", "incorrect");
    invalid.setContentType(MediaType.APPLICATION_JSON);
    int status = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
        new HttpEntity<>("{}", invalid), String.class).getStatusCodeValue();
    assertTrue("Invalid basic credentials: " + status, status == 401 || status == 403);
  }

  @Test
  public void healthAndSwaggerDocumentation() {
    ResponseEntity<JsonNode> health = get("/actuator/health", new HttpHeaders());
    assertEquals(HttpStatus.OK, health.getStatusCode());
    assertEquals("UP", health.getBody().path("status").asText());
    ResponseEntity<JsonNode> docs = get("/v2/api-docs", new HttpHeaders());
    assertEquals(HttpStatus.OK, docs.getStatusCode());
    assertEquals("2.0", docs.getBody().path("swagger").asText());
  }

  @Test
  public void manufacturerAdminStoreIsolationJourney() {
    HttpHeaders admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
    String owner = unique("uat");
    PersistableAddress address = new PersistableAddress();
    address.setAddress("1 UAT street");
    address.setPostalCode("12345");
    address.setCountry("US");
    address.setCity("FT LD");
    address.setStateProvince("FL");
    PersistableMerchantStore store = new PersistableMerchantStore();
    store.setCode(owner);
    store.setName(owner);
    store.setCurrency("CAD");
    store.setDefaultLanguage("en");
    store.setSupportedLanguages(Arrays.asList("en"));
    store.setEmail("uat@test.com");
    store.setPhone("444-555-6666");
    store.setAddress(address);
    assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
        new HttpEntity<>(store, admin), Void.class).getStatusCode());
    ResponseEntity<PersistableManufacturer> created = testRestTemplate.postForEntity(
        "/api/v1/private/manufacturer?store=" + owner, new HttpEntity<>(manufacturer(unique("brand")), admin),
        PersistableManufacturer.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    Long id = created.getBody().getId();
    assertNotNull(id);
    assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.exchange(
        "/api/v1/private/manufacturer/" + id + "?store=DEFAULT", HttpMethod.DELETE,
        new HttpEntity<>(admin), String.class).getStatusCode());
    assertManufacturerListed(owner, id, true);
    assertEquals(HttpStatus.OK, testRestTemplate.exchange(
        "/api/v1/private/manufacturer/" + id + "?store=" + owner, HttpMethod.DELETE,
        new HttpEntity<>(admin), String.class).getStatusCode());
    assertManufacturerListed(owner, id, false);
  }

  private void assertManufacturerListed(String owner, Long id, boolean expected) {
    ResponseEntity<ReadableManufacturerList> list = testRestTemplate.getForEntity(
        "/api/v1/manufacturers?store=" + owner + "&count=100", ReadableManufacturerList.class);
    assertEquals(HttpStatus.OK, list.getStatusCode());
    assertEquals(expected, list.getBody().getManufacturers().stream().anyMatch(m -> id.equals(m.getId())));
  }

  private ReadableShoppingCart createCart() {
    ReadableProduct product = sampleProduct(unique("cart"));
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(product.getSku());
    item.setQuantity(1);
    ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity("/api/v1/cart/",
        new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(1, response.getBody().getQuantity());
    return response.getBody();
  }

  private HttpHeaders registerAndLogin() {
    String email = unique("customer") + "@test.com";
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setGender("M");
    customer.setLanguage("en");
    customer.setStoreCode("DEFAULT");
    Address billing = new Address();
    billing.setFirstName("UAT");
    billing.setLastName("Customer");
    billing.setCountry("US");
    customer.setBilling(billing);
    assertEquals(HttpStatus.OK, testRestTemplate.postForEntity("/api/v1/customer/register",
        new HttpEntity<>(customer, getHeader()), PersistableCustomer.class).getStatusCode());
    return login("/api/v1/customer/login", email, "clear123");
  }

  private HttpHeaders login(String endpoint, String username, String password) {
    ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(endpoint,
        new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    String token = response.getBody().getToken();
    assertNotNull(token);
    assertFalse(token.isEmpty());
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private ResponseEntity<JsonNode> get(String url, HttpHeaders headers) {
    return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
  }

  private static String unique(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
