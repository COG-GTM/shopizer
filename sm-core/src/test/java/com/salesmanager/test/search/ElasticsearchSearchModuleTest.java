package com.salesmanager.test.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.http.HttpHost;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.salesmanager.core.business.modules.search.elasticsearch.ElasticsearchSearchModule;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import modules.commons.search.configuration.SearchConfiguration;
import modules.commons.search.configuration.SearchHost;
import modules.commons.search.request.Document;
import modules.commons.search.request.IndexItem;
import modules.commons.search.request.RequestOptions;
import modules.commons.search.request.SearchRequest;
import modules.commons.search.request.SearchResponse;

/**
 * Runs {@link ElasticsearchSearchModule} against a real Elasticsearch 8 node, skipped when docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class ElasticsearchSearchModuleTest {

	@Container
	static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
			"docker.elastic.co/elasticsearch/elasticsearch:8.18.8")
			.withEnv("xpack.security.enabled", "false")
			.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

	private static final ElasticsearchSearchModule MODULE = new ElasticsearchSearchModule();

	@BeforeAll
	static void configure() throws Exception {
		HttpHost httpHost = HttpHost.create(ELASTICSEARCH.getHttpHostAddress());
		SearchHost host = new SearchHost();
		host.setScheme("http");
		host.setHost(httpHost.getHostName());
		host.setPort(httpHost.getPort());

		SearchConfiguration configuration = new SearchConfiguration();
		configuration.setClusterName("shopizer");
		configuration.getHosts().add(host);
		configuration.getLanguages().add("en");
		configuration.getProductMappings().put("en", resource("search/MAPPINGS.json"));
		configuration.getKeywordsMappings().put("en", resource("search/KEYWORDS_MAPPINGS.json"));
		configuration.getSettings().put("en", resource("search/SETTINGS_en.json"));

		MODULE.configure(configuration);

		MODULE.index(item(1L, "Vintage leather bag", "brown leather bag", "bags", "acme"));
		MODULE.index(item(2L, "Canvas tote", "light canvas tote", "bags", "globex"));
		MODULE.index(item(3L, "Leather wallet", "slim leather wallet", "wallets", "acme"));
		MODULE.index(item(4L, "Leather wallet other store", "leather", "wallets", "acme", "OTHER"));

		((ElasticsearchClient) MODULE.getConnection()).indices().refresh(r -> r.index("products_en", "keywords_en"));
	}

	@Test
	void searchesProductsWithStoreFilterAndAggregation() throws Exception {
		SearchRequest request = request("leather");
		request.setAggregations(List.of("brand"));

		SearchResponse response = MODULE.searchProducts(request);

		assertEquals(2L, response.getCount());
		assertEquals(2, response.getItems().size());
		assertTrue(response.getItems().stream().allMatch(i -> "DEFAULT".equals(i.getStore())));
		assertEquals(1, response.getAggregations().size());
		assertEquals("acme", response.getAggregations().get(0).getName());
		assertEquals(2L, response.getAggregations().get(0).getCount());
	}

	@Test
	void searchesKeywordsByPrefix() throws Exception {
		SearchResponse response = MODULE.searchKeywords(request("vint"));

		assertEquals(1L, response.getCount());
		assertEquals("Vintage leather bag", response.getItems().get(0).getSuggestions());
	}

	@Test
	void getsAndDeletesDocuments() throws Exception {
		Optional<Document> document = MODULE.getDocument(2L, "en", RequestOptions.DO_NOT_FAIL_ON_NOT_FOUND);
		assertTrue(document.isPresent());
		assertEquals("2", document.get().getDocumentId());
		assertEquals("Canvas tote", document.get().getName());

		MODULE.delete(List.of("en"), 2L);

		assertFalse(MODULE.getDocument(2L, "en", RequestOptions.DO_NOT_FAIL_ON_NOT_FOUND).isPresent());
		assertEquals(List.of(Optional.empty()),
				MODULE.getDocument(99L, List.of("en"), RequestOptions.DO_NOT_FAIL_ON_NOT_FOUND));
	}

	private static SearchRequest request(String text) {
		SearchRequest request = new SearchRequest();
		request.setStore("DEFAULT");
		request.setLanguage("en");
		request.setSearchString(text);
		return request;
	}

	private static IndexItem item(Long id, String name, String description, String category, String brand) {
		return item(id, name, description, category, brand, "DEFAULT");
	}

	private static IndexItem item(Long id, String name, String description, String category, String brand,
			String store) {
		IndexItem item = new IndexItem();
		item.setId(id);
		item.setStore(store);
		item.setLanguage("en");
		item.setName(name);
		item.setDescription(description);
		item.setCategory(category);
		item.setBrand(brand);
		return item;
	}

	private static String resource(String path) throws Exception {
		return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}

}
