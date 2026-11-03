package com.salesmanager.test.shop.unit.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.store.api.exception.ErrorEntity;
import com.salesmanager.shop.store.api.exception.RestErrorHandler;
import com.salesmanager.shop.store.api.v1.shoppingCart.ShoppingCartApi;

class RestErrorHandlerValidationTest {

    @Test
    void mapsValidationFailureToBadRequestErrorEntity() throws Exception {
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(item, "persistableShoppingCartItem");
        bindingResult.addError(new FieldError("persistableShoppingCartItem", "quantity",
                "Quantity must not be negative"));

        Method addToCart = ShoppingCartApi.class.getMethod("addToCart", PersistableShoppingCartItem.class,
                com.salesmanager.core.model.merchant.MerchantStore.class,
                com.salesmanager.core.model.reference.language.Language.class);
        MethodParameter parameter = new MethodParameter(addToCart, 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ErrorEntity error = (ErrorEntity) RestErrorHandler.class
                .getMethod("handleValidationException", MethodArgumentNotValidException.class)
                .invoke(new RestErrorHandler(), exception);

        assertEquals("400", error.getErrorCode());
        assertEquals("Validation failed, quantity: Quantity must not be negative", error.getMessage());
    }

    @Test
    void validationHandlerIsAnnotatedAsBadRequest() throws Exception {
        Method method = RestErrorHandler.class.getMethod("handleValidationException",
                MethodArgumentNotValidException.class);

        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);

        assertNotNull(responseStatus);
        assertEquals(HttpStatus.BAD_REQUEST, responseStatus.value());
    }
}
