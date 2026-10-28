package com.salesmanager.core.business.services.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.EmailConfig;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;

@RunWith(MockitoJUnitRunner.class)
public class EmailServiceImplTest {

  private static final String SMTP_JSON = "{\"host\":\"smtp.attacker.test\",\"port\":\"2525\"}";

  @Mock
  private MerchantConfigurationService merchantConfigurationService;

  @Mock
  private HtmlEmailSender sender;

  @InjectMocks
  private EmailServiceImpl emailService;

  private MerchantStore store;

  @Before
  public void setUp() {
    store = new MerchantStore();
    store.setCode("MALLORY");
  }

  @Test
  public void socialRowNamedEmailConfigIsIgnored() throws Exception {
    when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
        .thenReturn(row(MerchantConfigurationType.SOCIAL));

    assertNull(emailService.getEmailConfiguration(store));

    Email email = new Email();
    emailService.sendHtmlEmail(store, email);
    verify(sender).send(eq(email), isNull());
  }

  @Test
  public void emailConfigRowIsPassedPerSend() throws Exception {
    when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
        .thenReturn(row(MerchantConfigurationType.INTEGRATION));

    Email email = new Email();
    emailService.sendHtmlEmail(store, email);

    ArgumentCaptor<EmailConfig> captor = ArgumentCaptor.forClass(EmailConfig.class);
    verify(sender).send(eq(email), captor.capture());
    assertEquals("smtp.attacker.test", captor.getValue().getHost());
    assertEquals("2525", captor.getValue().getPort());
  }

  @Test
  public void saveEmailConfigurationWritesEmailConfigType() throws Exception {
    MerchantConfiguration social = row(MerchantConfigurationType.SOCIAL);
    when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
        .thenReturn(social);

    EmailConfig config = new EmailConfig();
    config.setHost("smtp.store.test");
    config.setPort("587");
    emailService.saveEmailConfiguration(config, store);

    ArgumentCaptor<MerchantConfiguration> captor = ArgumentCaptor.forClass(MerchantConfiguration.class);
    verify(merchantConfigurationService).saveOrUpdate(captor.capture());
    assertSame(social, captor.getValue());
    assertEquals(MerchantConfigurationType.INTEGRATION, captor.getValue().getMerchantConfigurationType());
    assertEquals(MerchantConfigurationType.INTEGRATION, EmailServiceImpl.EMAIL_CONFIG_TYPE);
  }

  private MerchantConfiguration row(MerchantConfigurationType type) {
    MerchantConfiguration configuration = new MerchantConfiguration();
    configuration.setKey(Constants.EMAIL_CONFIG);
    configuration.setMerchantStore(store);
    configuration.setMerchantConfigurationType(type);
    configuration.setValue(SMTP_JSON);
    return configuration;
  }
}
