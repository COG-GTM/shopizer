package com.salesmanager.test.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.core.business.modules.search.elasticsearch.ElasticsearchSearchModule;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.util.ObjectBuilder;

class ElasticsearchSearchModuleUnitTest {
    private ElasticsearchSearchModule module;
    private ElasticsearchClient client;
    private List<SearchRequest> searches;
    private List<DeleteRequest> deletes;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() throws Exception {
        module = new ElasticsearchSearchModule();
        client = mock(ElasticsearchClient.class);
        ReflectionTestUtils.setField(module, "client", client);
        searches = new ArrayList<>();
        deletes = new ArrayList<>();
        doAnswer(call -> {
            Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> build = call.getArgument(0);
            searches.add(build.apply(new SearchRequest.Builder()).build());
            return co.elastic.clients.elasticsearch.core.SearchResponse.of(r -> r
                    .took(1).timedOut(false).shards(s -> s.total(1).successful(1).failed(0))
                    .hits(h -> h.hits(List.of()).total(t -> t.value(0).relation(
                            co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq))));
        }).when(client).search(any(Function.class), eq(Map.class));
        doAnswer(call -> {
            Function<DeleteRequest.Builder, ObjectBuilder<DeleteRequest>> build = call.getArgument(0);
            deletes.add(build.apply(new DeleteRequest.Builder()).build());
            return null;
        }).when(client).delete(any(Function.class));
    }

    private modules.commons.search.request.SearchRequest request() {
        modules.commons.search.request.SearchRequest request = new modules.commons.search.request.SearchRequest();
        request.setLanguage("fr");
        request.setStore("DEFAULT");
        request.setSearchString("bag");
        return request;
    }

    @Test
    void identifiesModuleAndBuildsProductQueryWithStoreAndAggregation() throws Exception {
        assertEquals("elasticsearch", module.getUniqueCode());
        assertSame(client, module.getConnection());
        modules.commons.search.request.SearchRequest request = request();
        request.setAggregations(List.of("category", "brand"));
        modules.commons.search.request.SearchFilter brand = new modules.commons.search.request.SearchFilter();
        brand.setField("brand");
        brand.setValue("acme");
        modules.commons.search.request.SearchFilter variant = new modules.commons.search.request.SearchFilter();
        variant.setField("variants.color");
        variant.setValue("blue");
        variant.setVariant(true);
        request.setFilters(List.of(brand, variant));
        assertEquals(0L, module.searchProducts(request).getCount());
        SearchRequest sent = searches.get(0);
        assertEquals(List.of("products_fr"), sent.index());
        assertEquals("bag", sent.query().bool().must().get(0).multiMatch().query());
        assertEquals(List.of("name", "description", "brand", "category"),
                sent.query().bool().must().get(0).multiMatch().fields());
        assertEquals("store", sent.query().bool().filter().get(0).term().field());
        assertEquals("DEFAULT", sent.query().bool().filter().get(0).term().value().stringValue());
        assertEquals("brand", sent.aggregations().get("aggregations").terms().field());
        assertEquals(3, sent.query().bool().filter().size());
        assertEquals("brand", sent.query().bool().filter().get(1).term().field());
        assertEquals("acme", sent.query().bool().filter().get(1).term().value().stringValue());
        assertEquals("variants", sent.query().bool().filter().get(2).nested().path());
        assertEquals("variants.color", sent.query().bool().filter().get(2).nested().query().term().field());
        assertEquals("blue", sent.query().bool().filter().get(2).nested().query().term().value().stringValue());
    }

    @Test
    void buildsKeywordPrefixQueryAndReturnsEmptyResults() throws Exception {
        assertTrue(module.searchKeywords(request()).getItems().isEmpty());
        SearchRequest sent = searches.get(0);
        assertEquals(List.of("keywords_fr"), sent.index());
        assertEquals(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BoolPrefix,
                sent.query().bool().must().get(0).multiMatch().type());
        assertEquals("DEFAULT", sent.query().bool().filter().get(0).term().value().stringValue());
    }

    @Test
    void deletesBothIndicesForEveryLanguageAndRejectsEmptyLanguages() throws Exception {
        module.delete(List.of("EN", "fr"), 42L);
        assertEquals(List.of("products_en", "keywords_en", "products_fr", "keywords_fr"),
                deletes.stream().map(DeleteRequest::index).toList());
        assertTrue(deletes.stream().allMatch(d -> "42".equals(d.id())));
        assertThrows(IllegalArgumentException.class, () -> module.delete(List.of(), 42L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validatesRequestsAndPropagatesClientFailure() throws Exception {
        assertThrows(NullPointerException.class, () -> module.searchProducts(null));
        assertThrows(NullPointerException.class,
                () -> module.searchProducts(new modules.commons.search.request.SearchRequest()));
        IOException failure = new IOException("search unavailable");
        doThrow(failure).when(client).search(any(Function.class), eq(Map.class));
        assertSame(failure, assertThrows(IOException.class, () -> module.searchProducts(request())));
        ReflectionTestUtils.setField(module, "client", null);
        assertTrue(assertThrows(Exception.class, () -> module.searchKeywords(request()))
                .getMessage().contains("has not been initialized"));
    }
}
