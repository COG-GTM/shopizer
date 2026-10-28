package com.salesmanager.test.business.modules.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactoryBean;

import com.salesmanager.core.business.modules.email.DefaultEmailSenderImpl;
import com.salesmanager.core.business.modules.email.Email;

import freemarker.template.Configuration;

@RunWith(MockitoJUnitRunner.class)
public class DefaultEmailSenderImplTest {

	private static final String PAYLOAD = "<b>x</b><img src=\"http://example.invalid/p.gif\">";

	@Mock
	private JavaMailSenderImpl mailSender;

	private DefaultEmailSenderImpl emailSender;

	@Before
	public void setUp() throws Exception {
		FreeMarkerConfigurationFactoryBean factory = new FreeMarkerConfigurationFactoryBean();
		factory.setTemplateLoaderPath("classpath:/templates/email");
		factory.afterPropertiesSet();

		emailSender = new DefaultEmailSenderImpl();
		emailSender.setMailSender(mailSender);
		emailSender.setFreemarkerMailConfiguration(factory.getObject());
	}

	@Test
	public void sanitizesFromDisplayNameAndRendersContactContentEscaped() throws Exception {
		Email email = email("Acme\r\nBcc: victim@example.com");
		emailSender.send(email);

		MimeMessage message = preparedMessage();
		InternetAddress from = (InternetAddress) message.getFrom()[0];
		assertEquals("Acme Bcc: victim@example.com", from.getPersonal());
		assertEquals("store@shop.example", from.getAddress());
		for (String header : message.getHeader("From")) {
			assertFalse(header.contains("\r"));
			assertFalse(header.contains("\n"));
		}
		assertNull(message.getHeader("Bcc"));
		assertNull(message.getRecipients(Message.RecipientType.BCC));
		InternetAddress to = (InternetAddress) message.getRecipients(Message.RecipientType.TO)[0];
		assertEquals("owner@shop.example", to.getAddress());

		Multipart multipart = (Multipart) message.getContent();
		String text = read(multipart.getBodyPart(0));
		assertTrue(text.contains("&lt;b&gt;x&lt;/b&gt;"));
		assertFalse(text.contains("<b>x</b>"));
	}

	@Test
	public void acceptsNullFromDisplayName() throws Exception {
		emailSender.send(email(null));

		MimeMessage message = preparedMessage();
		InternetAddress from = (InternetAddress) message.getFrom()[0];
		assertNull(from.getPersonal());
		assertEquals("store@shop.example", from.getAddress());
	}

	private MimeMessage preparedMessage() throws Exception {
		ArgumentCaptor<MimeMessagePreparator> preparator = ArgumentCaptor.forClass(MimeMessagePreparator.class);
		verify(mailSender).send(preparator.capture());

		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		preparator.getValue().prepare(message);
		message.saveChanges();
		return message;
	}

	private Email email(String from) {
		Email email = new Email();
		email.setFrom(from);
		email.setFromEmail("store@shop.example");
		email.setTo("owner@shop.example");
		email.setSubject("Contact");
		email.setTemplateName("email_template_contact.ftl");

		Map<String, String> tokens = new HashMap<String, String>();
		tokens.put("LOGOPATH", "<img src='https://shop.example/logo.png' style='max-width:400px;'>");
		tokens.put("EMAIL_CUSTOMER_CONTACT", "A new contact email - Question");
		tokens.put("EMAIL_CONTACT_NAME_LABEL", "Name");
		tokens.put("EMAIL_CONTACT_NAME", "Visitor");
		tokens.put("EMAIL_CONTACT_EMAIL_LABEL", "Email");
		tokens.put("EMAIL_CONTACT_EMAIL", "visitor@example.com");
		tokens.put("EMAIL_CONTACT_CONTENT", PAYLOAD);
		tokens.put("EMAIL_DISCLAIMER", "disclaimer");
		tokens.put("EMAIL_SPAM_DISCLAIMER", "spam");
		tokens.put("EMAIL_FOOTER_COPYRIGHT", "copyright");
		email.setTemplateTokens(tokens);
		return email;
	}

	private String read(BodyPart part) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (InputStream input = part.getInputStream()) {
			byte[] buffer = new byte[1024];
			int count;
			while ((count = input.read(buffer)) != -1) {
				output.write(buffer, 0, count);
			}
		}
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}
}
