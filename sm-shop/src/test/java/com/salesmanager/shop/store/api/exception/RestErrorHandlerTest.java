package com.salesmanager.shop.store.api.exception;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

class RestErrorHandlerTest {
    @Test
    void jwtFailuresMapToUnauthorized() throws Exception {
        RestErrorHandler handler = new RestErrorHandler();
        for (JwtException exception : new JwtException[] {
                new ExpiredJwtException(null, null, "expired"), new SignatureException("bad signature") }) {
            assertEquals("401", handler.handleJwtException(exception).getErrorCode());
        }
        assertEquals(HttpStatus.UNAUTHORIZED, RestErrorHandler.class.getMethod("handleJwtException", JwtException.class)
                .getAnnotation(ResponseStatus.class).value());
    }
}
