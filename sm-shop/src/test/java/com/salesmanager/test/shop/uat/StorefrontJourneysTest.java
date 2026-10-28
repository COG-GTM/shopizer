package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomerReview;
import com.salesmanager.shop.model.customer.ReadableCustomerReview;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.CustomerJourneySupport;

class StorefrontJourneysTest extends CustomerJourneySupport {
  @Test void adminJwtGrantsPrivateAccessButMissingTokenDoesNot() {
    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
        new AuthenticationRequest("admin@shopizer.com", "password"), AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody().getToken());
    String url = "/api/v1/private/customers?store=DEFAULT";
    assertEquals(HttpStatus.OK, request(url, HttpMethod.GET, null, bearer(login.getBody().getToken())).getStatusCode());
    assertDenied(request(url, HttpMethod.GET, null, new HttpHeaders()));
  }

  @Test void customerRegistersLogsInAndReadsAuthenticatedProfile() {
    String email = unique("uat") + "@example.com";
    AuthenticationResponse registered = register("DEFAULT", email);
    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
        new AuthenticationRequest(email, "clear123"), AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody().getToken());
    assertEquals(registered.getId(), login.getBody().getId());
    ResponseEntity<String> profile = request("/api/v1/auth/customer/profile", HttpMethod.GET, null,
        bearer(login.getBody().getToken()));
    assertEquals(HttpStatus.OK, profile.getStatusCode(), profile.getBody());
    assertTrue(profile.getBody().contains(email));
  }

  @Test void shopperBrowsesStoreCategoriesProductsAndDetail() throws Exception {
    ReadableProduct product = sampleProduct(unique("browse"));
    assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
    ResponseEntity<String> categories = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class);
    assertEquals(HttpStatus.OK, categories.getStatusCode(), categories.getBody());
    assertFalse(new ObjectMapper().readTree(categories.getBody()).isEmpty());
    ResponseEntity<String> products = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", String.class);
    assertEquals(HttpStatus.OK, products.getStatusCode(), products.getBody());
    assertTrue(products.getBody().contains(product.getSku()));
    ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity("/api/v2/product/" + product.getSku(), ReadableProduct.class);
    assertEquals(HttpStatus.OK, detail.getStatusCode());
    assertEquals(product.getSku(), detail.getBody().getSku());
  }

  @Test void shopperCreatesAddsUpdatesAndReadsCart() {
    ReadableProduct first = sampleProduct(unique("cartfirst"));
    ReadableProduct second = sampleProduct(unique("cartsecond"));
    PersistableShoppingCartItem item = item(first.getSku(), 1);
    ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart", item, ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    String url = "/api/v1/cart/" + created.getBody().getCode();
    ResponseEntity<ReadableShoppingCart> added = testRestTemplate.exchange(url, HttpMethod.PUT,
        new HttpEntity<>(item(second.getSku(), 1)), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, added.getStatusCode());
    assertEquals(2, added.getBody().getQuantity());
    ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(url, HttpMethod.PUT,
        new HttpEntity<>(item(first.getSku(), 2)), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, updated.getStatusCode());
    ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(url, ReadableShoppingCart.class);
    assertEquals(HttpStatus.OK, read.getStatusCode());
    assertEquals(3, read.getBody().getQuantity());
    assertEquals(2, read.getBody().getProducts().size());
  }

  @Test void authenticatedCustomerChecksOutAndReadsOwnOrder() throws Exception {
    HttpHeaders admin = getHeader();
    IntegrationModuleConfiguration paymentConfiguration = new IntegrationModuleConfiguration();
    paymentConfiguration.setCode("moneyorder");
    paymentConfiguration.setActive(true);
    paymentConfiguration.getIntegrationKeys().put("address", "123 Test Street");
    ResponseEntity<String> configured = request("/api/v1/private/modules/payment", HttpMethod.POST,
        paymentConfiguration, admin);
    assertEquals(HttpStatus.OK, configured.getStatusCode(), configured.getBody());
    AuthenticationResponse customer = register("DEFAULT", unique("checkout") + "@example.com");
    HttpHeaders token = bearer(customer.getToken());
    ReadableProduct product = sampleProduct(unique("checkout"));
    ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
        item(product.getSku(), 1), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    ReadableShoppingCart cart = created.getBody();
    PersistablePayment payment = new PersistablePayment();
    payment.setAmount(cart.getTotal().toPlainString());
    payment.setPaymentModule("moneyorder");
    payment.setPaymentType("MONEYORDER");
    payment.setTransactionType("AUTHORIZECAPTURE");
    PersistableOrder order = new PersistableOrder();
    order.setPayment(payment);
    order.setCurrency("CAD");
    order.setCustomerAgreement(true);
    ResponseEntity<String> checkedOut = request("/api/v1/auth/cart/" + cart.getCode() + "/checkout",
        HttpMethod.POST, order, token);
    assertEquals(HttpStatus.OK, checkedOut.getStatusCode(), checkedOut.getBody());
    ReadableOrderConfirmation confirmation = new ObjectMapper().readValue(checkedOut.getBody(), ReadableOrderConfirmation.class);
    assertNotNull(confirmation.getId());
    ResponseEntity<String> read = request("/api/v1/auth/orders/" + confirmation.getId(), HttpMethod.GET, null, token);
    assertEquals(HttpStatus.OK, read.getStatusCode(), read.getBody());
    JsonNode body = new ObjectMapper().readTree(read.getBody());
    assertEquals(confirmation.getId().longValue(), body.path("id").asLong());
    assertEquals(customer.getId().longValue(), body.path("customer").path("id").asLong());
  }

  @Test void legacyServicesAcceptValidBasicAuthAndRejectInvalidCredentials() {
    String url = "/services/private/system/optin";
    HttpHeaders valid = new HttpHeaders();
    valid.setBasicAuth("admin@shopizer.com", "password");
    assertEquals(HttpStatus.OK, request(url, HttpMethod.POST, "{}", valid).getStatusCode());
    HttpHeaders invalid = new HttpHeaders();
    invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
    assertDenied(request(url, HttpMethod.POST, "{}", invalid));
  }

  @Test void healthIsUpAndSwaggerV2IsAvailable() throws Exception {
    ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
    assertEquals(HttpStatus.OK, health.getStatusCode(), health.getBody());
    assertEquals("UP", new ObjectMapper().readTree(health.getBody()).path("status").asText());
    ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
    assertEquals(HttpStatus.OK, docs.getStatusCode(), docs.getBody());
    assertEquals("2.0", new ObjectMapper().readTree(docs.getBody()).path("swagger").asText());
  }

  @Test void adminReviewIsVisibleToAnonymousStorefrontButNotAnotherStore() {
    HttpHeaders admin = getHeader();
    String otherStore = createStore(admin);
    Long reviewed = register("DEFAULT", unique("reviewed") + "@example.com").getId();
    Long reviewer = register("DEFAULT", unique("reviewer") + "@example.com").getId();
    PersistableCustomerReview created = createReview(reviewed, reviewer, admin);
    ResponseEntity<ReadableCustomerReview[]> listed = testRestTemplate.getForEntity(
        reviewUrl(reviewed, false, "DEFAULT"), ReadableCustomerReview[].class);
    assertEquals(HttpStatus.OK, listed.getStatusCode());
    assertEquals(1, listed.getBody().length);
    assertEquals(created.getId(), listed.getBody()[0].getId());
    assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.getForEntity(
        reviewUrl(reviewed, false, otherStore), String.class).getStatusCode());
  }

  private PersistableShoppingCartItem item(String sku, int quantity) {
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(sku);
    item.setQuantity(quantity);
    return item;
  }

  private void assertDenied(ResponseEntity<?> response) {
    assertTrue(response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN,
        "Expected 401/403, got " + response.getStatusCode());
  }
}
