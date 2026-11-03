package com.salesmanager.test.business.system;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;

import com.salesmanager.core.business.modules.email.DefaultEmailSenderImpl;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.EmailConfig;

import freemarker.template.Configuration;

public class DefaultEmailSenderImplTest {

	private JavaMailSenderImpl sharedMailSender;
	private DefaultEmailSenderImpl sender;

	@BeforeEach
	public void setUp() {
		sharedMailSender = mock(JavaMailSenderImpl.class);
		sender = new DefaultEmailSenderImpl();
		sender.setMailSender(sharedMailSender);
		sender.setFreemarkerMailConfiguration(mock(Configuration.class));
	}

	private Email email() {
		Email email = new Email();
		email.setFrom("Store");
		email.setFromEmail("store@example.com");
		email.setTo("customer@example.com");
		email.setSubject("Subject");
		email.setTemplateName("template.ftl");
		return email;
	}

	@Test
	public void storeEmailConfigDoesNotTouchSharedMailSender() {
		EmailConfig config = new EmailConfig();
		config.setProtocol("smtp");
		config.setHost("127.0.0.1");
		config.setPort("1");

		assertThrows(Exception.class, () -> sender.send(email(), config));

		verifyNoInteractions(sharedMailSender);
	}

	@Test
	public void noEmailConfigUsesSharedMailSender() throws Exception {
		sender.send(email(), null);

		verify(sharedMailSender).send(any(MimeMessagePreparator.class));
	}
}
