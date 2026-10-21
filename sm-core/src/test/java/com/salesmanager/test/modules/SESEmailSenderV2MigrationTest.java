package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Writer;
import java.util.Collections;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import freemarker.template.Configuration;
import freemarker.template.Template;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.SESEmailSenderImpl;

public class SESEmailSenderV2MigrationTest {

  @Test
  public void sendsUtf8HtmlAndTextEmail() throws Exception {
    SesV2Client sesClient = mock(SesV2Client.class);
    Configuration templateConfiguration = mock(Configuration.class);
    Template template = mock(Template.class);
    when(templateConfiguration.getTemplate(anyString())).thenReturn(template);
    doAnswer(invocation -> {
      ((Writer) invocation.getArgument(1)).write("<p>hello</p>");
      return null;
    }).when(template).process(any(), any(Writer.class));

    SESEmailSenderImpl sender = new SESEmailSenderImpl() {
      @Override
      protected SesV2Client sesV2Client() {
        return sesClient;
      }
    };
    ReflectionTestUtils.setField(sender, "region", "US_EAST_1");
    ReflectionTestUtils.setField(sender, "freemarkerMailConfiguration", templateConfiguration);

    Email email = new Email();
    email.setTo("to@example.com");
    email.setFromEmail("from@example.com");
    email.setSubject("Héllo");
    email.setTemplateName("message.ftl");
    email.setTemplateTokens(Collections.<String, String>emptyMap());
    sender.send(email);

    org.mockito.ArgumentCaptor<SendEmailRequest> request =
        org.mockito.ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(request.capture());
    SendEmailRequest sent = request.getValue();
    assertEquals(Collections.singletonList("to@example.com"), sent.destination().toAddresses());
    assertEquals("from@example.com", sent.fromEmailAddress());
    assertEquals("Héllo", sent.content().simple().subject().data());
    assertEquals("UTF-8", sent.content().simple().subject().charset());
    assertEquals("<p>hello</p>", sent.content().simple().body().html().data());
    assertEquals("UTF-8", sent.content().simple().body().html().charset());
    assertEquals("This email was sent through Amazon SES using the AWS SDK for Java.",
        sent.content().simple().body().text().data());
    assertEquals("UTF-8", sent.content().simple().body().text().charset());
  }

  @Test
  public void normalizesUnderscoreRegionNames() {
    SESEmailSenderImpl sender = new SESEmailSenderImpl();
    ReflectionTestUtils.setField(sender, "region", "US_EAST_1");

    SesV2Client client = (SesV2Client) ReflectionTestUtils.invokeMethod(sender, "sesV2Client");

    assertNotNull(client);
    assertEquals(Region.US_EAST_1, client.serviceClientConfiguration().region());
    client.close();
  }

  @Test
  public void closesOnlyOwnedClientWhenRegionChanges() {
    SESEmailSenderImpl sender = new SESEmailSenderImpl();
    ReflectionTestUtils.setField(sender, "region", "us-west-2");
    SesV2Client ownedClient = mock(SesV2Client.class);
    doThrow(new IllegalStateException("close failed")).when(ownedClient).close();
    ReflectionTestUtils.setField(sender, "sesV2Client", ownedClient);
    ReflectionTestUtils.setField(sender, "sesRegion", "us-east-1");
    ReflectionTestUtils.setField(sender, "ownsSesV2Client", true);

    SesV2Client replacement =
        (SesV2Client) ReflectionTestUtils.invokeMethod(sender, "sesV2Client");

    verify(ownedClient).close();
    replacement.close();

    SESEmailSenderImpl injectedSender = new SESEmailSenderImpl();
    ReflectionTestUtils.setField(injectedSender, "region", "us-east-1");
    SesV2Client injectedClient = mock(SesV2Client.class);
    ReflectionTestUtils.invokeMethod(injectedSender, "setSesV2Client", injectedClient);
    ReflectionTestUtils.setField(injectedSender, "region", "us-west-2");

    SesV2Client injectedReplacement =
        (SesV2Client) ReflectionTestUtils.invokeMethod(injectedSender, "sesV2Client");

    verify(injectedClient, never()).close();
    injectedReplacement.close();
  }
}
