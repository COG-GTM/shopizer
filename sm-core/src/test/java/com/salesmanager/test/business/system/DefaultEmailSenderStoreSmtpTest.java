package com.salesmanager.test.business.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;

import com.salesmanager.core.business.modules.email.DefaultEmailSenderImpl;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.EmailConfig;

import freemarker.template.Configuration;

/**
 * Sends through a real JavaMail stack against throw-away local SMTP listeners
 * to verify that store specific settings reach the wire and that the shared
 * sender bean stays untouched.
 */
public class DefaultEmailSenderStoreSmtpTest {

	private JavaMailSenderImpl sharedMailSender;
	private DefaultEmailSenderImpl sender;
	private FakeSmtp smtpA;
	private FakeSmtp smtpB;

	@BeforeEach
	public void setUp() throws Exception {
		sharedMailSender = mock(JavaMailSenderImpl.class);
		sender = new DefaultEmailSenderImpl();
		sender.setMailSender(sharedMailSender);
		sender.setFreemarkerMailConfiguration(new Configuration(Configuration.VERSION_2_3_0));
		smtpA = new FakeSmtp();
		smtpB = new FakeSmtp();
	}

	@AfterEach
	public void tearDown() throws Exception {
		smtpA.close();
		smtpB.close();
	}

	private Email email() {
		Email email = new Email();
		email.setFrom("Store");
		email.setFromEmail("store@example.com");
		email.setTo("customer@example.com");
		email.setSubject("Subject");
		email.setTemplateName("email_template_isolation_test.ftl");
		email.setTemplateTokens(new HashMap<>());
		return email;
	}

	private EmailConfig config(int port) {
		EmailConfig config = new EmailConfig();
		config.setProtocol("smtp");
		config.setHost("127.0.0.1");
		config.setPort(String.valueOf(port));
		config.setSmtpAuth(false);
		config.setStarttls(false);
		return config;
	}

	@Test
	public void storeConfigConnectsToStoreSmtpHostAndPort() throws Exception {
		assertThrows(Exception.class, () -> sender.send(email(), config(smtpA.port())));

		assertTrue(smtpA.awaitConnection(), "store SMTP server should have been contacted");
		verifyNoInteractions(sharedMailSender);
	}

	@Test
	public void consecutiveStoresUseTheirOwnSmtpServers() throws Exception {
		assertThrows(Exception.class, () -> sender.send(email(), config(smtpA.port())));
		assertThrows(Exception.class, () -> sender.send(email(), config(smtpB.port())));

		assertTrue(smtpA.awaitConnection());
		assertTrue(smtpB.awaitConnection());
		assertEquals(1, smtpA.connections());
		assertEquals(1, smtpB.connections());
		verifyNoInteractions(sharedMailSender);
	}

	@Test
	public void legacySetEmailConfigIsUsedBySingleArgumentSend() throws Exception {
		sender.setEmailConfig(config(smtpA.port()));

		assertThrows(Exception.class, () -> sender.send(email()));

		assertTrue(smtpA.awaitConnection());
		verifyNoInteractions(sharedMailSender);
	}

	@Test
	public void sendWithoutAnyConfigUsesSharedSender() throws Exception {
		sender.send(email());

		verify(sharedMailSender).send(any(MimeMessagePreparator.class));
		assertEquals(0, smtpA.connections());
	}

	private static final class FakeSmtp implements AutoCloseable {
		private final ServerSocket server = new ServerSocket(0);
		private final CountDownLatch connected = new CountDownLatch(1);
		private final AtomicInteger connections = new AtomicInteger();
		private final Thread thread;

		FakeSmtp() throws Exception {
			thread = new Thread(() -> {
				while (!server.isClosed()) {
					try (Socket socket = server.accept()) {
						connections.incrementAndGet();
						connected.countDown();
						OutputStream out = socket.getOutputStream();
						out.write("421 test server closing\r\n".getBytes(StandardCharsets.US_ASCII));
						out.flush();
					} catch (Exception e) {
						return;
					}
				}
			});
			thread.setDaemon(true);
			thread.start();
		}

		int port() {
			return server.getLocalPort();
		}

		boolean awaitConnection() throws InterruptedException {
			return connected.await(10, TimeUnit.SECONDS);
		}

		int connections() {
			return connections.get();
		}

		@Override
		public void close() throws Exception {
			server.close();
		}
	}
}
