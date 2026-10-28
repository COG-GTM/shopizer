package com.salesmanager.shop.store.facade.category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
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
import com.salesmanager.shop.store.api.exception.UnauthorizedException;

@RunWith(MockitoJUnitRunner.class)
public class CategoryFacadeImplScopeTest {

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
	public void setUp() {
		acme = store(1, "ACME");
		globex = store(2, "GLOBEX");
		english = new Language();
		english.setCode("en");

		when(readableCategoryMapper.convert(any(Category.class), any(MerchantStore.class), nullable(Language.class)))
				.thenAnswer(invocation -> readable(invocation.getArgument(0)));
	}

	@Test
	public void visibleLookupPassesNullLanguageToMapper() throws Exception {
		Category root = category(42L, acme, true, "/42/", null);
		when(categoryService.getById(42L, acme.getId())).thenReturn(root);
		when(categoryService.getListByLineage(acme, "/42/")).thenReturn(Collections.emptyList());

		ReadableCategory result = categoryFacade.getVisibleById(acme, 42L, null);

		assertEquals(Long.valueOf(42L), result.getId());
		verify(readableCategoryMapper).convert(root, acme, null);
		verify(categoryService, never()).getOneByLanguage(anyLong(), any());
	}

	@Test(expected = UnauthorizedException.class)
	public void adminLookupRejectsCategoryFromAnotherStore() {
		when(categoryService.getById(42L, acme.getId())).thenReturn(category(42L, globex, true, "/42/", null));

		categoryFacade.getById(acme, 42L, english);
	}

	@Test(expected = UnauthorizedException.class)
	public void visibleLookupRejectsCategoryFromAnotherStore() {
		when(categoryService.getById(42L, acme.getId())).thenReturn(category(42L, globex, true, "/42/", null));

		categoryFacade.getVisibleById(acme, 42L, english);
	}

	@Test
	public void publicLookupKeepsVisibleChildAndAdminLookupKeepsHiddenGrandchild() throws Exception {
		Category root = category(42L, acme, true, "/42/", null);
		Category child = category(43L, acme, true, "/42/43/", root);
		Category hiddenGrandchild = category(44L, acme, false, "/42/43/44/", child);
		when(categoryService.getById(42L, acme.getId())).thenReturn(root);
		when(categoryService.getListByLineage(acme, "/42/")).thenReturn(Arrays.asList(child, hiddenGrandchild));

		ReadableCategory publicResult = categoryFacade.getVisibleById(acme, 42L, english);
		ReadableCategory adminResult = categoryFacade.getById(acme, 42L, english);

		assertEquals(1, publicResult.getChildren().size());
		assertEquals(Long.valueOf(43L), publicResult.getChildren().get(0).getId());
		assertTrue(publicResult.getChildren().get(0).getChildren().isEmpty());
		assertEquals(1, adminResult.getChildren().size());
		assertEquals(Long.valueOf(44L), adminResult.getChildren().get(0).getChildren().get(0).getId());
	}

	@Test(expected = NullPointerException.class)
	public void visibleLookupRejectsNullCategoryId() {
		categoryFacade.getVisibleById(acme, null, english);
	}

	@Test(expected = NullPointerException.class)
	public void visibleLookupRejectsNullStore() {
		categoryFacade.getVisibleById(null, 42L, english);
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
			com.salesmanager.shop.model.catalog.category.Category parent =
					new com.salesmanager.shop.model.catalog.category.Category();
			parent.setId(source.getParent().getId());
			readable.setParent(parent);
		}
		return readable;
	}
}
