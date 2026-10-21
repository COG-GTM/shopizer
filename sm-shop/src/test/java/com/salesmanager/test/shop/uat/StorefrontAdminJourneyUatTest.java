package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

  @Test
  void adminAndStorefrontJourney() {
    HttpHeaders adminHeaders = getHeader();
    ResponseEntity<JsonNode> users = testRestTemplate.exchange(
        "/api/v1/private/users?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<>(adminHeaders), JsonNode.class);
    ResponseEntity<String> anonymousUsers = testRestTemplate.exchange(
        "/api/v1/private/users?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<>(new HttpHeaders()), String.class);
    HttpHeaders invalidTokenHeaders = new HttpHeaders();
    invalidTokenHeaders.setBearerAuth("bad.token.value");
    ResponseEntity<String> invalidTokenUsers = testRestTemplate.exchange(
        "/api/v1/private/users?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<>(invalidTokenHeaders), String.class);

    ResponseEntity<JsonNode> store = testRestTemplate.getForEntity(
        "/api/v1/store/DEFAULT", JsonNode.class);
    ResponseEntity<JsonNode> categories = testRestTemplate.getForEntity(
        "/api/v1/category?store=DEFAULT", JsonNode.class);
    ReadableProduct sampledProduct = sampleProduct("uat" + UUID.randomUUID().toString()
        .replace("-", "").substring(0, 8));
    assertNotNull(sampledProduct);
    String sku = sampledProduct.getSku();
    ResponseEntity<JsonNode> products = testRestTemplate.getForEntity(
        "/api/v1/products?store=DEFAULT", JsonNode.class);
    assertNotNull(products.getBody());
    ResponseEntity<JsonNode> product = testRestTemplate.getForEntity(
        "/api/v2/product/" + sku, JsonNode.class);

    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String email = "uat" + suffix + "@test.com";
    PersistableCustomer registration = customer("DEFAULT", email, "Uat", "User", "1 Main st");
    ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity(
        "/api/v1/customer/register", new HttpEntity<>(registration), PersistableCustomer.class);
    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
        AuthenticationResponse.class);
    assertNotNull(login.getBody());
    HttpHeaders customerHeaders = new HttpHeaders();
    customerHeaders.setBearerAuth(login.getBody().getToken());
    ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
        "/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<>(customerHeaders), ReadableCustomer.class);

    ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
        "/api/v1/cart?store=DEFAULT",
        new HttpEntity<>(cartItem(sku, 1)), ReadableShoppingCart.class);
    assertNotNull(createdCart.getBody());
    String cartCode = createdCart.getBody().getCode();
    ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
        "/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.PUT,
        new HttpEntity<>(cartItem(sku, 2)), ReadableShoppingCart.class);
    ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.getForEntity(
        "/api/v1/cart/" + cartCode + "?store=DEFAULT", ReadableShoppingCart.class);

    HttpHeaders validBasicAuth = new HttpHeaders();
    validBasicAuth.setBasicAuth("admin@shopizer.com", "password");
    HttpHeaders invalidBasicAuth = new HttpHeaders();
    invalidBasicAuth.setBasicAuth("admin@shopizer.com", "wrong");
    ResponseEntity<String> validServices = testRestTemplate.exchange(
        "/services/private/ping", HttpMethod.GET,
        new HttpEntity<>(validBasicAuth), String.class);
    ResponseEntity<String> invalidServices = testRestTemplate.exchange(
        "/services/private/ping", HttpMethod.GET,
        new HttpEntity<>(invalidBasicAuth), String.class);
    ResponseEntity<JsonNode> health = testRestTemplate.getForEntity(
        "/actuator/health", JsonNode.class);
    ResponseEntity<String> apiDocs = testRestTemplate.getForEntity(
        "/v2/api-docs", String.class);

    assertEquals(HttpStatus.OK, users.getStatusCode());
    assertEquals(HttpStatus.UNAUTHORIZED, anonymousUsers.getStatusCode());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, invalidTokenUsers.getStatusCode());
    assertEquals(HttpStatus.OK, store.getStatusCode());
    assertEquals(HttpStatus.OK, categories.getStatusCode());
    assertEquals(HttpStatus.OK, products.getStatusCode());
    assertEquals(HttpStatus.OK, product.getStatusCode());
    assertEquals(HttpStatus.OK, registered.getStatusCode());
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertEquals(HttpStatus.OK, profile.getStatusCode());
    assertEquals(email, profile.getBody().getEmailAddress());
    assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
    assertEquals(HttpStatus.CREATED, updatedCart.getStatusCode());
    assertEquals(HttpStatus.OK, readCart.getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, validServices.getStatusCode());
    assertEquals(HttpStatus.UNAUTHORIZED, invalidServices.getStatusCode());
    assertEquals(HttpStatus.OK, health.getStatusCode());
    assertEquals("UP", health.getBody().path("status").asText());
    assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
    assertTrue(cartCode.length() > 0);
  }

  private PersistableCustomer customer(
      String storeCode, String email, String firstName, String lastName, String address) {
    Address billing = new Address();
    billing.setFirstName(firstName);
    billing.setLastName(lastName);
    billing.setAddress(address);
    billing.setCity("Montreal");
    billing.setPostalCode("H2H2H2");
    billing.setCountry("CA");
    billing.setZone("QC");

    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setGender("M");
    customer.setLanguage("en");
    customer.setStoreCode(storeCode);
    customer.setBilling(billing);
    return customer;
  }

  private PersistableShoppingCartItem cartItem(String sku, int quantity) {
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(sku);
    item.setQuantity(quantity);
    return item;
  }
}
