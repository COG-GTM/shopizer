package com.salesmanager.core.business.modules.email;


public interface HtmlEmailSender {
	
	void send(final Email email) throws Exception;

	void send(final Email email, EmailConfig emailConfig) throws Exception;

	void setEmailConfig(EmailConfig emailConfig);

}
