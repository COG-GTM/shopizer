package com.salesmanager.core.business.services.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.EmailConfig;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;

@RunWith(MockitoJUnitRunner.class)
public class EmailServiceImplConfigTypeTest {

  @Mock
  private MerchantConfigurationService merchantConfigurationService;

  @Mock
  private HtmlEmailSender sender;

  private EmailServiceImpl emailService;
  private MerchantStore store;

  @Before
  public void setUp() {
    emailService = new EmailServiceImpl();
    org.springframework.test.util.ReflectionTestUtils.setField(
        emailService, "merchantConfigurationService", merchantConfigurationService);
    org.springframework.test.util.ReflectionTestUtils.setField(emailService, "sender", sender);
    store = new MerchantStore();
    store.setCode("SHOP-A");
  }

  @Test
  public void ignoresEmailConfigRowsOfEveryNonIntegrationType() throws Exception {
    Email email = new Email();
    for (MerchantConfigurationType type : MerchantConfigurationType.values()) {
      if (type == MerchantConfigurationType.INTEGRATION) {
        continue;
      }
      when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
          .thenReturn(row(type, "{\"host\":\"mail.example.test\",\"port\":\"2525\"}"));

      assertNull(emailService.getEmailConfiguration(store));
      emailService.sendHtmlEmail(store, email);

      verify(sender).send(eq(email), isNull());
      clearInvocations(sender);
    }
  }

  @Test
  public void readsAllEmailFieldsFromIntegrationRow() throws Exception {
    EmailConfig saved = new EmailConfig();
    saved.setHost("mail.example.test");
    saved.setPort("2525");
    saved.setProtocol("smtps");
    saved.setUsername("store-user");
    saved.setPassword("store-password");
    saved.setSmtpAuth(true);
    saved.setStarttls(true);
    when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
        .thenReturn(row(MerchantConfigurationType.INTEGRATION, saved.toJSONString()));

    EmailConfig result = emailService.getEmailConfiguration(store);

    assertEquals("mail.example.test", result.getHost());
    assertEquals("2525", result.getPort());
    assertEquals("smtps", result.getProtocol());
    assertEquals("store-user", result.getUsername());
    assertEquals("store-password", result.getPassword());
    assertTrue(result.isSmtpAuth());
    assertTrue(result.isStarttls());
  }

  @Test
  public void returnsNullWhenNoEmailConfigurationExists() throws Exception {
    when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
        .thenReturn(null);

    assertNull(emailService.getEmailConfiguration(store));
  }

  @Test
  public void malformedIntegrationValueOmitsSecretFromServiceException() throws Exception {
    when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
        .thenReturn(row(MerchantConfigurationType.INTEGRATION, "{\"password\":\"s3cr3t\", broken"));

    ServiceException exception = org.junit.Assert.assertThrows(
        ServiceException.class, () -> emailService.getEmailConfiguration(store));

    assertNull(exception.getMessage());
    assertFalse(exception.getMessageCode().contains("s3cr3t"));
    assertTrue(exception.getMessageCode().contains("SHOP-A"));
  }

  @Test
  public void upgradesExistingSocialRowWhenSavingEmailConfiguration() throws Exception {
    MerchantConfiguration social = row(MerchantConfigurationType.SOCIAL, "{}");
    when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
        .thenReturn(social);
    EmailConfig emailConfig = new EmailConfig();
    emailConfig.setHost("mail.example.test");
    emailConfig.setPort("2525");

    emailService.saveEmailConfiguration(emailConfig, store);

    ArgumentCaptor<MerchantConfiguration> captor = ArgumentCaptor.forClass(MerchantConfiguration.class);
    verify(merchantConfigurationService).saveOrUpdate(captor.capture());
    assertEquals(MerchantConfigurationType.INTEGRATION, captor.getValue().getMerchantConfigurationType());
    assertEquals("mail.example.test", new com.fasterxml.jackson.databind.ObjectMapper()
        .readValue(captor.getValue().getValue(), EmailConfig.class).getHost());
  }

  private MerchantConfiguration row(MerchantConfigurationType type, String value) {
    MerchantConfiguration configuration = new MerchantConfiguration();
    configuration.setKey(Constants.EMAIL_CONFIG);
    configuration.setMerchantStore(store);
    configuration.setMerchantConfigurationType(type);
    configuration.setValue(value);
    return configuration;
  }
}
