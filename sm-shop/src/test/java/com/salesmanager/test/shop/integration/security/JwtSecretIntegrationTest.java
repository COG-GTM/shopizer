package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

class JwtSecretIntegrationTest extends ServicesTestSupport {
    private static final String ADMIN = "admin@shopizer.com";
    private static final String PROFILE_PATH = "/api/v1/private/user/profile";

    @Autowired
    private JWTTokenUtil jwtTokenUtil;

    @BeforeEach
    void setUp() {
        assertNotNull(jwtTokenUtil);
    }

    @Test
    void configuredSecretIsNotTheOldHardCodedValue() {
        String secret = (String) ReflectionTestUtils.getField(jwtTokenUtil, "secret");
        assertNotNull(secret);
        assertNotEquals("aSecret", secret);
        assertTrue(Base64.getDecoder().decode(secret).length >= 64);
    }

    @Test
    void appSignedAdminTokenCanReadProfile() {
        AuthenticationResponse login = adminLogin();
        assertNotNull(login.getToken());
        assertEquals(HttpStatus.OK, profile(login.getToken()).getStatusCode());
    }

    @Test
    void rejectsForgedAdminTokensAndReportsAuthenticationStatuses() {
        AuthenticationResponse login = adminLogin();
        ResponseEntity<String> shortSecret = profile(token("aSecret"));
        ResponseEntity<String> otherSecret = profile(token(randomSecret()));
        ResponseEntity<String> noAuthorization = testRestTemplate.getForEntity(PROFILE_PATH, String.class);
        ResponseEntity<String> garbage = profile("garbage");
        System.out.println("JWT profile statuses: forged aSecret=" + shortSecret.getStatusCode()
                + ", other key=" + otherSecret.getStatusCode()
                + ", no Authorization=" + noAuthorization.getStatusCode()
                + ", garbage=" + garbage.getStatusCode());
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertFalse(shortSecret.getStatusCode().is2xxSuccessful()),
                () -> assertFalse(otherSecret.getStatusCode().is2xxSuccessful()),
                () -> assertEquals(HttpStatus.UNAUTHORIZED, noAuthorization.getStatusCode()),
                () -> assertFalse(garbage.getStatusCode().is2xxSuccessful()));
    }

    @Test
    void adminTokenCanBeRefreshed() {
        AuthenticationResponse login = adminLogin();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getToken());
        ResponseEntity<AuthenticationResponse> refresh = testRestTemplate.exchange(
                "/api/v1/auth/refresh", HttpMethod.GET, new HttpEntity<>(headers), AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, refresh.getStatusCode());
        assertNotNull(refresh.getBody());
        assertEquals(HttpStatus.OK, profile(refresh.getBody().getToken()).getStatusCode());
    }

    private AuthenticationResponse adminLogin() {
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
                "/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest(ADMIN, "password")),
                AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private ResponseEntity<String> profile(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return testRestTemplate.exchange(PROFILE_PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String token(String signingSecret) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(ADMIN)
                .setAudience("api")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 3_600_000))
                .signWith(SignatureAlgorithm.HS512, signingSecret)
                .compact();
    }

    private String randomSecret() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
