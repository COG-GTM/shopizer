package com.salesmanager.test.shop.uat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import static org.junit.jupiter.api.Assertions.*;

/** Real random-port HTTP acceptance journeys, including CMS bytes used by the storefront. */
class CmsUpgradeJourneyTest extends ServicesTestSupport {
  private final ObjectMapper json = new ObjectMapper();
  @Autowired EmailService emailService;

  @Test
  void adminUploadsReplacesDownloadsAndDeletesStorefrontImage() throws Exception {
    String token = admin();
    assertTrue(request(HttpMethod.GET, "/api/v1/private/customers", null, token, 200).has("customers"));
    String name = unique() + ".png";
    String path = "/static/files/DEFAULT/IMAGE/" + name;
    byte[] first = "first CMS bytes\u0000\u00ff".getBytes(StandardCharsets.UTF_8);
    byte[] second = "replacement CMS bytes".getBytes(StandardCharsets.UTF_8);
    assertEquals(401, exchange(HttpMethod.GET, "/api/v1/private/customers", null, null).getStatusCode().value());
    upload(name, first, null, 401);
    try {
      upload(name, first, token, 201);
      assertListed(name, true);
      assertBytes(path, first);
      upload(name, second, token, 201);
      assertBytes(path, second);
      request(HttpMethod.DELETE, "/api/v1/private/content/?name=" + name + "&contentType=IMAGE", null, token, 200);
      assertListed(name, false);
    } finally {
      request(HttpMethod.DELETE, "/api/v1/private/content/?name=" + name + "&contentType=IMAGE", null, token, 200);
    }
  }

