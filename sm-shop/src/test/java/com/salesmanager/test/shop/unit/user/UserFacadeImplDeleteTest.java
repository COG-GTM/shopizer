package com.salesmanager.test.shop.unit.user;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UserFacadeImplDeleteTest {

	@Mock
	private UserService userService;

	@InjectMocks
	private UserFacadeImpl userFacade;

	@Test
	public void deleteRejectsSuperadmin() throws Exception {
		User superadmin = userWithGroup(Constants.GROUP_SUPERADMIN);
		when(userService.findByStore(1L, "DEFAULT")).thenReturn(superadmin);

		assertThrows(ServiceRuntimeException.class, () -> userFacade.delete(1L, "DEFAULT"));

		verify(userService, never()).delete(superadmin);
	}

	@Test
	public void deleteDeletesAdmin() throws Exception {
		User admin = userWithGroup(Constants.GROUP_ADMIN);
		when(userService.findByStore(2L, "STORE_A")).thenReturn(admin);

		userFacade.delete(2L, "STORE_A");

		verify(userService).delete(admin);
	}

	@Test
	public void deleteRejectsUserNotInStore() throws Exception {
		when(userService.findByStore(2L, "STORE_A")).thenReturn(null);

		assertThrows(ServiceRuntimeException.class, () -> userFacade.delete(2L, "STORE_A"));

		verify(userService, never()).delete(org.mockito.ArgumentMatchers.any());
	}

	private User userWithGroup(String groupName) {
		User user = new User();
		user.setId(1L);
		Group group = new Group(groupName);
		user.setGroups(Collections.singletonList(group));
		return user;
	}
}
