package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.modules.email.DefaultEmailSenderImpl;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.shop.ContactForm;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ContactEmailIntegrationTest {

	private static final String DISPLAY_NAME = "Acme\r\nBcc: victim@example.com";
	private static final String COMMENT = "<b>x</b>\r\nsecond line";
	private static final String SUBJECT = "<img src=x onerror=alert(1)>";

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Autowired
	private JavaMailSenderImpl actualMailSender;

	@Autowired
	private DefaultEmailSenderImpl emailSender;

	private JavaMailSenderImpl mailSender;

	@Before
	public void resetMailSender() {
		mailSender = org.mockito.Mockito.mock(JavaMailSenderImpl.class);
		emailSender.setMailSender(mailSender);
	}

	@After
	public void restoreMailSender() {
		emailSender.setMailSender(actualMailSender);
	}

	@Test
	public void v1ContactEscapesVisitorContentAndSanitizesHeaders() throws Exception {
		ResponseEntity<Void> response = testRestTemplate.postForEntity(
				"/api/v1/contact?store=DEFAULT", new HttpEntity<ContactForm>(contact(DISPLAY_NAME, SUBJECT, COMMENT)),
				Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertContactMessage();
	}

	@Test
	public void v0ContactEscapesVisitorContentAndSanitizesHeaders() throws Exception {
		ResponseEntity<Void> response = testRestTemplate.postForEntity(
				"/services/public/DEFAULT/contact", new HttpEntity<ContactForm>(contact(DISPLAY_NAME, SUBJECT, COMMENT)),
				Void.class);
		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
		assertContactMessage();
	}

	private void assertContactMessage() throws Exception {
		ArgumentCaptor<MimeMessagePreparator> preparator = ArgumentCaptor.forClass(MimeMessagePreparator.class);
		verify(mailSender, timeout(15000)).send(preparator.capture());

		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		preparator.getValue().prepare(message);
		message.saveChanges();

		InternetAddress from = (InternetAddress) message.getFrom()[0];
		assertEquals("Acme Bcc: victim@example.com", from.getPersonal());
		for (String header : message.getHeader("From")) {
			assertFalse(header.contains("\r"));
			assertFalse(header.contains("\n"));
		}
		assertNull(message.getHeader("Bcc"));
		assertNull(message.getRecipients(Message.RecipientType.BCC));
		assertEquals("contact@shopizer.com",
				((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress());

		Multipart multipart = (Multipart) message.getContent();
		String body = read(multipart.getBodyPart(0));
		assertTrue(body.contains("&lt;b&gt;x&lt;/b&gt;<br />second line"));
		assertFalse(body.contains("<b>x</b>"));
		assertTrue(body.contains("&lt;img src=x onerror=alert(1)&gt;"));
		assertTrue(body.contains("A new contact email"));
	}

	private ContactForm contact(String name, String subject, String comment) {
		ContactForm contact = new ContactForm();
		contact.setName(name);
		contact.setEmail("visitor@example.com");
		contact.setSubject(subject);
		contact.setComment(comment);
		return contact;
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
