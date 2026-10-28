package com.salesmanager.test.shop.unit.facade;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.GenericRuntimeException;
import com.salesmanager.shop.store.facade.customer.CustomerFacadeImpl;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;

public class MissingResetRequestFacadeTest {
    @Test
    public void customerWithoutResetRequestIsRejectedExplicitly() {
        CustomerService service = mock(CustomerService.class);
        when(service.getByPasswordResetToken("DEFAULT", "token")).thenReturn(new Customer());
        CustomerFacadeImpl facade = new CustomerFacadeImpl();
        ReflectionTestUtils.setField(facade, "customerService", service);
        try {
            facade.verifyPasswordRequestToken("token", "DEFAULT");
            fail("Missing reset request must be rejected");
        } catch (GenericRuntimeException expected) {
            assertEquals("No reset request found for token [token]", expected.getErrorMessage());
        }
    }

    @Test
    public void userWithoutResetRequestIsRejectedExplicitly() {
        UserService service = mock(UserService.class);
        when(service.getByPasswordResetToken("DEFAULT", "token")).thenReturn(new User());
        UserFacadeImpl facade = new UserFacadeImpl();
        ReflectionTestUtils.setField(facade, "userService", service);
        try {
            facade.verifyPasswordRequestToken("token", "DEFAULT");
            fail("Missing reset request must be rejected");
        } catch (GenericRuntimeException expected) {
            assertEquals("No reset request found for token [token]", expected.getErrorMessage());
        }
    }
}
