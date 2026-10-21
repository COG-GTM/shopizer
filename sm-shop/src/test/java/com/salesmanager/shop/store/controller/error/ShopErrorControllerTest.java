package com.salesmanager.shop.store.controller.error;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

/**
 * AB-538 removed the JAX-RS {@code @Produces} annotations from this advice; the Spring MVC
 * handler contract (views, model, status) must be unchanged.
 */
public class ShopErrorControllerTest {

	private final ShopErrorController controller = new ShopErrorController();

	@Test
	public void accessDeniedRendersAccessDeniedView() {
		ModelAndView view = controller.handleException(new AccessDeniedException("denied"));
		assertEquals("error/access_denied", view.getViewName());
		assertNull(view.getModel().get("errMsg"));
	}

	@Test
	public void genericExceptionRendersErrorViewWithMessageAndStackTrace() {
		ModelAndView view = controller.handleException(new Exception("boom"));
		assertEquals("error/generic_error", view.getViewName());
		assertEquals("boom", view.getModel().get("errMsg"));
		assertTrue(((String) view.getModel().get("stackError")).startsWith("java.lang.Exception: boom"));
	}

	@Test
	public void runtimeExceptionRendersErrorViewWithMessageAndStackTrace() {
		ModelAndView view = controller.handleRuntimeException(new IllegalStateException("bad state"));
		assertEquals("error/generic_error", view.getViewName());
		assertEquals("bad state", view.getModel().get("errMsg"));
		assertTrue(((String) view.getModel().get("stackError")).contains("IllegalStateException: bad state"));
	}

	@Test
	public void handlersKeepSpringExceptionMappingAndStatus() throws Exception {
		assertHandler("handleException", Exception.class);
		assertHandler("handleRuntimeException", RuntimeException.class);
	}

	private void assertHandler(String methodName, Class<? extends Throwable> handled) throws Exception {
		Method method = ShopErrorController.class.getMethod(methodName, Exception.class);
		ExceptionHandler handler = method.getAnnotation(ExceptionHandler.class);
		assertNotNull(handler);
		assertEquals(handled, handler.value()[0]);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, method.getAnnotation(ResponseStatus.class).value());
	}
}
