package com.salesmanager.test.business.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.EmailConfig;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.business.services.system.EmailServiceImpl;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;

@ExtendWith(MockitoExtension.class)
public class EmailServiceImplTest {

	private static final String EMAIL_CONFIG_JSON = "{\"protocol\":\"smtp\",\"host\":\"smtp.store.example\",\"port\":\"2525\","
			+ "\"smtpAuth\":true,\"starttls\":false,\"username\":\"u\",\"password\":\"p\"}";

	@Mock
	private MerchantConfigurationService merchantConfigurationService;

	@Mock
	private HtmlEmailSender sender;

	@InjectMocks
	private EmailServiceImpl emailService;

	private final MerchantStore store = new MerchantStore(1, "DEFAULT", "Default");

	private MerchantConfiguration row(MerchantConfigurationType type, String value) {
		MerchantConfiguration configuration = new MerchantConfiguration();
		configuration.setId(5L);
		configuration.setKey(Constants.EMAIL_CONFIG);
		configuration.setMerchantStore(store);
		configuration.setMerchantConfigurationType(type);
		configuration.setValue(value);
		return configuration;
	}

	@Test
	public void sendHtmlEmailPassesStoreConfigPerCallWithoutMutatingSender() throws Exception {
		when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
				.thenReturn(row(MerchantConfigurationType.INTEGRATION, EMAIL_CONFIG_JSON));
		Email email = new Email();

		emailService.sendHtmlEmail(store, email);

		ArgumentCaptor<EmailConfig> captor = ArgumentCaptor.forClass(EmailConfig.class);
		verify(sender).send(same(email), captor.capture());
		assertEquals("smtp.store.example", captor.getValue().getHost());
		assertEquals("2525", captor.getValue().getPort());
		verify(sender, never()).setEmailConfig(any());
		verify(sender, never()).send(any(Email.class));
	}

	@Test
	public void sendHtmlEmailWithoutStoreConfigPassesNull() throws Exception {
		Email email = new Email();

		emailService.sendHtmlEmail(store, email);

		verify(sender).send(same(email), isNull());
		verify(sender, never()).setEmailConfig(any());
	}

	@Test
	public void getEmailConfigurationIgnoresSocialRow() throws Exception {
		when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
				.thenReturn(row(MerchantConfigurationType.SOCIAL, EMAIL_CONFIG_JSON));

		assertNull(emailService.getEmailConfiguration(store));
	}

	@Test
	public void getEmailConfigurationReadsIntegrationRow() throws Exception {
		when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
				.thenReturn(row(MerchantConfigurationType.INTEGRATION, EMAIL_CONFIG_JSON));

		EmailConfig config = emailService.getEmailConfiguration(store);

		assertNotNull(config);
		assertEquals("smtp", config.getProtocol());
		assertEquals("smtp.store.example", config.getHost());
	}

	@Test
	public void getEmailConfigurationRejectsInvalidJson() throws Exception {
		when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store))
				.thenReturn(row(MerchantConfigurationType.INTEGRATION, "not-json"));

		assertThrows(ServiceException.class, () -> emailService.getEmailConfiguration(store));
	}

	@Test
	public void saveEmailConfigurationCreatesIntegrationRow() throws Exception {
		EmailConfig config = new EmailConfig();
		config.setHost("smtp.store.example");
		config.setPort("25");

		emailService.saveEmailConfiguration(config, store);

		ArgumentCaptor<MerchantConfiguration> captor = ArgumentCaptor.forClass(MerchantConfiguration.class);
		verify(merchantConfigurationService).saveOrUpdate(captor.capture());
		MerchantConfiguration saved = captor.getValue();
		assertEquals(Constants.EMAIL_CONFIG, saved.getKey());
		assertSame(store, saved.getMerchantStore());
		assertEquals(MerchantConfigurationType.INTEGRATION, saved.getMerchantConfigurationType());
		assertEquals(config.toJSONString(), saved.getValue());
	}

	@Test
	public void saveEmailConfigurationRetagsExistingSocialRowAsIntegration() throws Exception {
		MerchantConfiguration existing = row(MerchantConfigurationType.SOCIAL, "{}");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store)).thenReturn(existing);
		EmailConfig config = new EmailConfig();
		config.setHost("smtp.store.example");
		config.setPort("25");

		emailService.saveEmailConfiguration(config, store);

		verify(merchantConfigurationService).saveOrUpdate(existing);
		assertEquals(MerchantConfigurationType.INTEGRATION, existing.getMerchantConfigurationType());
		assertEquals(Long.valueOf(5L), existing.getId());
	}
}
