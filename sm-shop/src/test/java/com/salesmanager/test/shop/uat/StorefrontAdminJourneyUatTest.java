package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void adminCanLoginAndProtectedModulesRejectMissingAndBadTokens() {
    HttpHeaders admin = getHeader();
    ResponseEntity<String> ok = testRestTemplate.exchange(
        "/api/v1/private/modules/payment?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<String>(admin), String.class);
    assertEquals(HttpStatus.OK, ok.getStatusCode());
    assertStatus(HttpHeaders.EMPTY, HttpStatus.UNAUTHORIZED);
    HttpHeaders invalid = new HttpHeaders();
    invalid.setBearerAuth("not-a-valid-token");
    assertStatus(invalid, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  public void customerCanRegisterLoginAndReadAuthenticatedProfile() throws Exception {
    String email = unique("profile");
    AuthenticationResponse auth = registerAndLogin(email);
    HttpHeaders headers = bearer(auth.getToken());
    ResponseEntity<JsonNode> profile = testRestTemplate.exchange(
        "/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<String>(headers), JsonNode.class);
    assertEquals(HttpStatus.OK, profile.getStatusCode());
    assertEquals(email, profile.getBody().path("emailAddress").asText());
  }

  @Test
  public void browsesStoreCategoriesProductsAndProductDetails() throws Exception {
    HttpHeaders headers = getHeader();
    String slug = unique("browse-product");
    createProductWithFriendlyUrl(slug, headers);
    ResponseEntity<JsonNode> store = testRestTemplate.exchange(
        "/api/v1/store/DEFAULT", HttpMethod.GET, new HttpEntity<String>(headers), JsonNode.class);
    ResponseEntity<JsonNode> categories = testRestTemplate.exchange(
        "/api/v1/category?store=DEFAULT", HttpMethod.GET, new HttpEntity<String>(headers), JsonNode.class);
    ResponseEntity<JsonNode> products = testRestTemplate.exchange(
        "/api/v1/products?store=DEFAULT&count=10", HttpMethod.GET,
        new HttpEntity<String>(headers), JsonNode.class);
    assertEquals(HttpStatus.OK, store.getStatusCode());
    assertTrue(store.getBody().hasNonNull("code"));
    assertEquals(HttpStatus.OK, categories.getStatusCode());
    assertTrue(categories.getBody().isContainerNode());
    assertEquals(HttpStatus.OK, products.getStatusCode());
    JsonNode firstProduct = products.getBody().path("products").get(0);
    if (firstProduct == null) {
      firstProduct = products.getBody().get(0);
    }
    assertNotNull(firstProduct);
    ResponseEntity<JsonNode> detail = testRestTemplate.exchange(
        "/api/v1/product/" + slug + "?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<String>(headers), JsonNode.class);
    assertEquals(HttpStatus.OK, detail.getStatusCode());
    assertTrue(detail.getBody().hasNonNull("sku"));
  }

  @Test
  public void createsUpdatesAndReadsCartQuantityAndTotal() throws Exception {
    String sku = sampleProduct(unique("cart")).getSku();
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(sku);
    item.setQuantity(1);
    ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
        "/api/v1/cart?store=DEFAULT", new HttpEntity<>(item), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    assertNotNull(created.getBody());

    item.setQuantity(3);
    ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
        "/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT",
        HttpMethod.PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, updated.getStatusCode());
    ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
        "/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
    assertEquals(HttpStatus.OK, read.getStatusCode());
    assertEquals(3, read.getBody().getQuantity());
    assertTrue(read.getBody().getTotal().signum() > 0);
  }

  @Test
  public void checksOutAsAuthenticatedCustomerUsingMoneyOrderAndReadsOrder() throws Exception {
    String email = unique("checkout");
    AuthenticationResponse customer = registerAndLogin(email);
    HttpHeaders customerHeaders = bearer(customer.getToken());
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(sampleProduct(unique("order")).getSku());
    item.setQuantity(1);
    ReadableShoppingCart cart = testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
        new HttpEntity<>(item), ReadableShoppingCart.class).getBody();
    assertNotNull(cart);

    Map<String, Object> moneyOrderConfiguration = new HashMap<String, Object>();
    moneyOrderConfiguration.put("code", "moneyorder");
    moneyOrderConfiguration.put("active", true);
    moneyOrderConfiguration.put("integrationKeys",
        mapOf("address", "Test money order address", "transaction", "AUTHORIZECAPTURE"));
    ResponseEntity<String> configured = testRestTemplate.postForEntity(
        "/api/v1/private/modules/payment?store=DEFAULT",
        new HttpEntity<>(moneyOrderConfiguration, getHeader()), String.class);
    assertEquals(HttpStatus.OK, configured.getStatusCode());

    PersistablePayment payment = new PersistablePayment();
    payment.setPaymentModule("moneyorder");
    payment.setPaymentType("MONEYORDER");
    payment.setTransactionType("AUTHORIZECAPTURE");
    payment.setAmount(cart.getTotal().toPlainString());
    PersistableOrder order = new PersistableOrder();
    order.setPayment(payment);
    order.setCurrency("USD");
    ResponseEntity<JsonNode> checkout = testRestTemplate.postForEntity(
        "/api/v1/auth/cart/" + cart.getCode() + "/checkout?store=DEFAULT",
        new HttpEntity<>(order, customerHeaders), JsonNode.class);
    assertEquals(HttpStatus.OK, checkout.getStatusCode());
    JsonNode orderResponse = checkout.getBody();
    assertNotNull(orderResponse);
    JsonNode id = orderResponse.has("id") ? orderResponse.path("id") : orderResponse.path("orderId");
    assertTrue(id.isNumber());

    ResponseEntity<JsonNode> read = testRestTemplate.exchange(
        "/api/v1/auth/orders/" + id.asText() + "?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<String>(customerHeaders), JsonNode.class);
    assertEquals(HttpStatus.OK, read.getStatusCode());
    assertEquals(id.asLong(), read.getBody().path("id").asLong());
  }

  @Test
  public void servicesPrivateRequiresValidBasicAuthentication() {
    HttpHeaders invalid = new HttpHeaders();
    invalid.setBasicAuth("invalid-user", "invalid-password");
    ResponseEntity<String> denied = testRestTemplate.exchange(
        "/services/private/DEFAULT", HttpMethod.GET, new HttpEntity<String>(invalid), String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());

    HttpHeaders valid = new HttpHeaders();
    valid.setBasicAuth("admin@shopizer.com", "password");
    ResponseEntity<String> authenticated = testRestTemplate.exchange(
        "/services/private/DEFAULT", HttpMethod.GET, new HttpEntity<String>(valid), String.class);
    assertTrue(authenticated.getStatusCode() != HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void exposesHealthAndOpenApiDocs() {
    ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
    assertEquals(HttpStatus.OK, health.getStatusCode());
    assertEquals("UP", health.getBody().path("status").asText());
    ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
    assertEquals(HttpStatus.OK, docs.getStatusCode());
  }

  @Test
  public void configuresAndReadsPaypalModuleAsAdmin() throws Exception {
    HttpHeaders admin = getHeader();
    Map<String, Object> body = new HashMap<String, Object>();
    body.put("code", "paypal-express-checkout");
    body.put("active", true);
    body.put("integrationKeys", mapOf("clientId", "uat-client", "clientSecret", "uat-secret"));
    ResponseEntity<String> saved = testRestTemplate.postForEntity(
        "/api/v1/private/modules/payment?store=DEFAULT", new HttpEntity<>(body, admin), String.class);
    assertEquals(HttpStatus.OK, saved.getStatusCode());
    ResponseEntity<JsonNode> read = testRestTemplate.exchange(
        "/api/v1/private/modules/payment/paypal-express-checkout?store=DEFAULT",
        HttpMethod.GET, new HttpEntity<String>(admin), JsonNode.class);
    assertEquals(HttpStatus.OK, read.getStatusCode());
    assertEquals("uat-client", read.getBody().path("integrationKeys").path("clientId").asText());
  }

  private AuthenticationResponse registerAndLogin(String email) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    customer.setStoreCode(Constants.DEFAULT_STORE);
    Address billing = new Address();
    billing.setFirstName("Journey");
    billing.setLastName("Customer");
    billing.setCountry("BE");
    customer.setBilling(billing);
    ResponseEntity<String> registration = testRestTemplate.postForEntity(
        "/api/v1/customer/register?store=DEFAULT",
        new HttpEntity<>(customer, getHeader()), String.class);
    assertTrue(registration.getStatusCode().is2xxSuccessful());
    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/customer/login?store=DEFAULT",
        new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody().getToken());
    return login.getBody();
  }

  private void assertStatus(HttpHeaders headers, HttpStatus expectedStatus) {
    ResponseEntity<String> response = testRestTemplate.exchange(
        "/api/v1/private/modules/payment?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<String>(headers), String.class);
    assertEquals(expectedStatus, response.getStatusCode());
  }

  private void createProductWithFriendlyUrl(String slug, HttpHeaders admin) {
    PersistableCategory category = category(unique("browse-category"), slug);
    ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
        "/api/v1/private/category?store=DEFAULT", new HttpEntity<>(category, admin),
        PersistableCategory.class);
    assertEquals(HttpStatus.CREATED, categoryResponse.getStatusCode());
    assertNotNull(categoryResponse.getBody());

    PersistableProduct product = product(slug);
    product.getDescriptions().get(0).setFriendlyUrl(slug);
    product.getCategories().add(categoryResponse.getBody());
    ProductSpecification specification = new ProductSpecification();
    specification.setManufacturer(Manufacturer.DEFAULT_MANUFACTURER);
    product.setProductSpecifications(specification);
    product.setAvailable(true);
    product.setPrice(BigDecimal.TEN);
    product.setSku(slug);
    product.setQuantity(100);
    ResponseEntity<Entity> productResponse = testRestTemplate.postForEntity(
        "/api/v1/private/product?store=DEFAULT", new HttpEntity<>(product, admin), Entity.class);
    assertEquals(HttpStatus.CREATED, productResponse.getStatusCode());
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private String unique(String prefix) {
    return prefix + System.currentTimeMillis();
  }

  private Map<String, String> mapOf(String... values) {
    Map<String, String> result = new HashMap<String, String>();
    for (int index = 0; index < values.length; index += 2) {
      result.put(values[index], values[index + 1]);
    }
    return result;
  }
}
