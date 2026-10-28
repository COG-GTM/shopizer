package com.salesmanager.test.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import freemarker.template.Configuration;
import freemarker.template.Template;

public class PasswordResetEmailTemplateLinkTest {

	private static final String[] TEMPLATES = {
			"email_template_password_reset_request_customer.ftl",
			"email_template_password_reset_request_user.ftl"
	};

	@Test
	public void benignLinksAppearVerbatimInBothTemplates() throws Exception {
		String link = "https://shop.example/customer/DEFAULT/reset/0b1c2d3e-aaaa-bbbb-cccc-1234567890ab";
		for (String template : TEMPLATES) {
			assertTrue(render(template, link).contains("href=\"" + link + "\""));
		}
	}

	@Test
	public void ampersandsAreEscapedInBothTemplates() throws Exception {
		String link = "https://shop.example/reset?token=abc&store=DEFAULT";
		for (String template : TEMPLATES) {
			assertTrue(render(template, link).contains("href=\"https://shop.example/reset?token=abc&amp;store=DEFAULT\""));
		}
	}

	@Test
	public void apostrophesAreEscapedInsideHrefValues() throws Exception {
		String link = "https://shop.example/reset/'token";
		for (String template : TEMPLATES) {
			Matcher matcher = Pattern.compile("href=\"([^\"]*)\"").matcher(render(template, link));
			assertTrue(matcher.find());
			assertFalse(matcher.group(1).contains("'"));
		}
	}

	private String render(String templateName, String link) throws Exception {
		Configuration configuration = new Configuration(Configuration.VERSION_2_3_23);
		configuration.setClassForTemplateLoading(PasswordResetEmailTemplateLinkTest.class, "/templates/email");
		Template template = configuration.getTemplate(templateName);
		Map<String, String> tokens = new HashMap<>();
		for (String key : new String[] { "LOGOPATH", "LABEL_HI", "EMAIL_CUSTOMER_FIRSTNAME", "EMAIL_USER_FIRSTNAME",
				"RESET_PASSWORD_TEXT", "LABEL_LINK_TITLE", "LABEL_LINK", "EMAIL_CONTACT_OWNER", "EMAIL_DISCLAIMER",
				"EMAIL_SPAM_DISCLAIMER", "EMAIL_FOOTER_COPYRIGHT" }) {
			tokens.put(key, key);
		}
		tokens.put("RESET_PASSWORD_LINK", link);
		StringWriter out = new StringWriter();
		template.process(tokens, out);
		return out.toString();
	}
}
