package com.salesmanager.core.business.modules.search.elasticsearch;

import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import modules.commons.search.SearchModule;
import modules.commons.search.configuration.SearchConfiguration;
import modules.commons.search.request.Aggregation;
import modules.commons.search.request.Document;
import modules.commons.search.request.IndexItem;
import modules.commons.search.request.RequestOptions;
import modules.commons.search.request.SearchFilter;
import modules.commons.search.request.SearchItem;
import modules.commons.search.request.SearchRequest;
import modules.commons.search.request.SearchResponse;

/**
 * {@link SearchModule} backed by the Elasticsearch 8 Java API client ({@code co.elastic.clients:elasticsearch-java}).
 * <p>
 * Uses the same index layout ({@code products_<lang>}, {@code keywords_<lang>}), document shape and queries as the
 * shopizer OpenSearch starter so both engines are interchangeable through {@code search.engine}.
 */
public class ElasticsearchSearchModule implements SearchModule {

	public static final String UNIQUE_CODE = "elasticsearch";

	private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchSearchModule.class);

	private static final String PRODUCTS_INDEX = "products_";
	private static final String KEYWORDS_INDEX = "keywords_";
	private static final String DEFAULT_AGGREGATION = "aggregations";
	private static final String DEFAULT_LANGUAGE = "en";
	private static final String TRUSTSTORE_PASSWORD = "instaclustr";

	private final ObjectMapper mapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private ElasticsearchClient client;

	@Override
	public synchronized void configure(SearchConfiguration configuration) throws Exception {
		Validate.notNull(configuration, "SearchConfiguration cannot be null");
		Validate.notEmpty(configuration.getHosts(), "SearchConfiguration.hosts cannot be empty");

		if (client != null) {
			client._transport().close();
		}
		client = new ElasticsearchClient(new RestClientTransport(restClient(configuration), new JacksonJsonpMapper(mapper)));

		List<String> languages = CollectionUtils.isEmpty(configuration.getLanguages())
				? List.of(DEFAULT_LANGUAGE)
				: configuration.getLanguages();

		for (String language : languages) {
			createIndex(PRODUCTS_INDEX, language, configuration.getProductMappings().get(language),
					configuration.getSettings().get(language));
			createIndex(KEYWORDS_INDEX, language, configuration.getKeywordsMappings().get(language),
					configuration.getSettings().get(language));
		}
	}

	private RestClient restClient(SearchConfiguration configuration) throws Exception {
		HttpHost[] hosts = configuration.getHosts().stream()
				.map(h -> new HttpHost(h.getHost(), h.getPort(), h.getScheme()))
				.toArray(HttpHost[]::new);

		RestClientBuilder builder = RestClient.builder(hosts);

		BasicCredentialsProvider credentialsProvider = null;
		if (configuration.getCredentials() != null
				&& StringUtils.isNotBlank(configuration.getCredentials().getUserName())) {
			credentialsProvider = new BasicCredentialsProvider();
			credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(
					configuration.getCredentials().getUserName(), configuration.getCredentials().getPassword()));
		}
		SSLContext sslContext = sslContext(configuration.getJksAbsolutePath());

		final BasicCredentialsProvider credentials = credentialsProvider;
		builder.setHttpClientConfigCallback(httpClientBuilder -> {
			if (credentials != null) {
				httpClientBuilder.setDefaultCredentialsProvider(credentials);
			}
			if (sslContext != null) {
				httpClientBuilder.setSSLContext(sslContext);
			}
			return httpClientBuilder;
		});
		return builder.build();
	}

	private SSLContext sslContext(String jksAbsolutePath) throws Exception {
		if (StringUtils.isBlank(jksAbsolutePath)) {
			return null;
		}
		KeyStore truststore = KeyStore.getInstance("jks");
		try (InputStream is = Files.newInputStream(Paths.get(jksAbsolutePath))) {
			truststore.load(is, TRUSTSTORE_PASSWORD.toCharArray());
		}
		return SSLContexts.custom().loadTrustMaterial(truststore, null).build();
	}

	private void createIndex(String prefix, String language, String mappings, String settings) throws Exception {
		String index = prefix + language.toLowerCase();
		if (client.indices().exists(e -> e.index(index)).value()) {
			return;
		}
		StringBuilder body = new StringBuilder("{");
		if (StringUtils.isNotBlank(settings)) {
			body.append("\"settings\":").append(settings);
		}
		if (StringUtils.isNotBlank(mappings)) {
			body.append(body.length() > 1 ? "," : "").append("\"mappings\":").append(mappings);
		}
		body.append("}");

		boolean acknowledged = client.indices()
				.create(c -> c.index(index).withJson(new StringReader(body.toString())))
				.acknowledged();
		LOGGER.info("Created search index [{}] acknowledged [{}]", index, acknowledged);
	}

	@Override
	public void index(IndexItem item) throws Exception {
		Validate.notNull(item, "Item must not be null");
		Validate.notNull(item.getLanguage(), "Language must not be null");
		checkConfigured();

		String id = String.valueOf(item.getId());
		client.index(i -> i.index(PRODUCTS_INDEX + item.getLanguage()).id(id).document(fields(item)));

		Map<String, String> keywords = new HashMap<>();
		keywords.put("store", item.getStore());
		keywords.put("suggestions", item.getName());
		client.index(i -> i.index(KEYWORDS_INDEX + item.getLanguage()).id(id).document(keywords));
	}

	@Override
	public void index(List<IndexItem> items) throws Exception {
		for (IndexItem item : items) {
			index(item);
		}
	}

	@Override
	public void delete(List<String> languages, Long id) throws Exception {
		checkConfigured();
		Validate.notEmpty(languages, "Languages cannot be empty");
		for (String language : languages) {
			String lang = language.toLowerCase();
			client.delete(d -> d.index(PRODUCTS_INDEX + lang).id(String.valueOf(id)));
			client.delete(d -> d.index(KEYWORDS_INDEX + lang).id(String.valueOf(id)));
		}
	}

	@Override
	public String getUniqueCode() {
		return UNIQUE_CODE;
	}

	@Override
	public SearchResponse searchKeywords(SearchRequest searchRequest) throws Exception {
		validate(searchRequest);
		checkConfigured();

		Query query = BoolQuery.of(b -> b
				.must(m -> m.multiMatch(mm -> mm
						.query(searchRequest.getSearchString())
						.type(TextQueryType.BoolPrefix)
						.fields("suggestions", "suggestions._2gram", "suggestions._3gram")))
				.filter(storeFilter(searchRequest)))._toQuery();

		@SuppressWarnings("rawtypes")
		co.elastic.clients.elasticsearch.core.SearchResponse<Map> response = client.search(
				s -> s.index(KEYWORDS_INDEX + searchRequest.getLanguage()).query(query), Map.class);

		return toSearchResponse(response);
	}

	@Override
	public SearchResponse searchProducts(SearchRequest searchRequest) throws Exception {
		validate(searchRequest);
		checkConfigured();

		List<Query> filters = new ArrayList<>();
		filters.add(storeFilter(searchRequest));
		if (!CollectionUtils.isEmpty(searchRequest.getFilters())) {
			searchRequest.getFilters().forEach(f -> filters.add(filter(f)));
		}

		Query query = BoolQuery.of(b -> b
				.must(m -> m.multiMatch(mm -> mm
						.query(searchRequest.getSearchString())
						.fields("name", "description", "brand", "category")))
				.filter(filters))._toQuery();

		// as in the opensearch starter, a single terms aggregation named "aggregations" on the last requested field
		String aggregationField = CollectionUtils.isEmpty(searchRequest.getAggregations()) ? null
				: searchRequest.getAggregations().get(searchRequest.getAggregations().size() - 1);

		@SuppressWarnings("rawtypes")
		co.elastic.clients.elasticsearch.core.SearchResponse<Map> response = client.search(s -> {
			s.index(PRODUCTS_INDEX + searchRequest.getLanguage()).query(query);
			if (aggregationField != null) {
				s.aggregations(DEFAULT_AGGREGATION, a -> a.terms(t -> t.field(aggregationField)));
			}
			return s;
		}, Map.class);

		SearchResponse serviceResponse = toSearchResponse(response);

		Aggregate aggregate = response.aggregations().get(DEFAULT_AGGREGATION);
		if (aggregate != null) {
			serviceResponse.getAggregations().addAll(buckets(aggregate));
		}
		return serviceResponse;
	}

	@Override
	public Optional<Document> getDocument(Long id, String language, RequestOptions option) throws Exception {
		checkConfigured();

		@SuppressWarnings("rawtypes")
		GetResponse<Map> response = client.get(g -> g.index(PRODUCTS_INDEX + language).id(String.valueOf(id)),
				Map.class);

		if (response.found() && response.source() != null) {
			Document document = mapper.convertValue(response.source(), Document.class);
			document.setDocumentId(response.id());
			return Optional.of(document);
		}
		if (option == RequestOptions.FAIL_ON_NOT_FOUNT) {
			throw new Exception("Document with id [" + id + "] does not exist in products index");
		}
		return Optional.empty();
	}

	@Override
	public List<Optional<Document>> getDocument(Long id, List<String> languages, RequestOptions option)
			throws Exception {
		Validate.notNull(id, "id cannot be null");
		Validate.notEmpty(languages, "Languages cannot be empty");

		List<Optional<Document>> documents = new ArrayList<>();
		for (String language : languages) {
			try {
				documents.add(getDocument(id, language, option));
			} catch (ElasticsearchException e) {
				if (option == RequestOptions.FAIL_ON_NOT_FOUNT) {
					throw new RuntimeException(
							"Cannot convert to document [" + id + "] with language [" + language + "]", e);
				}
				documents.add(Optional.empty());
			}
		}
		return documents;
	}

	@Override
	public Object getConnection() {
		return client;
	}

	private void checkConfigured() throws Exception {
		if (client == null) {
			throw new Exception(
					"Elasticsearch client has not been initialized. Please run configure(SearchConfiguration) first.");
		}
	}

	private static void validate(SearchRequest searchRequest) {
		Validate.notNull(searchRequest, "SearchRequest must not be null");
		Validate.notNull(searchRequest.getLanguage(), "SearchRequest.language must not be null");
		Validate.notNull(searchRequest.getStore(), "SearchRequest.store must not be null");
	}

	private static Query storeFilter(SearchRequest searchRequest) {
		return Query.of(q -> q.term(t -> t.field("store").value(FieldValue.of(searchRequest.getStore()))));
	}

	private static Query filter(SearchFilter filter) {
		Query term = Query.of(q -> q.term(t -> t.field(filter.getField()).value(FieldValue.of(filter.getValue()))));
		if (filter.isVariant()) {
			return Query.of(q -> q.nested(n -> n.path("variants").query(term).scoreMode(ChildScoreMode.None)));
		}
		return term;
	}

	@SuppressWarnings("rawtypes")
	private SearchResponse toSearchResponse(co.elastic.clients.elasticsearch.core.SearchResponse<Map> response) {
		SearchResponse serviceResponse = new SearchResponse();
		TotalHits total = response.hits().total();
		serviceResponse.setCount(total != null ? total.value() : (long) response.hits().hits().size());
		for (Hit<Map> hit : response.hits().hits()) {
			if (hit.source() != null) {
				serviceResponse.getItems().add(mapper.convertValue(hit.source(), SearchItem.class));
			}
		}
		return serviceResponse;
	}

	private static List<Aggregation> buckets(Aggregate aggregate) {
		List<Aggregation> aggregations = new ArrayList<>();
		if (aggregate.isSterms()) {
			aggregate.sterms().buckets().array()
					.forEach(b -> aggregations.add(aggregation(b.key().stringValue(), b.docCount())));
		} else if (aggregate.isLterms()) {
			aggregate.lterms().buckets().array()
					.forEach(b -> aggregations.add(aggregation(String.valueOf(b.key()), b.docCount())));
		} else if (aggregate.isDterms()) {
			aggregate.dterms().buckets().array()
					.forEach(b -> aggregations.add(aggregation(String.valueOf(b.key()), b.docCount())));
		}
		return aggregations;
	}

	private static Aggregation aggregation(String name, long count) {
		Aggregation aggregation = new Aggregation();
		aggregation.setName(name);
		aggregation.setCount(count);
		return aggregation;
	}

	private static Map<String, Object> fields(IndexItem item) {
		Map<String, Object> document = new HashMap<>();
		for (Field field : IndexItem.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			field.setAccessible(true);
			try {
				document.put(field.getName(), field.get(item));
			} catch (IllegalAccessException e) {
				LOGGER.warn("Cannot read IndexItem field [{}]", field.getName());
			}
		}
		return document;
	}

}
