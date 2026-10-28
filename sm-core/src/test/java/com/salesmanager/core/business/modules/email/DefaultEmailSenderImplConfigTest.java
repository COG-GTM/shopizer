package com.salesmanager.core.business.modules.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

public class DefaultEmailSenderImplConfigTest {

  @Test
  public void createsIndependentSenderWithNormalizedConfiguration() {
    JavaMailSenderImpl sharedSender = new JavaMailSenderImpl();
    DefaultEmailSenderImpl sender = new DefaultEmailSenderImpl();
    ReflectionTestUtils.setField(sender, "mailSender", sharedSender);

    EmailConfig config = config(" mail.example.test ", " 2525 ");
    config.setProtocol("smtps");
    config.setUsername("store-user");
    config.setPassword("store-password");
    config.setSmtpAuth(true);
    config.setStarttls(false);

    JavaMailSenderImpl first = (JavaMailSenderImpl) sender.createMailSender(config);
    JavaMailSenderImpl second = (JavaMailSenderImpl) sender.createMailSender(config);

    assertEquals("mail.example.test", first.getHost());
    assertEquals(2525, first.getPort());
    assertEquals("smtps", first.getProtocol());
    assertEquals("store-user", first.getUsername());
    assertEquals("store-password", first.getPassword());
    assertEquals("true", first.getJavaMailProperties().getProperty("mail.smtp.auth"));
    assertEquals("false", first.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
    assertNotSame(sharedSender, first);
    assertNotSame(first, second);
  }

  @Test
  public void defaultsBlankProtocolAndCopiesFalseFlags() {
    DefaultEmailSenderImpl sender = new DefaultEmailSenderImpl();

    for (String protocol : new String[] {null, "  "}) {
      EmailConfig config = config("mail.example.test", "2525");
      config.setProtocol(protocol);

      JavaMailSenderImpl mailSender = (JavaMailSenderImpl) sender.createMailSender(config);

      assertEquals(JavaMailSenderImpl.DEFAULT_PROTOCOL, mailSender.getProtocol());
      assertEquals("false", mailSender.getJavaMailProperties().getProperty("mail.smtp.auth"));
      assertEquals("false", mailSender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
    }
  }

  @Test
  public void acceptsPortBoundaries() {
    DefaultEmailSenderImpl sender = new DefaultEmailSenderImpl();

    assertEquals(1, ((JavaMailSenderImpl) sender.createMailSender(config("mail.example.test", "1"))).getPort());
    assertEquals(65535, ((JavaMailSenderImpl) sender.createMailSender(config("mail.example.test", "65535"))).getPort());
  }

  @Test
  public void rejectsMissingOrInvalidHostAndPort() {
    DefaultEmailSenderImpl sender = new DefaultEmailSenderImpl();

    for (EmailConfig config : new EmailConfig[] {
        config(null, "25"),
        config("  ", "25"),
        config("mail.example.test", null),
        config("mail.example.test", "abc"),
        config("mail.example.test", "0"),
        config("mail.example.test", "-1"),
        config("mail.example.test", "65536")
    }) {
      assertThrows(IllegalArgumentException.class, () -> sender.createMailSender(config));
    }
  }

  @Test
  public void invalidConfigurationDoesNotInteractWithSharedSender() {
    JavaMailSender sharedSender = mock(JavaMailSender.class);
    DefaultEmailSenderImpl sender = new DefaultEmailSenderImpl();
    ReflectionTestUtils.setField(sender, "mailSender", sharedSender);

    assertThrows(IllegalArgumentException.class, () -> sender.send(new Email(), config(null, "25")));

    verifyNoInteractions(sharedSender);
  }

  private static EmailConfig config(String host, String port) {
    EmailConfig config = new EmailConfig();
    config.setHost(host);
    config.setPort(port);
    return config;
  }
}
