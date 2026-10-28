package com.salesmanager.shop.store.facade.category;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.salesmanager.shop.store.api.exception.UnauthorizedException;

/**
 * Store scoping of {@link CategoryFacadeImpl} lookups (category IDOR fix, PR #196).
 */
@RunWith(MockitoJUnitRunner.class)
public class CategoryFacadeImplStoreScopeTest {

	private static final Long FOREIGN_ID = 42L;
	private static final Long OWN_ID = 7L;
	private static final Long UNKNOWN_ID = 999L;

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

	@Test
	public void allLanguagesGetOfForeignCategoryIsNotFoundNotUnauthorized() {
		when(categoryService.getById(FOREIGN_ID, callerStore.getId())).thenReturn(category(FOREIGN_ID, otherStore));
		try {
			categoryFacade.getById(callerStore, FOREIGN_ID, null);
			fail("expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			// 404 that does not reveal the id exists in another store
		} catch (UnauthorizedException e) {
			fail("foreign category must be reported as not found, not unauthorized");
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void allLanguagesGetOfUnknownCategoryIsNotFound() {
		categoryFacade.getById(callerStore, UNKNOWN_ID, null);
	}

	@Test(expected = ResourceNotFoundException.class)
	public void categoryWithoutOwnerStoreIsNotFound() throws Exception {
		when(categoryService.getById(OWN_ID, callerStore.getId())).thenReturn(category(OWN_ID, null));
		try {
			categoryFacade.saveCategory(callerStore, persistable(OWN_ID, true));
		} finally {
			verify(categoryService, never()).saveOrUpdate(any(Category.class));
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void deleteOfUnknownCategoryIsNotFoundAndUsesScopedLookup() throws Exception {
		try {
			categoryFacade.deleteCategory(UNKNOWN_ID, callerStore);
		} finally {
			verify(categoryService).getById(UNKNOWN_ID, callerStore.getId());
			verify(categoryService, never()).getById(anyLong());
			verify(categoryService, never()).delete(any(Category.class));
		}
	}

	@Test
	public void setVisibleOnForeignCategoryReturnedByServiceIsNotFoundNotServerError() throws Exception {
		Category foreign = category(FOREIGN_ID, otherStore);
		foreign.setVisible(true);
		when(categoryService.getById(FOREIGN_ID, callerStore.getId())).thenReturn(foreign);
		try {
			categoryFacade.setVisible(persistable(FOREIGN_ID, false), callerStore);
			fail("expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			// not wrapped as ServiceRuntimeException (500)
		}
		assertTrue("foreign category visibility must not change", foreign.isVisible());
		verify(categoryService, never()).saveOrUpdate(any(Category.class));
	}

	@Test
	public void setVisibleOnOwnCategoryUpdatesFlag() throws Exception {
		Category own = category(OWN_ID, callerStore);
		own.setVisible(true);
		when(categoryService.getById(OWN_ID, callerStore.getId())).thenReturn(own);

		categoryFacade.setVisible(persistable(OWN_ID, false), callerStore);

		assertFalse(own.isVisible());
		verify(categoryService).saveOrUpdate(own);
		verify(categoryService, never()).getById(anyLong());
	}

	@Test
	public void createWithNullIdDoesNotLookUpExistingCategory() throws Exception {
		PersistableCategory request = persistable(null, true);
		when(persistableCatagoryPopulator.populate(any(PersistableCategory.class), any(Category.class),
				any(MerchantStore.class), any())).thenAnswer(inv -> inv.getArgument(1));

		categoryFacade.saveCategory(callerStore, request);

		verify(categoryService, never()).getById(anyLong());
		verify(categoryService, never()).getById(anyLong(), anyInt());
		verify(categoryService).saveOrUpdate(any(Category.class));
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

	private static PersistableCategory persistable(Long id, boolean visible) {
		PersistableCategory category = new PersistableCategory();
		category.setId(id);
		category.setCode("code-" + id);
		category.setVisible(visible);
		return category;
	}
}
