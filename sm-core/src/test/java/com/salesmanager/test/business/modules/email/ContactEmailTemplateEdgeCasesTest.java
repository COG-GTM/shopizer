package com.salesmanager.test.business.modules.email;

import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactoryBean;

import freemarker.template.Configuration;
import freemarker.template.Template;

public class ContactEmailTemplateEdgeCasesTest {

	private Configuration configuration;

	@Before
	public void setUp() throws Exception {
		FreeMarkerConfigurationFactoryBean factory = new FreeMarkerConfigurationFactoryBean();
		factory.setTemplateLoaderPath("classpath:/templates/email");
		factory.afterPropertiesSet();
		configuration = factory.getObject();
	}

	@Test
	public void escapesAmpersandsInHeadingAndName() throws Exception {
		String html = render(tokens("A & B", "visitor@example.com", "comment", "Heading & Title"));
		assertTrue(html.contains("Heading &amp; Title"));
		assertTrue(html.contains("Name: A &amp; B"));
	}

	@Test
	public void escapesDoubleQuotesInName() throws Exception {
		String html = render(tokens("A \"quoted\" name", "visitor@example.com", "comment", "Heading"));
		assertTrue(html.contains("A &quot;quoted&quot; name"));
	}

	@Test
	public void escapesPreEscapedCommentWithoutUnescapingIt() throws Exception {
		String html = render(tokens("Visitor", "visitor@example.com", "&lt;b&gt;", "Heading"));
		assertTrue(html.contains("&amp;lt;b&amp;gt;"));
	}

	@Test
	public void preservesRepeatedLineBreaksAsHtmlBreaks() throws Exception {
		String html = render(tokens("Visitor", "visitor@example.com", "a\r\n\r\nb", "Heading"));
		assertTrue(html.contains("a<br /><br />b"));
	}

	@Test
	public void passesNonAsciiCommentThrough() throws Exception {
		String html = render(tokens("Visitor", "visitor@example.com", "Jos\u00e9 \ud83d\ude00", "Heading"));
		assertTrue(html.contains("Jos\u00e9 \ud83d\ude00"));
	}

	@Test
	public void rendersLongComment() throws Exception {
		StringBuilder comment = new StringBuilder();
		for (int i = 0; i < 10000; i++) {
			comment.append('x');
		}
		assertTrue(render(tokens("Visitor", "visitor@example.com", comment.toString(), "Heading"))
				.contains(comment.toString()));
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
}
