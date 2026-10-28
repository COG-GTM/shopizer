package com.salesmanager.test.shop.integration.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Qualifier;
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
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import com.salesmanager.test.shop.common.SmtpStub;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StoreBrandingEmailIsolationIntegrationTest extends ServicesTestSupport {

  private static final String FACEBOOK_KEY = "facebook_page_url";
  private static final String GOOGLE_API_KEY = "google_api_key";
  private static final String EMAIL_CONFIG = Constants.EMAIL_CONFIG;
  private static final String CONFIG = "CONFIG";
  private static final String SHIPPING_CONFIG = "SHIPPING_CONFIG";
  private static final String STORE_CODE = "DEFAULT";

  @Inject
  private MerchantStoreService merchantStoreService;

  @Inject
  private MerchantConfigurationService merchantConfigurationService;

  @Inject
  private EmailService emailService;

  @Inject
  @Qualifier("mailSender")
  private JavaMailSenderImpl mailSender;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, ConfigurationState> originalConfigurations = new LinkedHashMap<>();
  private String originalProtocol;
  private String originalHost;
  private int originalPort;
  private String originalUsername;
  private String originalPassword;
  private Properties originalJavaMailProperties;
  private SmtpStub platformSmtp;
  private SmtpStub attackerSmtp;
  private SmtpStub storeSmtp;

  @Before
  public void setUp() throws Exception {
    MerchantStore store = merchantStoreService.getByCode(STORE_CODE);
    for (String key : Arrays.asList(EMAIL_CONFIG, CONFIG, SHIPPING_CONFIG, FACEBOOK_KEY, GOOGLE_API_KEY)) {
      originalConfigurations.put(key, ConfigurationState.copy(
          merchantConfigurationService.getMerchantConfiguration(key, store)));
    }

    originalProtocol = mailSender.getProtocol();
    originalHost = mailSender.getHost();
    originalPort = mailSender.getPort();
    originalUsername = mailSender.getUsername();
    originalPassword = mailSender.getPassword();
    originalJavaMailProperties = new Properties();
    originalJavaMailProperties.putAll(mailSender.getJavaMailProperties());
  }

  @After
  public void tearDown() throws Exception {
    closeSmtpStubs();

    MerchantStore store = merchantStoreService.getByCode(STORE_CODE);
    for (Map.Entry<String, ConfigurationState> entry : originalConfigurations.entrySet()) {
      MerchantConfiguration current = merchantConfigurationService.getMerchantConfiguration(entry.getKey(), store);
      if (current != null) {
        merchantConfigurationService.delete(current);
      }
      ConfigurationState original = entry.getValue();
      if (original != null) {
        merchantConfigurationService.saveOrUpdate(original.restore(store));
      }
    }
    originalConfigurations.clear();

    mailSender.setProtocol(originalProtocol);
    mailSender.setHost(originalHost);
    mailSender.setPort(originalPort);
    mailSender.setUsername(originalUsername);
    mailSender.setPassword(originalPassword);
    mailSender.setJavaMailProperties(originalJavaMailProperties);
  }

  @Test
  public void brandingRejectsReservedConfigurationKeysWithoutPersistingEmailConfig() throws Exception {
    MerchantStore store = merchantStoreService.getByCode(STORE_CODE);
    MerchantConfiguration existing = merchantConfigurationService.getMerchantConfiguration(EMAIL_CONFIG, store);
    if (existing != null) {
      merchantConfigurationService.delete(existing);
    }

    boolean allRejected = true;
    for (String key : Arrays.asList(EMAIL_CONFIG, CONFIG, SHIPPING_CONFIG)) {
      String value = key.equals(EMAIL_CONFIG) ? emailConfig("smtp.attacker.test", "2525").toJSONString() : "reserved";
      ResponseEntity<String> response = postBranding(brandingPayload(key, value));
      System.out.printf("C1 %s status=%s body=%s%n", key, response.getStatusCode(), response.getBody());
      allRejected &= !response.getStatusCode().is2xxSuccessful();
    }

    assertTrue("Expected rejection of EMAIL_CONFIG, CONFIG, and SHIPPING_CONFIG", allRejected);
    assertNull(merchantConfigurationService.getMerchantConfiguration(EMAIL_CONFIG, store));
    assertNull(emailService.getEmailConfiguration(store));
  }

  @Test
  public void brandingSavesAndDeletesSocialLink() throws Exception {
    ResponseEntity<String> saved = postBranding(brandingPayload(FACEBOOK_KEY, "https://facebook.test/shop"));
    assertEquals(201, saved.getStatusCodeValue());

    JsonNode savedNetwork = findNetwork(getBranding(), FACEBOOK_KEY);
    assertNotNull(savedNetwork);
    assertEquals("SOCIAL", savedNetwork.path("type").asText());
    assertEquals("https://facebook.test/shop", savedNetwork.path("value").asText());

    ResponseEntity<String> deleted = postBranding(brandingPayload(FACEBOOK_KEY, " "));
    assertEquals(201, deleted.getStatusCodeValue());
    assertNull(findNetwork(getBranding(), FACEBOOK_KEY));
  }

  @Test
  public void mixedBatchIsRejectedWithoutSavingAllowedLink() throws Exception {
    ObjectNode payload = objectMapper.createObjectNode();
    ArrayNode networks = payload.putArray("socialNetworks");
    networks.add(network(FACEBOOK_KEY, "https://facebook.test/shop"));
    networks.add(network(EMAIL_CONFIG, emailConfig("smtp.attacker.test", "2525").toJSONString()));

    ResponseEntity<String> response = postBranding(objectMapper.writeValueAsString(payload));

    assertFalse(response.getStatusCode().is2xxSuccessful());
    assertNull(findNetwork(getBranding(), FACEBOOK_KEY));
  }

  @Test
  public void brandingCannotOverwriteIntegrationConfiguration() throws Exception {
    MerchantStore store = merchantStoreService.getByCode(STORE_CODE);
    MerchantConfiguration integration = configuration(
        store, GOOGLE_API_KEY, MerchantConfigurationType.INTEGRATION, "keep");
    merchantConfigurationService.saveOrUpdate(integration);

    ResponseEntity<String> response = postBranding(brandingPayload(GOOGLE_API_KEY, "override"));

    assertFalse(response.getStatusCode().is2xxSuccessful());
    MerchantConfiguration reloaded = merchantConfigurationService.getMerchantConfiguration(GOOGLE_API_KEY, store);
    assertNotNull(reloaded);
    assertEquals("keep", reloaded.getValue());
    assertEquals(MerchantConfigurationType.INTEGRATION, reloaded.getMerchantConfigurationType());
  }

  @Test
  public void emailConfigurationPersistsOnlyAsIntegration() throws Exception {
    MerchantStore store = merchantStoreService.getByCode(STORE_CODE);
    EmailConfig saved = emailConfig("smtp.store.test", "2525");

    emailService.saveEmailConfiguration(saved, store);

    MerchantConfiguration reloaded = merchantConfigurationService.getMerchantConfiguration(EMAIL_CONFIG, store);
    assertNotNull(reloaded);
    assertEquals(MerchantConfigurationType.INTEGRATION, reloaded.getMerchantConfigurationType());
    EmailConfig loaded = emailService.getEmailConfiguration(store);
    assertEquals("smtp.store.test", loaded.getHost());
    assertEquals("2525", loaded.getPort());

    merchantConfigurationService.delete(reloaded);
    merchantConfigurationService.saveOrUpdate(
        configuration(store, EMAIL_CONFIG, MerchantConfigurationType.SOCIAL, saved.toJSONString()));
    assertNull(emailService.getEmailConfiguration(store));
  }

  @Test
  public void passwordResetUsesPlatformOrStoreSenderWithoutMutatingSharedSender() throws Exception {
    MerchantStore store = merchantStoreService.getByCode(STORE_CODE);
    platformSmtp = new SmtpStub();
    attackerSmtp = new SmtpStub();
    storeSmtp = new SmtpStub();
    mailSender.setProtocol("smtp");
    mailSender.setHost("127.0.0.1");
    mailSender.setPort(platformSmtp.port());
    mailSender.setUsername(null);
    mailSender.setPassword(null);
    mailSender.setJavaMailProperties(new Properties());

    merchantConfigurationService.saveOrUpdate(configuration(
        store,
        EMAIL_CONFIG,
        MerchantConfigurationType.SOCIAL,
        emailConfig("127.0.0.1", String.valueOf(attackerSmtp.port())).toJSONString()));

    // SMTPTransport reaches RCPT TO before the incompatible mail API fails writing the body.
    assertEquals(500, requestPasswordReset().getStatusCodeValue());
    awaitRecipients(platformSmtp, 1);
    assertEquals(1, platformSmtp.recipients().size());
    assertTrue(platformSmtp.recipients().get(0).contains("admin@shopizer.com"));
    assertEquals(0, attackerSmtp.connectionCount());
    assertEquals(0, attackerSmtp.recipients().size());

    EmailConfig storeConfig = emailConfig("127.0.0.1", String.valueOf(storeSmtp.port()));
    emailService.saveEmailConfiguration(storeConfig, store);
    assertEquals(500, requestPasswordReset().getStatusCodeValue());
    awaitRecipients(storeSmtp, 1);
    assertEquals(1, storeSmtp.recipients().size());
    assertEquals(1, platformSmtp.recipients().size());
    assertTrue(storeSmtp.recipients().get(0).contains("admin@shopizer.com"));

    MerchantConfiguration storeEmailConfig = merchantConfigurationService.getMerchantConfiguration(EMAIL_CONFIG, store);
    merchantConfigurationService.delete(storeEmailConfig);
    assertEquals(500, requestPasswordReset().getStatusCodeValue());
    awaitRecipients(platformSmtp, 2);
    assertEquals(2, platformSmtp.recipients().size());
    assertTrue(platformSmtp.recipients().get(1).contains("admin@shopizer.com"));
    assertEquals(0, attackerSmtp.connectionCount());
    assertEquals(0, attackerSmtp.recipients().size());

    assertEquals("127.0.0.1", mailSender.getHost());
    assertEquals(platformSmtp.port(), mailSender.getPort());
    assertNull(mailSender.getUsername());
    assertNull(mailSender.getPassword());
  }

  private ResponseEntity<String> postBranding(String json) {
    return testRestTemplate.postForEntity(
        "/api/v1/private/store/" + STORE_CODE + "/marketing",
        new HttpEntity<>(json, getHeader()),
        String.class);
  }

  private JsonNode getBranding() {
    return testRestTemplate.exchange(
        "/api/v1/private/store/" + STORE_CODE + "/marketing",
        HttpMethod.GET,
        new HttpEntity<>(getHeader()),
        JsonNode.class).getBody();
  }

  private ResponseEntity<String> requestPasswordReset() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return testRestTemplate.postForEntity(
        "/api/v1/user/password/reset/request?store=" + STORE_CODE,
        new HttpEntity<>("{\"username\":\"admin@shopizer.com\",\"returnUrl\":\"http://localhost:8080\"}", headers),
        String.class);
  }

  private String brandingPayload(String key, String value) throws Exception {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.putArray("socialNetworks").add(network(key, value));
    return objectMapper.writeValueAsString(payload);
  }

  private ObjectNode network(String key, String value) {
    ObjectNode network = objectMapper.createObjectNode();
    network.put("key", key);
    network.put("value", value);
    network.put("active", true);
    return network;
  }

  private JsonNode findNetwork(JsonNode branding, String key) {
    if (branding == null || !branding.path("socialNetworks").isArray()) {
      return null;
    }
    for (JsonNode network : branding.path("socialNetworks")) {
      if (key.equals(network.path("key").asText())) {
        return network;
      }
    }
    return null;
  }

  private EmailConfig emailConfig(String host, String port) {
    EmailConfig config = new EmailConfig();
    config.setHost(host);
    config.setPort(port);
    config.setProtocol("smtp");
    return config;
  }

  private MerchantConfiguration configuration(
      MerchantStore store, String key, MerchantConfigurationType type, String value) {
    MerchantConfiguration configuration = new MerchantConfiguration();
    configuration.setMerchantStore(store);
    configuration.setKey(key);
    configuration.setMerchantConfigurationType(type);
    configuration.setValue(value);
    configuration.setActive(true);
    return configuration;
  }

  private void awaitRecipients(SmtpStub stub, int count) throws InterruptedException {
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (stub.recipients().size() < count && System.nanoTime() < deadline) {
      Thread.sleep(25);
    }
    assertEquals(count, stub.recipients().size());
  }

  private void closeSmtpStubs() throws Exception {
    for (SmtpStub stub : Arrays.asList(platformSmtp, attackerSmtp, storeSmtp)) {
      if (stub != null) {
        stub.close();
      }
    }
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
