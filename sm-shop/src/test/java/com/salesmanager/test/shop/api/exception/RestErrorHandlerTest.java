package com.salesmanager.test.shop.api.exception;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

import com.salesmanager.shop.store.api.exception.ErrorEntity;
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;
import com.salesmanager.shop.store.api.exception.RestErrorHandler;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;

public class RestErrorHandlerTest {

	private final RestErrorHandler handler = new RestErrorHandler();

	@Test
	public void operationNotAllowedPreservesErrorCodeAndMessage() {
		ErrorEntity error = handler
				.handleServiceException(new OperationNotAllowedException("Cannot delete superadmin user [1]"));

		assertThat(error.getErrorCode(), is("304"));
		assertThat(error.getMessage(), containsString("Cannot delete superadmin user [1]"));
	}

	@Test
	public void serviceExceptionReportsRootCause() {
		ServiceRuntimeException exception = new ServiceRuntimeException("outer",
				new RuntimeException("mid", new IllegalStateException("root")));

		ErrorEntity error = handler.handleServiceException(exception);

		assertThat(error.getErrorCode(), is("500"));
		assertThat(error.getMessage(), containsString("root"));
	}

	@Test
	public void serviceExceptionWithoutCauseDoesNotThrow() {
		ErrorEntity error = handler.handleServiceException(new ServiceRuntimeException("only message"));

		assertThat(error.getMessage(), containsString("only message"));
	}
}
