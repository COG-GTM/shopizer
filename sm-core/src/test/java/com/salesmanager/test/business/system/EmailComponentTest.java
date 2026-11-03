package com.salesmanager.test.business.system;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.EmailComponent;
import com.salesmanager.core.business.modules.email.EmailConfig;
import com.salesmanager.core.business.modules.email.EmailModule;

public class EmailComponentTest {

	private EmailModule defaultSender;
	private EmailModule sesSender;
	private EmailComponent component;

	@BeforeEach
	public void setUp() {
		defaultSender = mock(EmailModule.class);
		sesSender = mock(EmailModule.class);
		component = new EmailComponent();
		ReflectionTestUtils.setField(component, "defaultEmailSender", defaultSender);
		ReflectionTestUtils.setField(component, "sesEmailSender", sesSender);
	}

	@Test
	public void defaultImplementationReceivesPerCallConfig() throws Exception {
		ReflectionTestUtils.setField(component, "emailSender", "default");
		Email email = new Email();
		EmailConfig config = new EmailConfig();

		component.send(email, config);

		verify(defaultSender).send(email, config);
		verifyNoInteractions(sesSender);
	}

	@Test
	public void sesImplementationReceivesPerCallConfig() throws Exception {
		ReflectionTestUtils.setField(component, "emailSender", "ses");
		Email email = new Email();
		EmailConfig config = new EmailConfig();

		component.send(email, config);

		verify(sesSender).send(email, config);
		verifyNoInteractions(defaultSender);
	}

	@Test
	public void unknownImplementationIsRejected() {
		ReflectionTestUtils.setField(component, "emailSender", "unknown");

		assertThrows(Exception.class, () -> component.send(new Email(), new EmailConfig()));
		verifyNoInteractions(defaultSender, sesSender);
	}
}
