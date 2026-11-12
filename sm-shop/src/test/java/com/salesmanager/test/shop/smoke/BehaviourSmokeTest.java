package com.salesmanager.test.shop.smoke;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.business.services.system.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Embedded AB-533 suite: only outbound SMTP is mocked. */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BehaviourSmokeTest extends BehaviourSmokeScenarios {
    @Autowired private EmailService emailService;

    @Override
    @Test
    void checkoutCreatesOrderThatAdminCanRead() throws Exception {
        Object sender = ReflectionTestUtils.getField(emailService, "sender");
        HtmlEmailSender transport = mock(HtmlEmailSender.class);
        ReflectionTestUtils.setField(emailService, "sender", transport);
        try {
            checkout();
            verify(transport, timeout(5000).times(2)).send(any());
        } finally {
            ReflectionTestUtils.setField(emailService, "sender", sender);
        }
    }
}