  @Test
  void storefrontCanBrowseAndReadHealthAndApiDocs() throws Exception {
    assertEquals("DEFAULT", request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200).path("code").asText());
    assertTrue(request(HttpMethod.GET, "/api/v1/category/", null, null, 200).isContainerNode());
    assertTrue(request(HttpMethod.GET, "/api/v1/products", null, null, 200).has("products"));
    assertEquals("UP", request(HttpMethod.GET, "/actuator/health", null, null, 200).path("status").asText());
    assertTrue(request(HttpMethod.GET, "/v3/api-docs", null, null, 200).has("paths"));
  }

  @Test
  void customerRegistersUsesCartAndReadsAuthenticatedCheckout() throws Exception {
    String token = admin();
    String sku = unique();
    ObjectNode product = json.createObjectNode().put("sku", sku).put("available", true).put("price", 10).put("quantity", 100);
    product.putObject("productSpecifications").put("manufacturer", "DEFAULT");
    product.putArray("descriptions").addObject().put("name", sku).put("language", "en");
    product.putObject("inventory").put("quantity", 100).put("sku", sku)
        .putObject("price").put("defaultPrice", true).put("price", 10);
    long productId = request(HttpMethod.POST, "/api/v1/private/product", product, token, 201).path("id").asLong();
    Object sender = ReflectionTestUtils.getField(emailService, "sender");
    // Checkout emits mail; replace only the external SMTP transport, as in BehaviourSmokeTest.
    HtmlEmailSender transport = org.mockito.Mockito.mock(HtmlEmailSender.class);
    ReflectionTestUtils.setField(emailService, "sender", transport);
    try {
      assertEquals(sku, request(HttpMethod.GET, "/api/v2/product/" + sku, null, null, 200).path("sku").asText());
      String email = unique() + "@example.test";
      ObjectNode customer = json.createObjectNode().put("emailAddress", email).put("password", "clear123")
          .put("repeatPassword", "clear123").put("gender", "M").put("language", "en").put("storeCode", "DEFAULT");
      customer.putObject("billing").put("firstName", "CMS").put("lastName", "Customer").put("country", "BE")
          .put("city", "Brussels").put("address", "1 Test Street").put("postalCode", "1000").put("phone", "123456789");
      long customerId = request(HttpMethod.POST, "/api/v1/customer/register", customer, null, 200).path("id").asLong();
      String customerToken = request(HttpMethod.POST, "/api/v1/customer/login",
          json.createObjectNode().put("username", email).put("password", "clear123"), null, 200).path("token").asText();
      assertFalse(customerToken.isBlank());
      assertEquals(email, request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, customerToken, 200).path("emailAddress").asText());
      ObjectNode item = json.createObjectNode().put("product", sku).put("quantity", 1);
      String code = request(HttpMethod.POST, "/api/v1/cart", item, null, 201).path("code").asText();
      item.put("quantity", 2);
      assertEquals(2, request(HttpMethod.PUT, "/api/v1/cart/" + code, item, null, 201).path("quantity").asInt());
      assertEquals(2, request(HttpMethod.GET, "/api/v1/cart/" + code, null, null, 200).path("quantity").asInt());
      ObjectNode payment = json.createObjectNode().put("code", "moneyorder").put("active", true);
      payment.putObject("integrationKeys").put("transaction", "AUTHORIZECAPTURE").put("address", "1 Test Street");
      payment.putObject("integrationOptions");
      request(HttpMethod.POST, "/api/v1/private/modules/payment", payment, token, 200);
      ObjectNode order = json.createObjectNode().put("currency", "CAD").put("customerAgreement", true);
      order.putObject("payment").put("paymentModule", "moneyorder").put("paymentType", "MONEYORDER")
          .put("transactionType", "AUTHORIZECAPTURE").put("amount", "20.00");
      long orderId = request(HttpMethod.POST, "/api/v1/auth/cart/" + code + "/checkout", order, customerToken, 200).path("id").asLong();
      assertTrue(orderId > 0);
      JsonNode read = request(HttpMethod.GET, "/api/v1/auth/orders/" + orderId, null, customerToken, 200);
      assertEquals(orderId, read.path("id").asLong());
      assertEquals(customerId, read.path("customer").path("id").asLong());
      // Drain the two asynchronous registration notifications before restoring the transport.
      org.mockito.Mockito.verify(transport, org.mockito.Mockito.timeout(5000).atLeast(2))
          .send(org.mockito.ArgumentMatchers.any());
    } finally {
      ReflectionTestUtils.setField(emailService, "sender", sender);
      request(HttpMethod.DELETE, "/api/v1/private/product/" + productId, null, token, 200);
    }
  }

  @Test
  void legacyServicesStillAcceptValidBasicCredentialsAndRejectBadOnes() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    String path = "/services/private/system/optin";
    assertEquals(401, testRestTemplate.postForEntity(path, new HttpEntity<>("{}", headers), String.class).getStatusCode().value());
    headers.setBasicAuth("admin@shopizer.com", "incorrect");
    assertEquals(401, testRestTemplate.postForEntity(path, new HttpEntity<>("{}", headers), String.class).getStatusCode().value());
    headers.setBasicAuth("admin@shopizer.com", "password");
    ResponseEntity<String> accepted = testRestTemplate.postForEntity(path, new HttpEntity<>("{}", headers), String.class);
    assertEquals(200, accepted.getStatusCode().value());
    assertEquals(200, json.readTree(accepted.getBody()).path("status").asInt());
  }

  private String admin() throws Exception {
    String token = request(HttpMethod.POST, "/api/v1/private/login", json.createObjectNode()
        .put("username", "admin@shopizer.com").put("password", "password"), null, 200).path("token").asText();
    assertEquals(3, token.split("\\.").length);
    return token;
  }

  private ResponseEntity<String> exchange(HttpMethod method, String path, JsonNode body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) headers.setBearerAuth(token);
    return testRestTemplate.exchange(path, method, new HttpEntity<>(body == null ? null : body.toString(), headers), String.class);
  }

  private JsonNode request(HttpMethod method, String path, JsonNode body, String token, int status) throws Exception {
    ResponseEntity<String> response = exchange(method, path, body, token);
    assertEquals(status, response.getStatusCode().value(), () -> method + " " + path + ": " + response.getBody());
    return response.getBody() == null || response.getBody().isEmpty() ? json.nullNode() : json.readTree(response.getBody());
  }

  private void upload(String name, byte[] bytes, String token, int status) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    if (token != null) headers.setBearerAuth(token);
    HttpHeaders partHeaders = new HttpHeaders();
    partHeaders.setContentType(MediaType.IMAGE_PNG);
    ByteArrayResource resource = new ByteArrayResource(bytes) {
      @Override public String getFilename() { return name; }
    };
    var form = new LinkedMultiValueMap<String, Object>();
    form.add("file", new HttpEntity<>(resource, partHeaders));
    assertEquals(status, testRestTemplate.postForEntity("/api/v1/private/file", new HttpEntity<>(form, headers), String.class)
        .getStatusCode().value());
  }

  private void assertListed(String name, boolean expected) throws Exception {
    JsonNode folder = request(HttpMethod.GET, "/api/v1/content/images", null, null, 200);
    assertTrue(folder.path("content").isArray());
    boolean found = false;
    for (JsonNode entry : folder.path("content")) if (name.equals(entry.path("name").asText())) found = true;
    assertEquals(expected, found);
  }

  private void assertBytes(String path, byte[] bytes) {
    ResponseEntity<byte[]> response = testRestTemplate.getForEntity(path, byte[].class);
    assertEquals(200, response.getStatusCode().value());
    assertArrayEquals(bytes, response.getBody());
  }

  private String unique() { return "cms" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
