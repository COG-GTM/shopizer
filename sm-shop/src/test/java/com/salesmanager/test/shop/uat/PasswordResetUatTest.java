package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.shop.application.ShopApplication;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class PasswordResetUatTest extends UatSupport {
    @Autowired private EmailService emailService;
    @Value("${uat.expect-single-use:true}") private boolean expectSingleUse;

    @Test public void forgotResetLoginAndReplay() throws Exception {
        String email = register();
        HtmlEmailSender mail = mock(HtmlEmailSender.class);
        Object original = ReflectionTestUtils.getField(emailService, "sender");
        try {
            ReflectionTestUtils.setField(emailService, "sender", mail);
            assertEquals(200, request("forgot", HttpMethod.POST, "/api/v1/customer/password/reset/request",
                    Map.of("username", email, "returnUrl", "http://localhost/reset"), null).getStatusCodeValue());
        } finally {
            ReflectionTestUtils.setField(emailService, "sender", original);
        }
        ArgumentCaptor<Email> message = ArgumentCaptor.forClass(Email.class);
        verify(mail, timeout(5000)).send(message.capture());
        assertEquals(email, message.getValue().getTo());
        Matcher matcher = Pattern.compile("[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}")
                .matcher(message.getValue().getTemplateTokens().toString());
        assertTrue("Reset email contains token", matcher.find()); String token = matcher.group();
        String prefix = "/api/v1/customer/DEFAULT/";
        assertEquals(200, request("reset-verify", HttpMethod.GET, prefix + "reset/" + token, null, null).getStatusCodeValue());
        assertEquals(200, request("reset", HttpMethod.POST, prefix + "password/" + token,
                Map.of("password", "NewPass123", "repeatPassword", "NewPass123"), null).getStatusCodeValue());
        login("/api/v1/customer/login", email, "NewPass123");
        int replay = request("reset-replay", HttpMethod.POST, prefix + "password/" + token,
                Map.of("password", "Replay123", "repeatPassword", "Replay123"), null).getStatusCodeValue();
        int verifyUsed = request("reset-verify-used", HttpMethod.GET, prefix + "reset/" + token, null, null).getStatusCodeValue();
        if (expectSingleUse) {
            assertTrue(replay >= 400); assertTrue(verifyUsed >= 400);
            login("/api/v1/customer/login", email, "NewPass123");
        } else {
            assertEquals(200, replay); assertEquals(200, verifyUsed);
            login("/api/v1/customer/login", email, "Replay123");
        }
    }
}
