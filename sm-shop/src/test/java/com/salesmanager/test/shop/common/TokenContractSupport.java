package com.salesmanager.test.shop.common;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import java.util.Collections;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

public final class TokenContractSupport {
    private TokenContractSupport() { }

    public static String secret() throws Exception {
        Properties properties = new Properties();
        try (java.io.InputStream in = TokenContractSupport.class.getResourceAsStream("/authentication.properties")) {
            properties.load(in);
        }
        return properties.getProperty("jwt.secret");
    }

    public static byte[] randomKey() {
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public static String expiredAdminToken() throws Exception {
        JWTTokenUtil util = new JWTTokenUtil();
        ReflectionTestUtils.setField(util, "secret", secret());
        ReflectionTestUtils.setField(util, "expiration", -3600L);
        if (org.springframework.util.ReflectionUtils.findMethod(JWTTokenUtil.class, "initializeKey") != null) {
            ReflectionTestUtils.invokeMethod(util, "initializeKey");
        }
        return util.generateToken(new JWTUser(1L, "admin@shopizer.com", "Admin", "User",
                "admin@shopizer.com", "password", Collections.emptyList(), true, null));
    }

    public static String token(byte[] key, String subject, long issued, long expires) throws Exception {
        String body = encode("{\"alg\":\"HS512\",\"typ\":\"JWT\"}") + "." + encode(
                "{\"sub\":\"" + subject + "\",\"aud\":\"api\",\"iat\":" + issued + ",\"exp\":" + expires + "}");
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(key, "HmacSHA512"));
        return body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    public static String unsigned() {
        return encode("{\"alg\":\"none\"}") + "." + encode("{\"sub\":\"admin@shopizer.com\",\"aud\":\"api\"}") + ".";
    }

    public static JsonNode part(String token, int index) throws Exception {
        return new ObjectMapper().readTree(Base64.getUrlDecoder().decode(token.split("\\.")[index]));
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
