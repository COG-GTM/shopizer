package com.salesmanager.test.cms;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.Test;
import com.salesmanager.core.business.modules.cms.impl.*;

class CmsTreeCacheEdgeTest {
    @Test
    void normalizesPathsAndPreservesMarkerAfterRemovingLastEntry() throws Exception {
        try (DefaultCacheManager manager = new DefaultCacheManager()) {
            manager.defineConfiguration("tree", new org.infinispan.configuration.cache.ConfigurationBuilder().build());
            CmsTreeCache tree = new CmsTreeCache(manager.getCache("tree"));
            CmsTreeNode node = tree.getOrCreateNode("///a//b///");
            assertEquals("a/b", node.getPath());
            assertEquals(node, tree.getOrCreateNode("a/b"));
            assertEquals(node.hashCode(), tree.getOrCreateNode("/a/b/").hashCode());
            assertTrue(node.getKeys().isEmpty());
            assertTrue(tree.getCache().containsKey("a/b\u0000"));
            assertNull(node.get("missing"));
            node.remove("missing");
            node.put("file", new byte[] {1});
            node.put("file", new byte[] {2});
            node.put("empty", new byte[0]);
            assertArrayEquals(new byte[] {2}, node.get("file"));
            assertEquals(Set.of("file", "empty"), node.getKeys());
            node.remove("file");
            node.remove("empty");
            assertTrue(node.getKeys().isEmpty());
            assertTrue(tree.getCache().containsKey("a/b\u0000"));
            assertEquals(Set.of(node), tree.getOrCreateNode("a").getChildren());
        }
    }

    @Test
    void removesOnlyTargetAndDescendantsIncludingMarkers() throws Exception {
        try (DefaultCacheManager manager = new DefaultCacheManager()) {
            manager.defineConfiguration("tree", new org.infinispan.configuration.cache.ConfigurationBuilder().build());
            CmsTreeCache tree = new CmsTreeCache(manager.getCache("tree"));
            tree.getOrCreateNode("a/b").put("f", new byte[] {1});
            tree.getOrCreateNode("a/b/child/deep").put("f", new byte[] {2});
            tree.getOrCreateNode("a/b/empty");
            CmsTreeNode sibling = tree.getOrCreateNode("a/bc");
            sibling.put("f", new byte[] {3});
            tree.removeNode("//a//b/");
            assertEquals(Set.of("a/bc\u0000", "a/bc\u0000f"), tree.getCache().keySet());
            assertArrayEquals(new byte[] {3}, sibling.get("f"));
            tree.removeNode("missing");
            assertEquals(Set.of(sibling), tree.getOrCreateNode("a").getChildren());
        }
    }
}
