package com.salesmanager.core.business.modules.email;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

class SESEmailSenderImplTest {
    private SESEmailSenderImpl sender;
    private SesClient client;
    private Email email;

    @BeforeEach
    void setUp() {
        sender = new SESEmailSenderImpl();
        client = mock(SesClient.class);
        sender.setClient(client);
        ReflectionTestUtils.setField(sender, "region", "US_EAST_1");
        StringTemplateLoader loader = new StringTemplateLoader();
        loader.putTemplate("templates/email/test.ftl", "<b>${name}</b>");
        Configuration templates = spy(new Configuration(Configuration.VERSION_2_3_30));
        templates.setTemplateLoader(loader);
        doNothing().when(templates).setClassForTemplateLoading(any(Class.class), anyString());
        ReflectionTestUtils.setField(sender, "freemarkerMailConfiguration", templates);
        email = new Email();
        email.setTo("recipient@example.com");
        email.setFromEmail("sender@example.com");
        email.setSubject("Subject");
        email.setTemplateName("test.ftl");
        email.getTemplateTokens().put("name", "Shopizer");
    }

    @Test
    void sendsUnchangedMessage() throws Exception {
        sender.send(email);
        ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(client).sendEmail(request.capture());
        SendEmailRequest sent = request.getValue();
        assertEquals("sender@example.com", sent.source());
        assertEquals(java.util.Collections.singletonList("recipient@example.com"), sent.destination().toAddresses());
        assertEquals("Subject", sent.message().subject().data());
        assertEquals("<b>Shopizer</b>", sent.message().body().html().data());
        assertEquals(SESEmailSenderImpl.TEXTBODY, sent.message().body().text().data());
        assertEquals("UTF-8", sent.message().body().html().charset());
    }

    @Test
    void missingRegionDoesNotSend() {
        ReflectionTestUtils.setField(sender, "region", null);
        assertThrows(IllegalArgumentException.class, () -> sender.send(email));
        verifyNoInteractions(client);
    }

    @Test
    void sdkFailurePropagates() {
        when(client.sendEmail(any(SendEmailRequest.class))).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class, () -> sender.send(email));
    }
}
