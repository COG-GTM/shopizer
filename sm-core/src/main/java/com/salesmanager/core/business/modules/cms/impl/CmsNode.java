package com.salesmanager.core.business.modules.cms.impl;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A node of a {@link CmsTreeCache}, identified by its slash separated path.
 */
public class CmsNode {

  private final CmsTreeCache tree;
  private final String path;

  CmsNode(CmsTreeCache tree, String path) {
    this.tree = tree;
    this.path = path;
  }

  public String getPath() {
    return path;
  }

  /**
   * @return the child node at the given relative path or null when it does not exist
   */
  public CmsNode getChild(String relativePath) {
    String childPath = CmsTreeCache.childPath(path, relativePath);
    return tree.exists(childPath) ? new CmsNode(tree, childPath) : null;
  }

  /**
   * Creates the child node (and missing intermediate nodes) at the given relative path
   */
  public CmsNode addChild(String relativePath) {
    String childPath = CmsTreeCache.childPath(path, relativePath);
    tree.createNode(childPath);
    return new CmsNode(tree, childPath);
  }

  /**
   * Removes the child node at the given relative path with all its data and descendants
   */
  public boolean removeChild(String relativePath) {
    String childPath = CmsTreeCache.childPath(path, relativePath);
    if (childPath.equals(path)) {
      return false;
    }
    return tree.removeNode(childPath);
  }

  public Object put(String key, Object value) {
    tree.createNode(path);
    return tree.getCache().put(CmsTreeCache.dataKey(path, key), value);
  }

  public Object get(String key) {
    return tree.getCache().get(CmsTreeCache.dataKey(path, key));
  }

  public Object remove(String key) {
    return tree.getCache().remove(CmsTreeCache.dataKey(path, key));
  }

  public Set<String> getKeys() {
    String prefix = CmsTreeCache.dataPrefix(path);
    return tree.keys().filter(k -> k.startsWith(prefix)).map(k -> k.substring(prefix.length()))
        .collect(Collectors.toSet());
  }

  public Set<CmsNode> getChildren() {
    String prefix = CmsTreeCache.NODE_PREFIX + (path.isEmpty() ? "" : path + CmsTreeCache.PATH_SEPARATOR);
    return tree.keys().filter(k -> k.startsWith(prefix))
        .map(k -> k.substring(prefix.length()))
        .filter(name -> !name.isEmpty() && !name.contains(CmsTreeCache.PATH_SEPARATOR))
        .map(name -> new CmsNode(tree, CmsTreeCache.childPath(path, name)))
        .collect(Collectors.toSet());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CmsNode)) {
      return false;
    }
    return path.equals(((CmsNode) o).path);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path);
  }

}
