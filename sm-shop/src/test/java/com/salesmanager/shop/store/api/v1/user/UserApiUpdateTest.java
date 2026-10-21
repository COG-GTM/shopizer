package com.salesmanager.shop.store.api.v1.user;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.class)
public class UserApiUpdateTest {

	@Mock
	private UserFacade userFacade;

	@InjectMocks
	private UserApi userApi;

	@Test
	public void unauthenticatedUpdateStopsBeforeAuthorizationAndDelegation() {
		PersistableUser body = new PersistableUser();
		MerchantStore store = new MerchantStore(1, "DEFAULT", "Default");
		Language language = new Language();
		when(userFacade.authenticatedUser()).thenReturn(null);

		assertThrows(UnauthorizedException.class, () -> userApi.update(body, 7L, store, language));

		verify(userFacade, never()).authorizedGroups(anyString(), any(PersistableUser.class));
		verify(userFacade, never()).update(anyLong(), anyString(), any(MerchantStore.class),
				any(PersistableUser.class));
	}

	@Test
	public void authenticatedUpdateAuthorizesGroupsBeforeDelegating() {
		PersistableUser body = new PersistableUser();
		MerchantStore store = new MerchantStore(1, "DEFAULT", "Default");
		Language language = new Language();
		when(userFacade.authenticatedUser()).thenReturn("a@b.com");

		userApi.update(body, 7L, store, language);

		InOrder order = inOrder(userFacade);
		order.verify(userFacade).authorizedGroups("a@b.com", body);
		order.verify(userFacade).update(7L, "a@b.com", store, body);
	}
}
