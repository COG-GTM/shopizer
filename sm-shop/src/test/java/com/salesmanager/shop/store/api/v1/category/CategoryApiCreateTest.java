package com.salesmanager.shop.store.api.v1.category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.store.controller.category.facade.CategoryFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

/**
 * {@link CategoryApi#create} must never let a client-supplied id target an existing category (PR #196).
 */
@RunWith(MockitoJUnitRunner.class)
public class CategoryApiCreateTest {

	@Mock
	private CategoryFacade categoryFacade;

	@Mock
	private UserFacade userFacade;

	@InjectMocks
	private CategoryApi categoryApi;

	@Test
	public void createIgnoresClientSuppliedId() {
		assertCreateSendsZeroId(42L);
	}

	@Test
	public void createWithoutIdStillSendsZeroId() {
		assertCreateSendsZeroId(null);
	}

	private void assertCreateSendsZeroId(Long clientId) {
		MerchantStore store = new MerchantStore();
		store.setId(2);
		store.setCode("GLOBEX");
		PersistableCategory request = new PersistableCategory();
		request.setId(clientId);
		request.setCode("new-category");
		when(userFacade.authenticatedUser()).thenReturn("admin@globex.test");
		when(categoryFacade.saveCategory(eq(store), any(PersistableCategory.class))).thenReturn(request);

		PersistableCategory result = categoryApi.create(request, store, new Language());

		ArgumentCaptor<PersistableCategory> saved = ArgumentCaptor.forClass(PersistableCategory.class);
		verify(categoryFacade).saveCategory(eq(store), saved.capture());
		assertEquals(Long.valueOf(0L), saved.getValue().getId());
		assertSame(request, result);
	}
}
