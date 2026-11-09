package com.salesmanager.test.shop.integration.cache;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.test.shop.common.Pr350IntegrationSupport;

class HibernateJCacheIntegrationTest extends Pr350IntegrationSupport {
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired javax.cache.CacheManager cacheManager;
    @Autowired CountryService countries;
    @Autowired LanguageService languages;

    @Test
    void hibernateUsesSpringManagerAndDeclaredRegions() {
        assertSame(cacheManager, entityManagerFactory.getProperties().get("hibernate.javax.cache.cache_manager"));
        for (String region : List.of(Country.class.getName(),
                "com.salesmanager.core.model.reference.currency.Currency", Language.class.getName(),
                "default-query-results-region", "default-update-timestamps-region")) {
            assertNotNull(cacheManager.getCache(region), region);
        }
    }

    @Test
    void referenceEntitiesArePresentInSecondLevelCacheAfterServiceLoads() throws Exception {
        Country country = countries.getByCode("CA");
        Language language = languages.getByCode("en");
        assertNotNull(country);
        assertNotNull(language);
        assertEquals(country.getId(), countries.getById(country.getId()).getId());
        assertEquals(language.getId(), languages.getById(language.getId()).getId());
        assertEquals(country.getId(), countries.getById(country.getId()).getId());
        assertEquals(language.getId(), languages.getById(language.getId()).getId());
        assertTrue(entityManagerFactory.getCache().contains(Country.class, country.getId()));
        assertTrue(entityManagerFactory.getCache().contains(Language.class, language.getId()));
    }
}
