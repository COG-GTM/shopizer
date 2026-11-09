package com.salesmanager.core.business.modules.cms.impl;

import java.util.Objects;
import java.util.Set;

/**
 * A node (folder) of a {@link CmsTreeCache} holding named binary entries.
 */
public class CmsTreeNode {

	private final CmsTreeCache tree;
	private final String path;

	CmsTreeNode(CmsTreeCache tree, String path) {
		this.tree = tree;
		this.path = path;
	}

	public String getPath() {
		return path;
	}

	public void put(String key, byte[] value) {
		tree.getCache().put(entryKey(key), value);
	}

	public byte[] get(String key) {
		return tree.getCache().get(entryKey(key));
	}

	public void remove(String key) {
		tree.getCache().remove(entryKey(key));
	}

	public Set<String> getKeys() {
		return tree.keys(path);
	}

	public Set<CmsTreeNode> getChildren() {
		return tree.children(path);
	}

	private String entryKey(String key) {
		return path + CmsTreeCache.SEPARATOR + key;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CmsTreeNode)) {
			return false;
		}
		return path.equals(((CmsTreeNode) o).path);
	}

	@Override
	public int hashCode() {
		return Objects.hash(path);
	}

}
