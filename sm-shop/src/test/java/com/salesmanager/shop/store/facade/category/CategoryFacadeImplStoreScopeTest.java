package com.salesmanager.shop.store.facade.category;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.category.CategoryService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.ReadableCategoryMapper;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.populator.catalog.PersistableCategoryPopulator;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.class)
public class CategoryFacadeImplStoreScopeTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private MerchantStoreService merchantStoreService;

    @Mock
    private PersistableCategoryPopulator persistableCategoryPopulator;

    @Mock
    private ReadableCategoryMapper readableCategoryMapper;

    @Mock
    private ProductAttributeService productAttributeService;

    @InjectMocks
    private CategoryFacadeImpl categoryFacade;

    private MerchantStore store;

    @Before
    public void setUp() {
        store = new MerchantStore();
        store.setId(2);
        store.setCode("storeb");
        store.setDefaultLanguage(new Language());
    }

    @Test(expected = ResourceNotFoundException.class)
    public void saveCategoryRejectsAnIdOutsideTheStore() throws Exception {
        PersistableCategory category = new PersistableCategory();
        category.setId(5L);
        when(categoryService.getById(5L, 2)).thenReturn(null);

        try {
            categoryFacade.saveCategory(store, category);
        } finally {
            verify(categoryService).getById(5L, 2);
            verify(categoryService, never()).getById(5L);
            verify(categoryService, never()).findById(5L);
            verify(categoryService, never()).saveOrUpdate(any(Category.class));
            verify(categoryService, never()).create(any(Category.class));
            verify(categoryService, never()).update(any(Category.class));
            verifyNoInteractions(persistableCategoryPopulator);
        }
    }

    @Test(expected = ResourceNotFoundException.class)
    public void deleteCategoryRejectsAnIdOutsideTheStore() throws Exception {
        when(categoryService.getById(5L, 2)).thenReturn(null);

        try {
            categoryFacade.deleteCategory(5L, store);
        } finally {
            verify(categoryService).getById(5L, 2);
            verify(categoryService, never()).getById(5L);
            verify(categoryService, never()).findById(5L);
            verify(categoryService, never()).delete(any(Category.class));
        }
    }

    @Test
    public void deleteCategoryDeletesTheCategoryFoundInTheStore() throws Exception {
        Category category = new Category();
        category.setMerchantStore(store);
        when(categoryService.getById(5L, 2)).thenReturn(category);

        categoryFacade.deleteCategory(5L, store);

        verify(categoryService).delete(category);
    }

    @Test
    public void newCategoriesWithNullOrZeroIdDoNotLookUpAnExistingCategory() throws Exception {
        for (Long id : new Long[] { null, 0L }) {
            PersistableCategory input = new PersistableCategory();
            input.setId(id);
            Category populated = new Category();
            when(persistableCategoryPopulator.populate(eq(input), any(Category.class), eq(store), any()))
                    .thenReturn(populated);

            assertNotNull(categoryFacade.saveCategory(store, input));
        }

        verify(categoryService, never()).getById(anyLong(), anyInt());
        verify(categoryService, never()).getById(anyLong());
        verify(categoryService, never()).findById(anyLong());
        verify(categoryService, never()).getById(any(MerchantStore.class), any(Long.class));
        verify(categoryService, org.mockito.Mockito.times(2)).saveOrUpdate(any(Category.class));
    }
}
