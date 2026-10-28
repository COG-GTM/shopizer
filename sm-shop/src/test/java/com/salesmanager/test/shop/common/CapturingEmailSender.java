package com.salesmanager.test.shop.common;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.EmailConfig;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.business.services.system.EmailService;

/**
 * Records emails instead of sending them. Installed into the shared Spring
 * context's EmailService (no @MockBean, so no extra application context).
 */
public class CapturingEmailSender implements HtmlEmailSender {

	private final List<Email> sent = new CopyOnWriteArrayList<>();
	private final Object target;
	private final Object original;

	private CapturingEmailSender(Object target, Object original) {
		this.target = target;
		this.original = original;
	}

	public static CapturingEmailSender install(EmailService emailService) {
		Object target = AopTestUtils.getUltimateTargetObject(emailService);
		Object original = ReflectionTestUtils.getField(target, "sender");
		CapturingEmailSender capturing = new CapturingEmailSender(target, original);
		ReflectionTestUtils.setField(target, "sender", capturing);
		return capturing;
	}

	public void uninstall() {
		ReflectionTestUtils.setField(target, "sender", original);
	}

	public List<Email> sent() {
		return sent;
	}

	@Override
	public void send(Email email) {
		sent.add(email);
	}

	@Override
	public void setEmailConfig(EmailConfig emailConfig) {
		// not needed
	}
}
