package com.salesmanager.test.shop.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.reference.zone.ZoneService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.constants.EmailConstants;
import com.salesmanager.shop.model.shop.ContactForm;
import com.salesmanager.shop.utils.EmailTemplatesUtils;
import com.salesmanager.shop.utils.EmailUtils;
import com.salesmanager.shop.utils.FilePathUtils;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LabelUtils;

@RunWith(MockitoJUnitRunner.class)
public class EmailTemplatesUtilsContactEmailTest {

	@Mock
	private EmailService emailService;

	@Mock
	private LabelUtils messages;

	@Mock
	private CountryService countryService;

	@Mock
	private ProductService productService;

	@Mock
	private ZoneService zoneService;

	@Mock
	private PricingService pricingService;

	@Mock
	private ImageFilePath imageUtils;

	@Mock
	private EmailUtils emailUtils;

	@Mock
	private FilePathUtils filePathUtils;

	@InjectMocks
	private EmailTemplatesUtils emailTemplatesUtils;

	@Test
	public void dispatchesRawVisitorValuesWithSanitizedFromName() throws Exception {
		MerchantStore store = new MerchantStore();
		store.setStoreEmailAddress("contact@shopizer.com");
		String name = "Acme\r\nBcc: victim@example.com";
		String comment = "<b>x</b>";

		ContactForm contact = new ContactForm();
		contact.setName(name);
		contact.setEmail("visitor@example.com");
		contact.setSubject("Question");
		contact.setComment(comment);

		when(emailUtils.createEmailObjectsMap(anyString(), eq(store), eq(messages), any(Locale.class)))
				.thenReturn(new HashMap<String, String>());
		when(messages.getMessage(anyString(), any(Locale.class))).thenReturn("label");
		when(messages.getMessage(eq("email.contact"), any(String[].class), any(Locale.class)))
				.thenReturn("A new contact email - Question");

		emailTemplatesUtils.sendContactEmail(contact, store, Locale.ENGLISH, "");

		ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
		verify(emailService).sendHtmlEmail(eq(store), emailCaptor.capture());
		Email email = emailCaptor.getValue();
		assertEquals("Acme Bcc: victim@example.com", email.getFrom());
		assertEquals("contact@shopizer.com", email.getFromEmail());
		assertEquals("contact@shopizer.com", email.getTo());
		assertEquals(EmailConstants.EMAIL_CONTACT_TMPL, email.getTemplateName());
		Map<String, String> tokens = email.getTemplateTokens();
		assertEquals(name, tokens.get(EmailConstants.EMAIL_CONTACT_NAME));
		assertEquals(comment, tokens.get(EmailConstants.EMAIL_CONTACT_CONTENT));
	}
}
