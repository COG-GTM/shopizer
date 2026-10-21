package com.salesmanager.shop.store.api.v1.user;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserApiUpdateTest {

	@Mock
	private UserFacade userFacade;

	@InjectMocks
	private UserApi userApi;

	@Test
	public void updateWithoutAuthenticatedUserIsRejectedBeforeFacadeUpdate() {
		when(userFacade.authenticatedUser()).thenReturn(null);

		try {
			userApi.update(new PersistableUser(), 5L, new MerchantStore(), new Language("en"));
			fail("expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			// expected
		}
		verify(userFacade, never()).authorizedGroups(any(), any(PersistableUser.class));
		verify(userFacade, never()).update(anyLong(), anyString(), any(MerchantStore.class), any(PersistableUser.class));
	}

	@Test
	public void updateChecksGroupsThenDelegatesWithAuthenticatedCaller() {
		PersistableUser body = new PersistableUser();
		MerchantStore store = new MerchantStore();
		ReadableUser updated = new ReadableUser();
		when(userFacade.authenticatedUser()).thenReturn("caller@test.com");
		when(userFacade.update(5L, "caller@test.com", store, body)).thenReturn(updated);

		assertSame(updated, userApi.update(body, 5L, store, new Language("en")));

		InOrder order = inOrder(userFacade);
		order.verify(userFacade).authorizedGroups("caller@test.com", body);
		order.verify(userFacade).update(5L, "caller@test.com", store, body);
	}

	@Test
	public void superadminGroupGrantRejectionPropagates() {
		PersistableUser body = new PersistableUser();
		when(userFacade.authenticatedUser()).thenReturn("caller@test.com");
		org.mockito.Mockito.doThrow(new UnauthorizedException("Superadmin group not allowed")).when(userFacade)
				.authorizedGroups("caller@test.com", body);

		try {
			userApi.update(body, 5L, new MerchantStore(), new Language("en"));
			fail("expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			// expected
		}
		verify(userFacade, never()).update(anyLong(), anyString(), any(MerchantStore.class), any(PersistableUser.class));
	}
}
