package com.salesmanager.test.shop.integration.search;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.core.business.modules.search.elasticsearch.ElasticsearchSearchModule;
import com.salesmanager.core.business.services.search.SearchServiceImpl;
import com.salesmanager.test.shop.common.Pr350IntegrationSupport;
import modules.commons.search.SearchModule;

class SearchEngineContextIntegrationTest extends Pr350IntegrationSupport {
    @Autowired SearchServiceImpl searchService;
    @Autowired ElasticsearchSearchModule elasticsearch;

    @Test
    void defaultsToOpensearchAndExposesElasticsearchBean() {
        SearchModule selected = (SearchModule) ReflectionTestUtils.getField(searchService, "searchModule");
        assertNotNull(selected);
        assertEquals("opensearch", selected.getUniqueCode());
        assertEquals("elasticsearch", elasticsearch.getUniqueCode());
    }
}
