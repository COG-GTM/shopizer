package com.salesmanager.test.hibernate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;

import org.springframework.data.jpa.repository.Query;

public class HqlHibernate6LintTest {

	private static final Pattern JOIN_PATH = Pattern.compile(
			"\\bjoin\\s+(?:fetch\\s+)?([\\w.]+)(?:\\s+(?:as\\s+)?(\\w+))?", Pattern.CASE_INSENSITIVE);
	private static final Pattern FETCH_PATH = Pattern.compile(
			"\\bjoin\\s+fetch\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern FROM_ALIAS = Pattern.compile(
			"\\bfrom\\s+[\\w.]+\\s+(?:as\\s+)?(\\w+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern GLUED_KEYWORD = Pattern.compile(
			"\\b\\w+(?:left|inner|join|where)\\s+(?:join|fetch)\\b", Pattern.CASE_INSENSITIVE);
	private static final List<String> CLAUSES;

	static {
		List<String> clauses = new ArrayList<>();
		Collections.addAll(clauses, "left", "right", "inner", "outer", "join", "fetch", "where", "order",
				"group", "by", "on", "with", "and", "or", "having", "as");
		CLAUSES = Collections.unmodifiableList(clauses);
	}

	@Test
	public void repositoryQueriesHaveUniqueAliasesAndFetchPaths() throws Exception {
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		CachingMetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
		Resource[] resources = resolver.getResources(
				"classpath*:com/salesmanager/core/business/repositories/**/*.class");
		int queryCount = 0;

		for (Resource resource : resources) {
			MetadataReader reader = metadataReaderFactory.getMetadataReader(resource);
			if (!reader.getClassMetadata().isInterface()) {
				continue;
			}
			Class<?> repository = Class.forName(reader.getClassMetadata().getClassName());
			for (java.lang.reflect.Method method : repository.getDeclaredMethods()) {
				Query query = method.getAnnotation(Query.class);
				if (query == null || query.nativeQuery()) {
					continue;
				}
				queryCount += lintQuery(repository, method.getName(), "value", query.value());
				if (!query.countQuery().isEmpty()) {
					queryCount += lintQuery(repository, method.getName(), "countQuery", query.countQuery());
				}
			}
		}
		assertTrue("No repository @Query values were scanned", queryCount > 0);
	}

	private int lintQuery(Class<?> repository, String method, String kind, String hql) {
		String label = repository.getSimpleName() + "." + method + " " + kind;
		List<String> aliases = new ArrayList<>();
		List<String> fetchPaths = new ArrayList<>();

		Matcher froms = FROM_ALIAS.matcher(hql);
		while (froms.find()) {
			aliases.add(froms.group(1));
		}
		Matcher joinedPaths = JOIN_PATH.matcher(hql);
		while (joinedPaths.find()) {
			String alias = joinedPaths.group(2);
			if (alias != null && !CLAUSES.contains(alias.toLowerCase())) {
				aliases.add(alias);
			}
		}
		Matcher fetchedPaths = FETCH_PATH.matcher(hql);
		while (fetchedPaths.find()) {
			fetchPaths.add(fetchedPaths.group(1));
		}

		assertFalse(label + " declares an alias twice: " + duplicates(aliases), hasDuplicates(aliases));
		assertFalse(label + " fetches a path twice: " + duplicates(fetchPaths), hasDuplicates(fetchPaths));
		assertFalse(label + " contains a glued HQL keyword", GLUED_KEYWORD.matcher(hql).find());
		return 1;
	}

	private boolean hasDuplicates(Collection<String> values) {
		return values.size() != new java.util.HashSet<>(values).size();
	}

	private String duplicates(Collection<String> values) {
		java.util.Set<String> seen = new java.util.HashSet<>();
		List<String> duplicates = new ArrayList<>();
		for (String value : values) {
			if (!seen.add(value)) {
				duplicates.add(value);
			}
		}
		return duplicates.toString();
	}
}
