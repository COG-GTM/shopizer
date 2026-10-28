package com.salesmanager.core.business.modules.email;


public interface HtmlEmailSender {
	
	void send(final Email email) throws Exception;

	/**
	 * Sends using the given per-store configuration, or the platform default when null.
	 */
	void send(final Email email, EmailConfig emailConfig) throws Exception;

}
