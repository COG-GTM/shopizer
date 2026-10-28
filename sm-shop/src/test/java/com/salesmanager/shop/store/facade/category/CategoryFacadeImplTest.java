package com.salesmanager.shop.store.facade.category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

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
import com.salesmanager.shop.mapper.catalog.ReadableCategoryMapper;
import com.salesmanager.shop.model.catalog.category.ReadableCategory;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CategoryFacadeImplTest {

	@Mock
	private CategoryService categoryService;

	@Mock
	private ReadableCategoryMapper readableCategoryMapper;

	@InjectMocks
	private CategoryFacadeImpl categoryFacade;

	private MerchantStore acme;
	private MerchantStore globex;
	private Language english;

	@Before
	public void setUp() throws Exception {
		acme = store(1, "ACME");
		globex = store(2, "GLOBEX");
		english = new Language();
		english.setId(1);
		english.setCode("en");

		when(readableCategoryMapper.convert(any(Category.class), any(MerchantStore.class), any()))
				.thenAnswer(inv -> readable(inv.getArgument(0)));
	}

	@Test(expected = ResourceNotFoundException.class)
	public void publicLookupOfAnotherStoresCategoryIsNotFound() {
		// category 42 belongs to ACME, so a GLOBEX scoped lookup returns nothing
		when(categoryService.getById(42L, globex.getId())).thenReturn(null);
		try {
			categoryFacade.getVisibleById(globex, 42L, english);
		} finally {
			verify(categoryService, never()).getOneByLanguage(anyLong(), any());
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void adminLookupOfAnotherStoresCategoryIsNotFound() {
		when(categoryService.getById(42L, globex.getId())).thenReturn(null);
		categoryFacade.getById(globex, 42L, english);
	}

	@Test
	public void lookupIsScopedToTheRequestedStore() throws Exception {
		Category category = category(42L, acme, true, "/42/", null);
		when(categoryService.getById(42L, acme.getId())).thenReturn(category);
		when(categoryService.getListByLineage(acme, "/42/")).thenReturn(Collections.emptyList());

		ReadableCategory result = categoryFacade.getVisibleById(acme, 42L, english);

		assertEquals(Long.valueOf(42L), result.getId());
		verify(categoryService).getById(eq(42L), eq(acme.getId()));
		verify(categoryService, never()).getOneByLanguage(anyLong(), any());
	}

	@Test(expected = ResourceNotFoundException.class)
	public void publicLookupOfHiddenCategoryIsNotFound() {
		Category hidden = category(42L, acme, false, "/42/", null);
		when(categoryService.getById(42L, acme.getId())).thenReturn(hidden);
		categoryFacade.getVisibleById(acme, 42L, english);
	}

	@Test
	public void adminLookupReturnsHiddenCategory() throws Exception {
		Category hidden = category(42L, acme, false, "/42/", null);
		when(categoryService.getById(42L, acme.getId())).thenReturn(hidden);
		when(categoryService.getListByLineage(acme, "/42/")).thenReturn(Collections.emptyList());

		ReadableCategory result = categoryFacade.getById(acme, 42L, english);

		assertEquals(Long.valueOf(42L), result.getId());
	}

	@Test
	public void publicLookupDropsHiddenChildrenAndTheirSubtree() throws Exception {
		Category root = category(42L, acme, true, "/42/", null);
		Category visibleChild = category(43L, acme, true, "/42/43/", root);
		Category hiddenChild = category(44L, acme, false, "/42/44/", root);
		Category grandChildOfHidden = category(45L, acme, true, "/42/44/45/", hiddenChild);
		when(categoryService.getById(42L, acme.getId())).thenReturn(root);
		when(categoryService.getListByLineage(acme, "/42/"))
				.thenReturn(Arrays.asList(visibleChild, hiddenChild, grandChildOfHidden));

		ReadableCategory publicResult = categoryFacade.getVisibleById(acme, 42L, english);
		assertEquals(1, publicResult.getChildren().size());
		assertEquals(Long.valueOf(43L), publicResult.getChildren().get(0).getId());
		assertTrue(publicResult.getChildren().get(0).getChildren().isEmpty());

		ReadableCategory adminResult = categoryFacade.getById(acme, 42L, english);
		assertEquals(2, adminResult.getChildren().size());
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	private static Category category(Long id, MerchantStore store, boolean visible, String lineage, Category parent) {
		Category category = new Category();
		category.setId(id);
		category.setMerchantStore(store);
		category.setVisible(visible);
		category.setLineage(lineage);
		category.setParent(parent);
		return category;
	}

	private static ReadableCategory readable(Category source) {
		ReadableCategory readable = new ReadableCategory();
		readable.setId(source.getId());
		readable.setVisible(source.isVisible());
		if (source.getParent() != null) {
			com.salesmanager.shop.model.catalog.category.Category parent = new com.salesmanager.shop.model.catalog.category.Category();
			parent.setId(source.getParent().getId());
			readable.setParent(parent);
		}
		return readable;
	}
}
