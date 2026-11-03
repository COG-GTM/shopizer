package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class StoreJourneysUatTest extends ServicesTestSupport {

  @Test
  public void customerCatalogAndCartJourney() {
    ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity(
        "/api/v1/private/login", new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, adminLogin.getStatusCode());
    HttpHeaders adminHeaders = bearer(adminLogin.getBody().getToken());

    ResponseEntity<JsonNode> privateWithToken = testRestTemplate.exchange(
        "/api/v1/private/customers?store=DEFAULT", HttpMethod.GET, new HttpEntity<>(adminHeaders), JsonNode.class);
    ResponseEntity<JsonNode> privateWithoutToken = testRestTemplate.getForEntity(
        "/api/v1/private/customers?store=DEFAULT", JsonNode.class);
    HttpHeaders garbageHeaders = bearer("not-a-valid-token");
    ResponseEntity<JsonNode> privateWithGarbageToken = testRestTemplate.exchange(
        "/api/v1/private/customers?store=DEFAULT", HttpMethod.GET, new HttpEntity<>(garbageHeaders), JsonNode.class);
    assertEquals(HttpStatus.OK, privateWithToken.getStatusCode());
    assertFalse(privateWithoutToken.getStatusCode().is2xxSuccessful());
    assertFalse(privateWithGarbageToken.getStatusCode().is2xxSuccessful());

    String suffix = UUID.randomUUID().toString().replace("-", "");
    String email = "journey-" + suffix + "@example.test";
    PersistableCustomer registration = customer(email, "Journey");
    ResponseEntity<JsonNode> registrationResponse = testRestTemplate.postForEntity(
        "/api/v1/customer/register", new HttpEntity<>(registration), JsonNode.class);
    assertTrue(registrationResponse.getStatusCode().is2xxSuccessful(), registrationResponse.toString());

    ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
        "/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, customerLogin.getStatusCode());
    HttpHeaders customerHeaders = bearer(customerLogin.getBody().getToken());
    ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
        "/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<>(customerHeaders), ReadableCustomer.class);
    assertEquals(HttpStatus.OK, profile.getStatusCode());
    assertEquals(email, profile.getBody().getEmailAddress());

    ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
        "/api/v1/store/DEFAULT", ReadableMerchantStore.class);
    ResponseEntity<JsonNode> categories = testRestTemplate.getForEntity(
        "/api/v1/category?store=DEFAULT", JsonNode.class);
    ResponseEntity<ReadableProductList> productsResponse = testRestTemplate.getForEntity(
        "/api/v1/products?store=DEFAULT", ReadableProductList.class);
    assertEquals(HttpStatus.OK, store.getStatusCode());
    assertEquals(HttpStatus.OK, categories.getStatusCode());
    assertEquals(HttpStatus.OK, productsResponse.getStatusCode());

    ReadableProduct product;
    if (productsResponse.getBody() == null || productsResponse.getBody().getProducts().isEmpty()) {
      product = sampleProduct("uat-" + suffix);
    } else {
      product = productsResponse.getBody().getProducts().get(0);
    }
    ResponseEntity<ReadableProduct> productDetail = testRestTemplate.getForEntity(
        "/api/v2/product/" + product.getSku() + "?store=DEFAULT", ReadableProduct.class);
    assertEquals(HttpStatus.OK, productDetail.getStatusCode());

    PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
    cartItem.setProduct(product.getSku());
    cartItem.setQuantity(1);
    ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
        "/api/v1/cart?store=DEFAULT", new HttpEntity<>(cartItem, customerHeaders), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
    String cartCode = createdCart.getBody().getCode();

    cartItem.setQuantity(2);
    ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
        "/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.PUT,
        new HttpEntity<>(cartItem, customerHeaders), ReadableShoppingCart.class);
    ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.exchange(
        "/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<>(customerHeaders), ReadableShoppingCart.class);
    assertTrue(updatedCart.getStatusCode().is2xxSuccessful());
    assertEquals(HttpStatus.OK, readCart.getStatusCode());
    assertEquals(2, readCart.getBody().getQuantity());

    HttpHeaders validServiceAuth = new HttpHeaders();
    validServiceAuth.setBasicAuth("admin@shopizer.com", "password");
    ResponseEntity<JsonNode> serviceValid = testRestTemplate.exchange(
        "/services/private/system/optin", HttpMethod.POST,
        new HttpEntity<>("{\"code\":\"uat-" + suffix + "\"}", validServiceAuth), JsonNode.class);
    HttpHeaders invalidServiceAuth = new HttpHeaders();
    invalidServiceAuth.setBasicAuth("admin@shopizer.com", "wrong-password");
    ResponseEntity<JsonNode> serviceInvalid = testRestTemplate.exchange(
        "/services/private/system/optin", HttpMethod.POST,
        new HttpEntity<>("{\"code\":\"uat-" + suffix + "\"}", invalidServiceAuth), JsonNode.class);
    assertTrue(serviceValid.getStatusCode().is2xxSuccessful(), serviceValid.toString());
    assertFalse(serviceInvalid.getStatusCode().is2xxSuccessful());

    ResponseEntity<JsonNode> actuator = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
    ResponseEntity<JsonNode> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
    assertEquals(HttpStatus.OK, actuator.getStatusCode());
    assertEquals("UP", actuator.getBody().path("status").asText());
    assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
  }

  private PersistableCustomer customer(String email, String firstName) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    customer.setStoreCode("DEFAULT");
    customer.setFirstName(firstName);
    customer.setLastName("Journey");
    Address billing = new Address();
    billing.setFirstName(firstName);
    billing.setLastName("Journey");
    billing.setAddress("1 Journey Street");
    billing.setCity("New York");
    billing.setPostalCode("10001");
    billing.setCountry("US");
    customer.setBilling(billing);
    return customer;
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
