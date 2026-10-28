package com.salesmanager.test.business.modules.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactoryBean;

import freemarker.template.Configuration;
import freemarker.template.Template;

/**
 * Renders email_template_contact.ftl with the same FreeMarker setup as the
 * freemarkerMailConfiguration bean and checks that visitor input is escaped.
 */
public class ContactEmailTemplateTest {

	private static final String PAYLOAD = "<b>x</b><img src=\"http://example.invalid/p.gif\">";

	private Configuration configuration;

	@Before
	public void setUp() throws Exception {
		FreeMarkerConfigurationFactoryBean factory = new FreeMarkerConfigurationFactoryBean();
		factory.setTemplateLoaderPath("classpath:/templates/email");
		factory.afterPropertiesSet();
		configuration = factory.getObject();
	}

	private String render(Map<String, String> tokens) throws Exception {
		Template template = configuration.getTemplate("email_template_contact.ftl");
		StringWriter writer = new StringWriter();
		template.process(tokens, writer);
		return writer.toString();
	}

	private Map<String, String> tokens(String name, String email, String comment, String heading) {
		Map<String, String> tokens = new HashMap<String, String>();
		tokens.put("LOGOPATH", "<img src='https://shop.example/logo.png' style='max-width:400px;'>");
		tokens.put("EMAIL_CUSTOMER_CONTACT", heading);
		tokens.put("EMAIL_CONTACT_NAME_LABEL", "Name");
		tokens.put("EMAIL_CONTACT_NAME", name);
		tokens.put("EMAIL_CONTACT_EMAIL_LABEL", "Email");
		tokens.put("EMAIL_CONTACT_EMAIL", email);
		tokens.put("EMAIL_CONTACT_CONTENT", comment);
		tokens.put("EMAIL_DISCLAIMER", "disclaimer");
		tokens.put("EMAIL_SPAM_DISCLAIMER", "spam");
		tokens.put("EMAIL_FOOTER_COPYRIGHT", "copyright");
		return tokens;
	}

	@Test
	public void visitorInputIsHtmlEscaped() throws Exception {
		String html = render(tokens(
				"<a href=\"http://evil.invalid\">Acme Payments</a>",
				"\"><script>alert(1)</script>@example.com",
				PAYLOAD,
				"A new contact email - <img src=x onerror=alert(1)>"));

		assertFalse(html.contains("<b>x</b>"));
		assertFalse(html.contains("http://example.invalid/p.gif\">"));
		assertFalse(html.contains("<a href=\"http://evil.invalid\">"));
		assertFalse(html.contains("<script>"));
		assertFalse(html.contains("<img src=x"));

		assertTrue(html.contains("&lt;b&gt;x&lt;/b&gt;&lt;img src=&quot;http://example.invalid/p.gif&quot;&gt;"));
		assertTrue(html.contains("&lt;a href=&quot;http://evil.invalid&quot;&gt;Acme Payments&lt;/a&gt;"));
		assertTrue(html.contains("&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;@example.com"));
		assertTrue(html.contains("A new contact email - &lt;img src=x onerror=alert(1)&gt;"));
	}

	@Test
	public void trustedStoreMarkupStillRenders() throws Exception {
		String html = render(tokens("Bob", "bob@example.com", "Hello", "A new contact email - Question"));
		assertTrue(html.contains("<img src='https://shop.example/logo.png' style='max-width:400px;'>"));
	}

	@Test
	public void commentLineBreaksArePreserved() throws Exception {
		String html = render(tokens("Bob", "bob@example.com", "line1\r\nline2\nline3\r<i>4</i>", "Question"));
		assertTrue(html.contains("line1<br />line2<br />line3<br />&lt;i&gt;4&lt;/i&gt;"));
		assertEquals(-1, html.indexOf("<i>4</i>"));
	}
}
