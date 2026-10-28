package com.salesmanager.shop.store.facade.category;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.category.CategoryService;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.populator.catalog.PersistableCategoryPopulator;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.class)
public class CategoryFacadeImplTest {

	private static final Long FOREIGN_CATEGORY_ID = 42L;
	private static final Long OWN_CATEGORY_ID = 7L;

	@Mock
	private CategoryService categoryService;

	@Mock
	private PersistableCategoryPopulator persistableCatagoryPopulator;

	@InjectMocks
	private CategoryFacadeImpl categoryFacade;

	private MerchantStore callerStore;
	private MerchantStore otherStore;

	@Before
	public void setUp() {
		callerStore = store(2, "GLOBEX");
		otherStore = store(1, "ACME");
	}

	@Test(expected = ResourceNotFoundException.class)
	public void updateOfAnotherStoresCategoryIsNotFound() throws Exception {
		try {
			categoryFacade.saveCategory(callerStore, persistable(FOREIGN_CATEGORY_ID));
		} finally {
			verify(categoryService).getById(FOREIGN_CATEGORY_ID, callerStore.getId());
			verify(categoryService, never()).getById(anyLong());
			verify(categoryService, never()).saveOrUpdate(any(Category.class));
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void updateRejectsCategoryOwnedByAnotherStoreEvenIfReturned() throws Exception {
		when(categoryService.getById(FOREIGN_CATEGORY_ID, callerStore.getId()))
				.thenReturn(category(FOREIGN_CATEGORY_ID, otherStore));
		try {
			categoryFacade.saveCategory(callerStore, persistable(FOREIGN_CATEGORY_ID));
		} finally {
			verify(categoryService, never()).saveOrUpdate(any(Category.class));
		}
	}

	@Test
	public void updateOfOwnCategoryUsesStoreScopedLookup() throws Exception {
		Category own = category(OWN_CATEGORY_ID, callerStore);
		PersistableCategory request = persistable(OWN_CATEGORY_ID);
		when(categoryService.getById(OWN_CATEGORY_ID, callerStore.getId())).thenReturn(own);
		when(persistableCatagoryPopulator.populate(eq(request), eq(own), eq(callerStore), any()))
				.thenReturn(own);

		categoryFacade.saveCategory(callerStore, request);

		verify(categoryService).saveOrUpdate(own);
		verify(categoryService, never()).getById(anyLong());
	}

	@Test
	public void createDoesNotLookUpExistingCategory() throws Exception {
		PersistableCategory request = persistable(0L);
		when(persistableCatagoryPopulator.populate(eq(request), any(Category.class), eq(callerStore), any()))
				.thenAnswer(inv -> inv.getArgument(1));

		categoryFacade.saveCategory(callerStore, request);

		verify(categoryService, never()).getById(anyLong());
		verify(categoryService, never()).getById(anyLong(), anyInt());
		verify(categoryService).saveOrUpdate(any(Category.class));
	}

	@Test(expected = ResourceNotFoundException.class)
	public void deleteOfAnotherStoresCategoryIsNotFound() throws Exception {
		try {
			categoryFacade.deleteCategory(FOREIGN_CATEGORY_ID, callerStore);
		} finally {
			verify(categoryService).getById(FOREIGN_CATEGORY_ID, callerStore.getId());
			verify(categoryService, never()).getById(anyLong());
			verify(categoryService, never()).delete(any(Category.class));
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void deleteRejectsCategoryOwnedByAnotherStoreEvenIfReturned() throws Exception {
		when(categoryService.getById(FOREIGN_CATEGORY_ID, callerStore.getId()))
				.thenReturn(category(FOREIGN_CATEGORY_ID, otherStore));
		try {
			categoryFacade.deleteCategory(FOREIGN_CATEGORY_ID, callerStore);
		} finally {
			verify(categoryService, never()).delete(any(Category.class));
		}
	}

	@Test
	public void deleteOfOwnCategory() throws Exception {
		Category own = category(OWN_CATEGORY_ID, callerStore);
		when(categoryService.getById(OWN_CATEGORY_ID, callerStore.getId())).thenReturn(own);

		categoryFacade.deleteCategory(OWN_CATEGORY_ID, callerStore);

		verify(categoryService).delete(own);
	}

	@Test(expected = ResourceNotFoundException.class)
	public void setVisibleOnAnotherStoresCategoryIsNotFound() throws Exception {
		try {
			categoryFacade.setVisible(persistable(FOREIGN_CATEGORY_ID), callerStore);
		} finally {
			verify(categoryService, never()).saveOrUpdate(any(Category.class));
		}
	}

	@Test
	public void foreignCategoryIsLeftUntouched() throws Exception {
		Category foreign = category(FOREIGN_CATEGORY_ID, otherStore);
		when(categoryService.getById(FOREIGN_CATEGORY_ID, callerStore.getId())).thenReturn(foreign);
		try {
			categoryFacade.saveCategory(callerStore, persistable(FOREIGN_CATEGORY_ID));
		} catch (ResourceNotFoundException expected) {
			// expected
		}
		assertSame(otherStore, foreign.getMerchantStore());
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		store.setDefaultLanguage(new Language());
		return store;
	}

	private static Category category(Long id, MerchantStore owner) {
		Category category = new Category();
		category.setId(id);
		category.setMerchantStore(owner);
		return category;
	}

	private static PersistableCategory persistable(Long id) {
		PersistableCategory category = new PersistableCategory();
		category.setId(id);
		category.setCode("hijacked");
		return category;
	}
}
