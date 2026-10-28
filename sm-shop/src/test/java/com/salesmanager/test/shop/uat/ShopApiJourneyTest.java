package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.modules.email.EmailConfig;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import com.salesmanager.test.shop.common.SmtpStub;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ShopApiJourneyTest extends ServicesTestSupport {

  private static final String STORE_CODE = "DEFAULT";
  private static final String FACEBOOK_KEY = "facebook_page_url";
  private static final String EMAIL_CONFIG = Constants.EMAIL_CONFIG;
  private static final String PAYMENT_CONFIG = Constants.PAYMENT_MODULES;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, ConfigurationState> originalConfigurations = new LinkedHashMap<>();

  @Inject
  private MerchantStoreService merchantStoreService;

  @Inject
  private MerchantConfigurationService merchantConfigurationService;

  @Inject
  private EmailService emailService;

  @Inject
  private PaymentService paymentService;

  @Inject
  @Qualifier("mailSender")
  private JavaMailSenderImpl mailSender;

  @Value("${jwt.secret}")
  private String jwtSecret;

  private String originalProtocol;
  private String originalHost;
  private int originalPort;
  private String originalUsername;
  private String originalPassword;
  private Properties originalMailProperties;
  private SmtpStub resetSmtp;

  @Before
  public void setUp() throws Exception {
    MerchantStore store = merchantStoreService.getByCode(STORE_CODE);
    for (String key : Arrays.asList(FACEBOOK_KEY, EMAIL_CONFIG, PAYMENT_CONFIG)) {
      originalConfigurations.put(
          key, ConfigurationState.copy(merchantConfigurationService.getMerchantConfiguration(key, store)));
    }

    originalProtocol = mailSender.getProtocol();
    originalHost = mailSender.getHost();
    originalPort = mailSender.getPort();
    originalUsername = mailSender.getUsername();
    originalPassword = mailSender.getPassword();
    originalMailProperties = new Properties();
    originalMailProperties.putAll(mailSender.getJavaMailProperties());
  }

  @After
  public void tearDown() throws Exception {
    if (resetSmtp != null) {
      resetSmtp.close();
      resetSmtp = null;
    }

    MerchantStore store = merchantStoreService.getByCode(STORE_CODE);
    for (Map.Entry<String, ConfigurationState> entry : originalConfigurations.entrySet()) {
      MerchantConfiguration current = merchantConfigurationService.getMerchantConfiguration(entry.getKey(), store);
      if (current != null) {
        merchantConfigurationService.delete(current);
      }
      if (entry.getValue() != null) {
        merchantConfigurationService.saveOrUpdate(entry.getValue().restore(store));
      }
    }
    originalConfigurations.clear();

    mailSender.setProtocol(originalProtocol);
    mailSender.setHost(originalHost);
    mailSender.setPort(originalPort);
    mailSender.setUsername(originalUsername);
    mailSender.setPassword(originalPassword);
    mailSender.setJavaMailProperties(originalMailProperties);
  }

  @Test
  public void adminLoginAndMarketingRequireValidJwt() {
    HttpHeaders adminHeaders = getHeader();
    ResponseEntity<JsonNode> marketing = testRestTemplate.exchange(
        "/api/v1/private/store/" + STORE_CODE + "/marketing",
        HttpMethod.GET,
        new HttpEntity<>(adminHeaders),
        JsonNode.class);
    assertEquals(200, marketing.getStatusCodeValue());

    ResponseEntity<String> missingToken = testRestTemplate.exchange(
        "/api/v1/private/store/" + STORE_CODE + "/marketing",
        HttpMethod.GET,
        HttpEntity.EMPTY,
        String.class);
    assertTrue(missingToken.getStatusCodeValue() == 401 || missingToken.getStatusCodeValue() == 403);

    HttpHeaders badHeaders = new HttpHeaders();
    long now = System.currentTimeMillis();
    String expiredToken = Jwts.builder()
        .setSubject("admin@shopizer.com")
        .setIssuedAt(new Date(now - 60_000L))
        .setExpiration(new Date(now - 1_000L))
        .signWith(SignatureAlgorithm.HS512, jwtSecret)
        .compact();
    badHeaders.setBearerAuth(expiredToken);
    ResponseEntity<String> invalidToken = testRestTemplate.exchange(
        "/api/v1/private/store/" + STORE_CODE + "/marketing",
        HttpMethod.GET,
        new HttpEntity<>(badHeaders),
        String.class);
    assertTrue(invalidToken.getStatusCodeValue() == 401 || invalidToken.getStatusCodeValue() == 403);
  }

  @Test
  public void customerCanRegisterLoginAndReadAuthenticatedProfile() {
    String email = uniqueEmail();
    PersistableCustomer customer = customer(email);
    ResponseEntity<AuthenticationResponse> registered = testRestTemplate.postForEntity(
        "/api/v1/customer/register?store=" + STORE_CODE,
        new HttpEntity<>(customer),
        AuthenticationResponse.class);
    assertEquals(200, registered.getStatusCodeValue());
    assertNotNull(registered.getBody());

    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
        AuthenticationResponse.class);
    assertEquals(200, login.getStatusCodeValue());
    assertNotNull(login.getBody());
    assertNotNull(login.getBody().getToken());

    HttpHeaders customerHeaders = new HttpHeaders();
    customerHeaders.setBearerAuth(login.getBody().getToken());
    ResponseEntity<JsonNode> profile = testRestTemplate.exchange(
        "/api/v1/auth/customer/profile?store=" + STORE_CODE,
        HttpMethod.GET,
        new HttpEntity<>(customerHeaders),
        JsonNode.class);
    assertEquals(200, profile.getStatusCodeValue());
    assertEquals(email, profile.getBody().path("emailAddress").asText());
  }

  @Test
  public void browseStoreCategoriesProductListAndProductDetail() throws Exception {
    String code = uniqueCode("uat-browse-");
    ResponseEntity<JsonNode> store = testRestTemplate.getForEntity(
        "/api/v1/store/" + STORE_CODE, JsonNode.class);
    assertEquals(200, store.getStatusCodeValue());

    ReadableProduct created = sampleProduct(code);
    assertNotNull(created);
    assertEquals(200, testRestTemplate.getForEntity("/api/v1/category", JsonNode.class).getStatusCodeValue());
    assertEquals(200, testRestTemplate.getForEntity("/api/v1/products", JsonNode.class).getStatusCodeValue());

    ResponseEntity<JsonNode> detail = testRestTemplate.getForEntity(
        "/api/v2/product/" + created.getSku(), JsonNode.class);
    assertEquals(200, detail.getStatusCodeValue());
  }

  @Test
  public void cartCanBeCreatedUpdatedAndReadBack() throws Exception {
    ReadableProduct product = sampleProduct(uniqueCode("uat-cart-"));
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(product.getSku());
    item.setQuantity(1);
    ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
        "/api/v1/cart?store=" + STORE_CODE,
        new HttpEntity<>(item, getHeader()),
        ReadableShoppingCart.class);
    assertEquals(201, created.getStatusCodeValue());
    assertNotNull(created.getBody());

    item.setQuantity(2);
    ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
        "/api/v1/cart/" + created.getBody().getCode() + "?store=" + STORE_CODE,
        HttpMethod.PUT,
        new HttpEntity<>(item, getHeader()),
        ReadableShoppingCart.class);
    assertEquals(201, updated.getStatusCodeValue());
    assertEquals(2, updated.getBody().getQuantity());

    ResponseEntity<ReadableShoppingCart> reloaded = testRestTemplate.getForEntity(
        "/api/v1/cart/" + created.getBody().getCode() + "?store=" + STORE_CODE,
        ReadableShoppingCart.class);
    assertEquals(200, reloaded.getStatusCodeValue());
    assertEquals(2, reloaded.getBody().getQuantity());
  }

  @Test
  public void authenticatedCustomerCanCheckoutAndReadOrder() throws Exception {
    String email = uniqueEmail();
    AuthenticationResponse auth = loginNewCustomer(email);
    HttpHeaders customerHeaders = new HttpHeaders();
    customerHeaders.setContentType(MediaType.APPLICATION_JSON);
    customerHeaders.setBearerAuth(auth.getToken());

    ReadableProduct product = sampleProduct(uniqueCode("uat-order-"));
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(product.getSku());
    item.setQuantity(1);
    ResponseEntity<ReadableShoppingCart> cartResponse = testRestTemplate.postForEntity(
        "/api/v1/cart?store=" + STORE_CODE,
        new HttpEntity<>(item, customerHeaders),
        ReadableShoppingCart.class);
    assertEquals(201, cartResponse.getStatusCodeValue());
    assertNotNull(cartResponse.getBody().getTotal());

    PersistablePayment payment = new PersistablePayment();
    payment.setPaymentModule("moneyorder");
    payment.setPaymentType("MONEYORDER");
    payment.setTransactionType("AUTHORIZECAPTURE");
    payment.setAmount(cartResponse.getBody().getTotal().toPlainString());
    MerchantStore store = merchantStoreService.getByCode(STORE_CODE);
    IntegrationConfiguration moneyOrder = new IntegrationConfiguration();
    moneyOrder.setModuleCode("moneyorder");
    moneyOrder.setActive(true);
    moneyOrder.setDefaultSelected(true);
    moneyOrder.getIntegrationKeys().put("address", "Shopizer UAT payment test");
    moneyOrder.getIntegrationKeys().put("transaction", "AUTHORIZECAPTURE");
    paymentService.savePaymentModuleConfiguration(moneyOrder, store);

    PersistableOrder order = new PersistableOrder();
    order.setCurrency(fetchStore().getCurrency());
    order.setPayment(payment);

    ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
        "/api/v1/auth/cart/" + cartResponse.getBody().getCode() + "/checkout?store=" + STORE_CODE,
        new HttpEntity<>(order, customerHeaders),
        ReadableOrderConfirmation.class);
    assertEquals("checkout response body: " + checkout.getBody(), 200, checkout.getStatusCodeValue());
    assertNotNull(checkout.getBody());
    assertNotNull(checkout.getBody().getId());

    ResponseEntity<JsonNode> reloaded = testRestTemplate.exchange(
        "/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=" + STORE_CODE,
        HttpMethod.GET,
        new HttpEntity<>(customerHeaders),
        JsonNode.class);
    assertEquals(200, reloaded.getStatusCodeValue());
    assertEquals(checkout.getBody().getId().longValue(), reloaded.getBody().path("id").asLong());
  }

  @Test
  public void privateLegacyServicesRequireBasicAuthentication() {
    String body = "{\"code\":\"uat-optin\"}";
    HttpHeaders validHeaders = new HttpHeaders();
    validHeaders.setBasicAuth("admin@shopizer.com", "password");
    validHeaders.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> valid = testRestTemplate.postForEntity(
        "/services/private/system/optin",
        new HttpEntity<>(body, validHeaders),
        JsonNode.class);
    assertEquals(200, valid.getStatusCodeValue());

    HttpHeaders invalidHeaders = new HttpHeaders();
    invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
    invalidHeaders.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> invalid = testRestTemplate.postForEntity(
        "/services/private/system/optin",
        new HttpEntity<>(body, invalidHeaders),
        String.class);
    assertTrue(invalid.getStatusCodeValue() == 401 || invalid.getStatusCodeValue() == 403);
  }

  @Test
  public void actuatorHealthAndOpenApiAreAvailable() {
    ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
    assertEquals(200, health.getStatusCodeValue());
    assertEquals("UP", health.getBody().path("status").asText());

    ResponseEntity<JsonNode> openApi = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
    assertEquals(200, openApi.getStatusCodeValue());
  }

  @Test
  public void brandingCanSaveSocialConfigRejectEmailConfigAndRequestPasswordReset() throws Exception {
    MerchantStore store = merchantStoreService.getByCode(STORE_CODE);
    MerchantConfiguration existingEmailConfig =
        merchantConfigurationService.getMerchantConfiguration(EMAIL_CONFIG, store);
    if (existingEmailConfig != null) {
      merchantConfigurationService.delete(existingEmailConfig);
    }

    HttpHeaders adminHeaders = getHeader();
    ObjectNode socialPayload = brandingPayload(FACEBOOK_KEY, "https://facebook.test/uat");
    ResponseEntity<String> saved = testRestTemplate.postForEntity(
        "/api/v1/private/store/" + STORE_CODE + "/marketing",
        new HttpEntity<>(socialPayload, adminHeaders),
        String.class);
    assertEquals(201, saved.getStatusCodeValue());

    ResponseEntity<JsonNode> marketing = testRestTemplate.exchange(
        "/api/v1/private/store/" + STORE_CODE + "/marketing",
        HttpMethod.GET,
        new HttpEntity<>(adminHeaders),
        JsonNode.class);
    assertTrue(containsNetwork(marketing.getBody(), FACEBOOK_KEY));

    ObjectNode emailPayload = brandingPayload(
        EMAIL_CONFIG, emailConfig("smtp.attacker.test", "2525").toJSONString());
    ResponseEntity<String> rejected = testRestTemplate.postForEntity(
        "/api/v1/private/store/" + STORE_CODE + "/marketing",
        new HttpEntity<>(emailPayload, adminHeaders),
        String.class);
    assertFalse(rejected.getStatusCode().is2xxSuccessful());
    assertTrue(merchantConfigurationService.getMerchantConfiguration(EMAIL_CONFIG, store) == null);
    assertTrue(emailService.getEmailConfiguration(store) == null);

    ResponseEntity<String> deleted = testRestTemplate.postForEntity(
        "/api/v1/private/store/" + STORE_CODE + "/marketing",
        new HttpEntity<>(brandingPayload(FACEBOOK_KEY, ""), adminHeaders),
        String.class);
    assertEquals(201, deleted.getStatusCodeValue());

    resetSmtp = new SmtpStub();
    mailSender.setProtocol("smtp");
    mailSender.setHost("127.0.0.1");
    mailSender.setPort(resetSmtp.port());
    mailSender.setUsername(null);
    mailSender.setPassword(null);
    mailSender.setJavaMailProperties(new Properties());

    HttpHeaders resetHeaders = new HttpHeaders();
    resetHeaders.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> passwordReset = testRestTemplate.postForEntity(
        "/api/v1/user/password/reset/request?store=" + STORE_CODE,
        new HttpEntity<>(
            "{\"username\":\"admin@shopizer.com\",\"returnUrl\":\"http://localhost:8080\"}",
            resetHeaders),
        String.class);
    assertEquals(500, passwordReset.getStatusCodeValue());
    awaitRecipients(resetSmtp, 1);
    assertTrue(resetSmtp.recipients().get(0).contains("admin@shopizer.com"));
  }

  private AuthenticationResponse loginNewCustomer(String email) {
    ResponseEntity<AuthenticationResponse> registered = testRestTemplate.postForEntity(
        "/api/v1/customer/register?store=" + STORE_CODE,
        new HttpEntity<>(customer(email)),
        AuthenticationResponse.class);
    assertEquals(200, registered.getStatusCodeValue());
    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
        AuthenticationResponse.class);
    assertEquals(200, login.getStatusCodeValue());
    assertNotNull(login.getBody());
    return login.getBody();
  }

  private PersistableCustomer customer(String email) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    customer.setStoreCode(STORE_CODE);
    Address billing = new Address();
    billing.setFirstName("Test");
    billing.setLastName("Customer");
    billing.setCountry("BE");
    customer.setBilling(billing);
    return customer;
  }

  private String uniqueEmail() {
    return uniqueCode("uat-").replace('-', '.') + "@example.test";
  }

  private String uniqueCode(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private ObjectNode brandingPayload(String key, String value) {
    ObjectNode payload = objectMapper.createObjectNode();
    ArrayNode networks = payload.putArray("socialNetworks");
    ObjectNode network = networks.addObject();
    network.put("key", key);
    network.put("value", value);
    network.put("active", true);
    return payload;
  }

  private boolean containsNetwork(JsonNode branding, String key) {
    for (JsonNode network : branding.path("socialNetworks")) {
      if (key.equals(network.path("key").asText())) {
        return true;
      }
    }
    return false;
  }

  private void awaitRecipients(SmtpStub stub, int count) throws InterruptedException {
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (stub.recipients().size() < count && System.nanoTime() < deadline) {
      Thread.sleep(25);
    }
    assertEquals(count, stub.recipients().size());
  }

  private EmailConfig emailConfig(String host, String port) {
    EmailConfig config = new EmailConfig();
    config.setHost(host);
    config.setPort(port);
    config.setProtocol("smtp");
    return config;
  }

  private static final class ConfigurationState {
    private final Long id;
    private final String key;
    private final MerchantConfigurationType type;
    private final String value;
    private final Boolean active;

    private ConfigurationState(MerchantConfiguration configuration) {
      id = configuration.getId();
      key = configuration.getKey();
      type = configuration.getMerchantConfigurationType();
      value = configuration.getValue();
      active = configuration.getActive();
    }

    private static ConfigurationState copy(MerchantConfiguration configuration) {
      return configuration == null ? null : new ConfigurationState(configuration);
    }

    private MerchantConfiguration restore(MerchantStore store) {
      MerchantConfiguration configuration = new MerchantConfiguration();
      configuration.setId(id);
      configuration.setMerchantStore(store);
      configuration.setKey(key);
      configuration.setMerchantConfigurationType(type);
      configuration.setValue(value);
      configuration.setActive(active);
      return configuration;
    }
  }
}
