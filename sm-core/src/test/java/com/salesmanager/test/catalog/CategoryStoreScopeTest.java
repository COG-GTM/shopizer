package com.salesmanager.test.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.catalog.category.CategoryDescription;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;

public class CategoryStoreScopeTest extends com.salesmanager.test.common.AbstractSalesManagerCoreTestCase {

    @Test
    public void storeScopedLookupFindsCategoriesWithAndWithoutDescriptions() throws Exception {
        MerchantStore store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
        Language en = languageService.getByCode("en");
        Language fr = languageService.getByCode("fr");
        String suffix = UUID.randomUUID().toString().replace("-", "");

        Category withoutDescriptions = new Category();
        withoutDescriptions.setMerchantStore(store);
        withoutDescriptions.setCode("scope-empty-" + suffix);

        Category withDescriptions = new Category();
        withDescriptions.setMerchantStore(store);
        withDescriptions.setCode("scope-i18n-" + suffix);

        CategoryDescription english = new CategoryDescription();
        english.setName("English");
        english.setCategory(withDescriptions);
        english.setLanguage(en);

        CategoryDescription french = new CategoryDescription();
        french.setName("Français");
        french.setCategory(withDescriptions);
        french.setLanguage(fr);

        Set<CategoryDescription> descriptions = new HashSet<>();
        descriptions.add(english);
        descriptions.add(french);
        withDescriptions.setDescriptions(descriptions);

        try {
            categoryService.create(withoutDescriptions);
            categoryService.create(withDescriptions);

            assertNotNull(categoryService.getById(withoutDescriptions.getId(), store.getId()));
            Category fetched = categoryService.getById(withDescriptions.getId(), store.getId());
            assertNotNull(fetched);
            assertEquals(2, fetched.getDescriptions().size());
            assertNull(categoryService.getById(withoutDescriptions.getId(), store.getId() + 1000));
            assertNull(categoryService.getById(withDescriptions.getId(), store.getId() + 1000));
        } finally {
            if (withDescriptions.getId() != null) {
                categoryService.delete(withDescriptions);
            }
            if (withoutDescriptions.getId() != null) {
                categoryService.delete(withoutDescriptions);
            }
        }
    }
}
