package com.salesmanager.core.business.services.system;

import javax.inject.Inject;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.EmailConfig;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;

@Service("emailService")
public class EmailServiceImpl implements EmailService {

	/**
	 * Type written by {@link #saveEmailConfiguration}; rows of any other type (e.g. SOCIAL rows
	 * created through the store branding API) are never used as SMTP settings.
	 */
	static final MerchantConfigurationType EMAIL_CONFIG_TYPE = MerchantConfigurationType.INTEGRATION;

	@Inject
	private MerchantConfigurationService merchantConfigurationService;
	
	@Inject
	private HtmlEmailSender sender;
	
	@Override
	public void sendHtmlEmail(MerchantStore store, Email email) throws ServiceException, Exception {

		EmailConfig emailConfig = getEmailConfiguration(store);
		sender.send(email, emailConfig);
	}

	@Override
	public EmailConfig getEmailConfiguration(MerchantStore store) throws ServiceException {
		
		MerchantConfiguration configuration = merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store);
		EmailConfig emailConfig = null;
		if(configuration!=null && configuration.getMerchantConfigurationType() == EMAIL_CONFIG_TYPE) {
			String value = configuration.getValue();
			
			ObjectMapper mapper = new ObjectMapper();
			try {
				emailConfig = mapper.readValue(value, EmailConfig.class);
			} catch(Exception e) {
				throw new ServiceException("Cannot parse email configuration for store " + store.getCode());
			}
		}
		return emailConfig;
	}
	
	
	@Override
	public void saveEmailConfiguration(EmailConfig emailConfig, MerchantStore store) throws ServiceException {
		MerchantConfiguration configuration = merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store);
		if(configuration==null) {
			configuration = new MerchantConfiguration();
			configuration.setMerchantStore(store);
			configuration.setKey(Constants.EMAIL_CONFIG);
		}
		configuration.setMerchantConfigurationType(EMAIL_CONFIG_TYPE);
		
		String value = emailConfig.toJSONString();
		configuration.setValue(value);
		merchantConfigurationService.saveOrUpdate(configuration);
	}

}
