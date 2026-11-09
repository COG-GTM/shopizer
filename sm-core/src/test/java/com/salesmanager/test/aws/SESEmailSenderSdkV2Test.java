package com.salesmanager.test.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.SESEmailSenderImpl;

import freemarker.template.Configuration;

/**
 * SESEmailSenderImpl on AWS SDK v2 (SesClient): request mapping and region handling, against a local
 * fake SES endpoint.
 */
public class SESEmailSenderSdkV2Test {

	private static FakeAwsServer server;

	@BeforeAll
	static void startServer() throws Exception {
		server = new FakeAwsServer();
		server.configureSdk();
	}

	@AfterAll
	static void stopServer() {
		FakeAwsServer.clearSdk();
		server.close();
	}

	@BeforeEach
	void reset() {
		server.requests().clear();
	}

	private static SESEmailSenderImpl sender(String region) {
		SESEmailSenderImpl sender = new SESEmailSenderImpl();
		ReflectionTestUtils.setField(sender, "freemarkerMailConfiguration",
				new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS));
		ReflectionTestUtils.setField(sender, "region", region);
		return sender;
	}

	private static Email email() {
		Email email = new Email();
		email.setFrom("Shop");
		email.setFromEmail("shop@example.com");
		email.setTo("buyer@example.com");
		email.setSubject("Contact é & <test>");
		email.setTemplateName("email_template_contact.ftl");
		Map<String, String> tokens = new HashMap<>();
		for (String t : new String[] { "EMAIL_CONTACT_CONTENT", "EMAIL_CONTACT_EMAIL_LABEL", "EMAIL_CONTACT_EMAIL",
				"EMAIL_CONTACT_NAME_LABEL", "EMAIL_CONTACT_NAME", "EMAIL_CUSTOMER_CONTACT", "EMAIL_DISCLAIMER",
				"EMAIL_FOOTER_COPYRIGHT", "EMAIL_SPAM_DISCLAIMER", "LOGOPATH" }) {
			tokens.put(t, t.toLowerCase());
		}
		tokens.put("EMAIL_CONTACT_CONTENT", "Hello from the SES test");
		email.setTemplateTokens(tokens);
		return email;
	}

	private static Map<String, String> form(String body) {
		Map<String, String> m = new HashMap<>();
		for (String p : body.split("&")) {
			int eq = p.indexOf('=');
			m.put(URLDecoder.decode(p.substring(0, eq), StandardCharsets.UTF_8),
					URLDecoder.decode(p.substring(eq + 1), StandardCharsets.UTF_8));
		}
		return m;
	}

	@ParameterizedTest
	@CsvSource({ "US_EAST_1,us-east-1", "us-east-1,us-east-1", "us_east_1,us-east-1", "' EU_WEST_1 ',eu-west-1" })
	void sendsEmailForEnumAndIdRegionStyles(String configured, String expectedRegion) throws Exception {
		sender(configured).send(email());

		assertEquals(1, server.requests().size());
		FakeAwsServer.RecordedRequest req = server.requests().get(0);
		assertTrue(req.header("authorization").contains("/" + expectedRegion + "/ses/aws4_request"),
				req.header("authorization"));

		Map<String, String> form = form(req.bodyAsString());
		assertEquals("SendEmail", form.get("Action"));
		assertEquals("shop@example.com", form.get("Source"));
		assertEquals("buyer@example.com", form.get("Destination.ToAddresses.member.1"));
		assertEquals("Contact é & <test>", form.get("Message.Subject.Data"));
		assertEquals("UTF-8", form.get("Message.Subject.Charset"));
		assertEquals("UTF-8", form.get("Message.Body.Html.Charset"));
		assertTrue(form.get("Message.Body.Html.Data").contains("Hello from the SES test"));
		assertTrue(form.get("Message.Body.Text.Data").contains("Amazon SES"));
	}

	@Test
	void nullRegionIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> sender(null).send(email()));
		assertEquals(0, server.requests().size());
	}
}
