package com.salesmanager.shop.store.api.exception;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;

import org.junit.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;

public class RestErrorHandlerValidationTest {

	@Test
	public void formatsOneFieldValidationError() throws Exception {
		BeanPropertyBindingResult bindingResult =
				new BeanPropertyBindingResult(new PersistableShoppingCartItem(), "item");
		bindingResult.rejectValue("quantity", "Min", "quantity must be greater than or equal to 0");

		ErrorEntity error = new RestErrorHandler().handleValidationException(
				new MethodArgumentNotValidException(methodParameter(), bindingResult));

		assertEquals("400", error.getErrorCode());
		assertEquals("Validation failed, quantity quantity must be greater than or equal to 0",
				error.getMessage());
	}

	@Test
	public void joinsMultipleFieldValidationErrors() throws Exception {
		BeanPropertyBindingResult bindingResult =
				new BeanPropertyBindingResult(new PersistableShoppingCartItem(), "item");
		bindingResult.addError(new FieldError("item", "quantity", "quantity must be greater than or equal to 0"));
		bindingResult.addError(new FieldError("item", "product", "product must not be blank"));

		ErrorEntity error = new RestErrorHandler().handleValidationException(
				new MethodArgumentNotValidException(methodParameter(), bindingResult));

		assertEquals("Validation failed, quantity quantity must be greater than or equal to 0, "
				+ "product product must not be blank", error.getMessage());
	}

	@Test
	public void respondsWithBadRequest() throws Exception {
		Method method = RestErrorHandler.class.getMethod("handleValidationException",
				MethodArgumentNotValidException.class);

		assertEquals(HttpStatus.BAD_REQUEST, method.getAnnotation(ResponseStatus.class).value());
	}

	private static MethodParameter methodParameter() throws Exception {
		Method method = RestErrorHandlerValidationTest.class.getDeclaredMethod("dummyMethod", String.class);
		return new MethodParameter(method, 0);
	}

	private void dummyMethod(String value) {
	}
}
