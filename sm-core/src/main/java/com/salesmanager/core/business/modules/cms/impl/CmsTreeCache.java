package com.salesmanager.core.business.modules.cms.impl;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.infinispan.Cache;

/**
 * Hierarchical (folder like) view over a flat Infinispan cache.
 * <p>
 * Replaces the infinispan-tree module, which was removed after Infinispan 9.4.
 * Each node is identified by a slash separated path. A node's entries are stored
 * as {@code <path><SEPARATOR><key> -> byte[]} and node existence as {@code <path><SEPARATOR> -> byte[0]},
 * which keeps every cache key and value a String / byte[] that Infinispan can
 * persist without a custom marshaller.
 */
public class CmsTreeCache {

	static final char SEPARATOR = '\u0000';

	private static final byte[] NODE_MARKER = new byte[0];

	private final Cache<String, byte[]> cache;

	public CmsTreeCache(Cache<String, byte[]> cache) {
		this.cache = cache;
	}

	public Cache<String, byte[]> getCache() {
		return cache;
	}

	/**
	 * Returns the node at the given path, creating it when it does not exist.
	 */
	public CmsTreeNode getOrCreateNode(String path) {
		String normalized = normalize(path);
		cache.putIfAbsent(normalized + SEPARATOR, NODE_MARKER);
		return new CmsTreeNode(this, normalized);
	}

	/**
	 * Removes the node at the given path together with all of its entries and descendants.
	 */
	public void removeNode(String path) {
		String normalized = normalize(path);
		String childPrefix = normalized + '/';
		List<String> keys = cache.keySet().stream()
				.filter(k -> nodePath(k).equals(normalized) || nodePath(k).startsWith(childPrefix))
				.collect(Collectors.toList());
		keys.forEach(cache::remove);
	}

	Set<String> keys(String nodePath) {
		String prefix = nodePath + SEPARATOR;
		return cache.keySet().stream()
				.filter(k -> k.startsWith(prefix) && k.length() > prefix.length())
				.map(k -> k.substring(prefix.length()))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	Set<CmsTreeNode> children(String nodePath) {
		String childPrefix = nodePath + '/';
		return cache.keySet().stream()
				.map(CmsTreeCache::nodePath)
				.filter(p -> p.startsWith(childPrefix))
				.map(p -> childPrefix + p.substring(childPrefix.length()).split("/")[0])
				.distinct()
				.map(p -> new CmsTreeNode(this, p))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	static String normalize(String path) {
		return Arrays.stream(path.split("/"))
				.filter(s -> !s.isEmpty())
				.collect(Collectors.joining("/"));
	}

	private static String nodePath(String cacheKey) {
		int index = cacheKey.indexOf(SEPARATOR);
		return index < 0 ? cacheKey : cacheKey.substring(0, index);
	}

}
