package com.salesmanager.shop.store.api.exception;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RestErrorHandlerTest {

    private final RestErrorHandler handler = new RestErrorHandler();

    @Test
    public void handlesServiceExceptionWithoutCause() {
        ErrorEntity result = handler.handleServiceException(new OperationNotAllowedException("msg"));

        assertNotNull(result);
        assertTrue(result.getMessage().contains("msg"));
    }

    @Test
    public void reportsInnermostCauseMessage() {
        ServiceRuntimeException exception = new ServiceRuntimeException("outer",
                new RuntimeException("middle", new IllegalStateException("inner")));

        ErrorEntity result = handler.handleServiceException(exception);

        assertNotNull(result);
        assertTrue(result.getMessage().contains("inner"));
    }
}
