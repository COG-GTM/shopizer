package com.salesmanager.test.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.core.business.services.search.SearchServiceImpl;
import modules.commons.search.SearchModule;

class SearchServiceModuleSelectionTest {
    private SearchModule module(String code) {
        SearchModule module = mock(SearchModule.class);
        when(module.getUniqueCode()).thenReturn(code);
        return module;
    }

    private SearchServiceImpl service(String engine, List<SearchModule> modules) {
        SearchServiceImpl service = new SearchServiceImpl();
        ReflectionTestUtils.setField(service, "searchEngine", engine);
        ReflectionTestUtils.setField(service, "searchModules", modules);
        ReflectionTestUtils.setField(service, "noIndex", true);
        return service;
    }

    @Test
    void declaredDefaultSelectsOpensearchNotFirstBean() throws Exception {
        Value value = SearchServiceImpl.class.getDeclaredField("searchEngine").getAnnotation(Value.class);
        assertEquals("${search.engine:opensearch}", value.value());
        String defaultEngine = value.value().substring(value.value().indexOf(':') + 1, value.value().length() - 1);
        SearchModule elastic = module("elasticsearch");
        SearchModule open = module("opensearch");
        SearchServiceImpl service = service(defaultEngine, List.of(elastic, open));
        service.init();
        assertSame(open, ReflectionTestUtils.getField(service, "searchModule"));
        verify(open, never()).configure(any());
        verify(elastic, never()).configure(any());
    }

    @Test
    void selectsElasticsearchCaseInsensitively() throws Exception {
        SearchModule open = module("opensearch");
        SearchModule elastic = module("elasticsearch");
        SearchServiceImpl service = service("ElasticSearch", List.of(open, elastic));
        service.init();
        assertSame(elastic, ReflectionTestUtils.getField(service, "searchModule"));
    }

    @Test
    void unknownEngineAndEmptyBeansLeaveSearchDisabled() throws Exception {
        SearchModule open = module("opensearch");
        SearchServiceImpl service = service("unknown", List.of(open));
        service.init();
        assertNull(ReflectionTestUtils.getField(service, "searchModule"));
        verify(open, never()).configure(any());
        SearchServiceImpl empty = service("opensearch", List.of());
        assertDoesNotThrow(empty::init);
        assertNull(ReflectionTestUtils.getField(empty, "searchModule"));
    }
}
