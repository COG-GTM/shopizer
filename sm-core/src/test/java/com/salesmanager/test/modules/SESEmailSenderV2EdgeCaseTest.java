package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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

public class SESEmailSenderV2EdgeCaseTest {

  @Test
  public void normalizesTrimmedUnderscoreAndUppercaseRegions() {
    assertRegion(" us_east_1 ");
    assertRegion("US-EAST-1");
  }

  @Test
  public void replacesInjectedClientAfterRegionChangeWithoutClosingIt() {
    SESEmailSenderImpl sender = new SESEmailSenderImpl();
    ReflectionTestUtils.setField(sender, "region", "us-east-1");
    SesV2Client injected = mock(SesV2Client.class);
    ReflectionTestUtils.invokeMethod(sender, "setSesV2Client", injected);
    ReflectionTestUtils.setField(sender, "region", "us-west-2");

    SesV2Client replacement =
        (SesV2Client) ReflectionTestUtils.invokeMethod(sender, "sesV2Client");

    org.mockito.Mockito.verify(injected, org.mockito.Mockito.never()).close();
    assertEquals(Region.US_WEST_2, replacement.serviceClientConfiguration().region());
    replacement.close();
  }

  @Test
  public void propagatesSesFailureAndSendsTextFallback() throws Exception {
    SesV2Client ses = mock(SesV2Client.class);
    RuntimeException failure = new RuntimeException("SES unavailable");
    when(ses.sendEmail(any(SendEmailRequest.class))).thenThrow(failure);
    Configuration templates = mock(Configuration.class);
    Template template = mock(Template.class);
    when(templates.getTemplate(anyString())).thenReturn(template);
    doAnswer(invocation -> {
      ((Writer) invocation.getArgument(1)).write("<p>body</p>");
      return null;
    }).when(template).process(any(), any(Writer.class));
    SESEmailSenderImpl sender = new SESEmailSenderImpl() {
      @Override
      protected SesV2Client sesV2Client() {
        return ses;
      }
    };
    ReflectionTestUtils.setField(sender, "region", "us-east-1");
    ReflectionTestUtils.setField(sender, "freemarkerMailConfiguration", templates);

    try {
      sender.send(email());
      org.junit.Assert.fail("Expected SES exception to propagate");
    } catch (RuntimeException expected) {
      assertSame(failure, expected);
    }

    org.mockito.ArgumentCaptor<SendEmailRequest> request =
        org.mockito.ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(ses).sendEmail(request.capture());
    assertEquals("This email was sent through Amazon SES using the AWS SDK for Java.",
        request.getValue().content().simple().body().text().data());
  }

  private void assertRegion(String name) {
    SESEmailSenderImpl sender = new SESEmailSenderImpl();
    ReflectionTestUtils.setField(sender, "region", name);
    SesV2Client client = (SesV2Client) ReflectionTestUtils.invokeMethod(sender, "sesV2Client");
    try {
      assertEquals(Region.US_EAST_1, client.serviceClientConfiguration().region());
    } finally {
      client.close();
    }
  }

  private Email email() {
    Email email = new Email();
    email.setTo("to@example.com");
    email.setFromEmail("from@example.com");
    email.setSubject("subject");
    email.setTemplateName("message.ftl");
    email.setTemplateTokens(Collections.<String, String>emptyMap());
    return email;
  }
}
