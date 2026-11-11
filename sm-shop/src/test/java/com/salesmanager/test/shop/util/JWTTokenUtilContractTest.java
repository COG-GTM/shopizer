package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;
import com.salesmanager.test.shop.common.TokenContractSupport;
import io.jsonwebtoken.JwtException;

class JWTTokenUtilContractTest {
    private JWTTokenUtil util;
    private final JWTUser user = new JWTUser(1L, "admin@shopizer.com", "Admin", "User",
            "admin@shopizer.com", "password", Collections.emptyList(), true, null);

    @BeforeEach
    void setUp() throws Exception {
        util = new JWTTokenUtil();
        ReflectionTestUtils.setField(util, "secret", TokenContractSupport.secret());
        ReflectionTestUtils.setField(util, "expiration", 3600L);
        if (java.util.Arrays.stream(JWTTokenUtil.class.getMethods()).anyMatch(m -> m.getName().equals("initializeKey"))) {
            ReflectionTestUtils.invokeMethod(util, "initializeKey");
        }
    }

    @Test
    void subjectAndLifetimeMatchConfiguration() {
        String token = util.generateToken(user);
        assertEquals(user.getUsername(), util.getUsernameFromToken(token));
        assertEquals(3600000L, util.getExpirationDateFromToken(token).getTime() - util.getIssuedAtDateFromToken(token).getTime());
        assertEquals(user.getUsername(), util.getClaimFromToken(token, c -> c.getSubject()));
    }

    @Test
    void refreshPreservesIdentityAndUsesHs512() throws Exception {
        String original = util.generateToken(user);
        String refreshed = util.refreshToken(original);
        assertEquals(util.getUsernameFromToken(original), util.getUsernameFromToken(refreshed));
        assertEquals(util.getAudienceFromToken(original), util.getAudienceFromToken(refreshed));
        assertFalse(util.getExpirationDateFromToken(refreshed).before(util.getExpirationDateFromToken(original)));
        assertEquals("HS512", TokenContractSupport.part(refreshed, 0).get("alg").asText());
        assertTrue(util.validateToken(refreshed, user));
    }

    @Test
    void rejectsDifferentSigningKey() throws Exception {
        long now = System.currentTimeMillis() / 1000;
        String token = TokenContractSupport.token(TokenContractSupport.randomKey(), user.getUsername(), now, now + 3600);
        assertThrows(JwtException.class, () -> util.getUsernameFromToken(token));
    }

    @Test
    void rejectsUnsignedToken() {
        assertThrows(JwtException.class, () -> util.getUsernameFromToken(TokenContractSupport.unsigned()));
    }

    @Test
    void rejectsOldDefaultSecret() {
        assertNotNull(org.springframework.util.ReflectionUtils.findMethod(JWTTokenUtil.class, "initializeKey"),
                "JWTTokenUtil must validate its signing key at initialization");
        ReflectionTestUtils.setField(util, "secret", "aSecret");
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(util, "initializeKey"));
    }

    @Test
    void refreshEligibilityRespectsPasswordReset() {
        String token = util.generateToken(user);
        Date issued = util.getIssuedAtDateFromToken(token);
        assertTrue(util.canTokenBeRefreshed(token, null));
        assertTrue(util.canTokenBeRefreshed(token, new Date(issued.getTime() - 1000)));
        assertFalse(util.canTokenBeRefreshed(token, new Date(issued.getTime() + 1000)));
        assertTrue(util.canTokenBeRefreshedWithGrace(token, null));
        assertTrue(util.canTokenBeRefreshedWithGrace(token, new Date(issued.getTime() + 1000)));
    }

    @Test
    void rejectsEmptyToken() {
        assertThrows(IllegalArgumentException.class, () -> util.getUsernameFromToken(""));
    }
}
