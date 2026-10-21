package com.salesmanager.test.shop.integration.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import com.salesmanager.core.business.modules.integration.payment.impl.PayPalExpressCheckoutPayment;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class PaymentModulesSdkIntegrationTest extends ServicesTestSupport {

  private static final String PAYPAL = "paypal-express-checkout";

  @Autowired
  @Qualifier("paymentModules")
  private Map<String, PaymentModule> paymentModules;

  @Autowired
  @Qualifier("sesEmailSender")
  private Object sesEmailSender;

  @Autowired
  private PaymentService paymentService;

  @Autowired
  private com.salesmanager.core.business.services.merchant.MerchantStoreService merchantStoreService;

  @Test
  public void securesAdminModuleListingAndExposesSdkModules() throws Exception {
    ResponseEntity<JsonNode> response = testRestTemplate.exchange(
        "/api/v1/private/modules/payment?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<String>(getHeader()), JsonNode.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody().toString().contains(PAYPAL));
    assertTrue(response.getBody().toString().contains("stripe"));
    assertTrue(response.getBody().toString().contains("braintree"));

    assertStatus(HttpHeaders.EMPTY, HttpStatus.UNAUTHORIZED);
    HttpHeaders badToken = new HttpHeaders();
    badToken.set("Authorization", "Bearer invalid-token");
    assertStatus(badToken, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  public void validatesAndPersistsPaypalStripeAndBraintreeCredentials() throws Exception {
    HttpHeaders admin = getHeader();
    JsonNode beforeRejected = getConfiguration(PAYPAL, admin);
    IntegrationModuleConfiguration legacy = configuration(PAYPAL,
        mapOf("username", "legacy-user", "password", "legacy-password",
            "signature", "legacy-signature"));
    ResponseEntity<String> rejected = testRestTemplate.postForEntity(
        "/api/v1/private/modules/payment?store=DEFAULT", new HttpEntity<>(legacy, admin), String.class);
    assertTrue(rejected.getStatusCode().is4xxClientError()
        || rejected.getStatusCode().is5xxServerError());
    JsonNode afterRejected = getConfiguration(PAYPAL, admin);
    JsonNode beforeKeys = beforeRejected.path("integrationKeys");
    JsonNode afterKeys = afterRejected.path("integrationKeys");
    assertEquals(beforeKeys.path("clientId"), afterKeys.path("clientId"));
    assertFalse(afterKeys.has("username"));
    assertFalse(afterKeys.has("api"));
    assertFalse(afterKeys.has("signature"));
    assertFalse(afterKeys.has("password"));

    IntegrationModuleConfiguration paypal = configuration(PAYPAL,
        mapOf("clientId", "test-client", "clientSecret", "test-secret"));
    assertPostAccepted(paypal, admin);
    JsonNode savedPaypal = getConfiguration(PAYPAL, admin);
    assertEquals("test-client", savedPaypal.path("integrationKeys").path("clientId").asText());

    assertPostAccepted(configuration("stripe3",
        mapOf("secretKey", "sk_test_value", "publishableKey", "pk_test_value")), admin);
    assertPostAccepted(configuration("braintree",
        mapOf("merchant_id", "merchant", "public_key", "public",
            "private_key", "private", "tokenization_key", "tokenization")), admin);
  }

  @Test
  public void wiresPaymentAndSesBeansAndPaypalCheckoutConfiguration() throws Exception {
    assertNotNull(paymentModules);
    assertTrue(paymentModules.get(PAYPAL) instanceof PayPalExpressCheckoutPayment);
    assertNotNull(sesEmailSender);
    MerchantStore store = merchantStoreService.getByCode("DEFAULT");
    IntegrationModule module = paymentService.getPaymentMethodByCode(store, PAYPAL);
    assertNotNull(module);
    JsonNode configurations = new ObjectMapper().readTree(module.getConfiguration());
    assertEquals("https://www.sandbox.paypal.com/checkoutnow?token=",
        findConfiguration(configurations, "TEST").path("config1").asText());
  }

  private void assertStatus(HttpHeaders headers, HttpStatus expectedStatus) {
    ResponseEntity<String> response = testRestTemplate.exchange(
        "/api/v1/private/modules/payment?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<String>(headers), String.class);
    assertEquals(expectedStatus, response.getStatusCode());
  }

  private void assertPostAccepted(IntegrationModuleConfiguration configuration, HttpHeaders headers) {
    ResponseEntity<String> response = testRestTemplate.postForEntity(
        "/api/v1/private/modules/payment?store=DEFAULT",
        new HttpEntity<>(configuration, headers), String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  private JsonNode getConfiguration(String code, HttpHeaders headers) throws Exception {
    ResponseEntity<String> response = testRestTemplate.exchange(
        "/api/v1/private/modules/payment/" + code + "?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<String>(headers), String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    return new ObjectMapper().readTree(response.getBody());
  }

  private IntegrationModuleConfiguration configuration(String code, Map<String, String> keys) {
    IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
    configuration.setCode(code);
    configuration.setActive(true);
    configuration.setIntegrationKeys(keys);
    return configuration;
  }

  private Map<String, String> mapOf(String... values) {
    Map<String, String> map = new HashMap<String, String>();
    for (int index = 0; index < values.length; index += 2) {
      map.put(values[index], values[index + 1]);
    }
    return map;
  }

  private JsonNode findConfiguration(JsonNode configurations, String environment) {
    for (JsonNode configuration : configurations) {
      if (environment.equals(configuration.path("env").asText())) {
        return configuration;
      }
    }
    throw new AssertionError("Missing " + environment + " PayPal configuration");
  }
}
