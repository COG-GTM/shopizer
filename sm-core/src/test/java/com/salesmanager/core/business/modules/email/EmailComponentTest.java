package com.salesmanager.core.business.modules.email;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class EmailComponentTest {

  private EmailComponent component;
  private EmailModule defaultSender;
  private EmailModule sesSender;
  private Email email;
  private EmailConfig config;

  @Before
  public void setUp() {
    component = new EmailComponent();
    defaultSender = mock(EmailModule.class);
    sesSender = mock(EmailModule.class);
    email = new Email();
    config = new EmailConfig();
    ReflectionTestUtils.setField(component, "defaultEmailSender", defaultSender);
    ReflectionTestUtils.setField(component, "sesEmailSender", sesSender);
  }

  @Test
  public void routesDefaultSenderWithConfiguration() throws Exception {
    ReflectionTestUtils.setField(component, "emailSender", "default");

    component.send(email, config);

    verify(defaultSender).send(email, config);
    verifyNoInteractions(sesSender);
  }

  @Test
  public void routesSesSenderWithConfiguration() throws Exception {
    ReflectionTestUtils.setField(component, "emailSender", "ses");

    component.send(email, config);

    verify(sesSender).send(email, config);
    verifyNoInteractions(defaultSender);
  }

  @Test
  public void delegatesSingleArgumentSendWithNullConfiguration() throws Exception {
    ReflectionTestUtils.setField(component, "emailSender", "default");

    component.send(email);

    verify(defaultSender).send(email, null);
    verifyNoInteractions(sesSender);
  }

  @Test
  public void rejectsUnknownSender() {
    ReflectionTestUtils.setField(component, "emailSender", "unknown");

    Exception exception = assertThrows(Exception.class, () -> component.send(email, null));

    assertTrue(exception.getMessage().contains("No email implementation"));
    verifyNoInteractions(defaultSender, sesSender);
  }
}
