package com.salesmanager.test.cms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.impl.*;
import com.salesmanager.core.business.modules.cms.content.infinispan.CmsStaticContentFileManagerImpl;
import com.salesmanager.core.model.content.*;

class CmsStaticContentFileManagerUnitTest {
    private DefaultCacheManager embedded;
    private CacheManager cacheManager;
    private CmsStaticContentFileManagerImpl files;

    @BeforeEach
    void setUp() {
        embedded = new DefaultCacheManager();
        embedded.defineConfiguration("tree", new org.infinispan.configuration.cache.ConfigurationBuilder().build());
        CmsTreeCache tree = new CmsTreeCache(embedded.getCache("tree"));
        cacheManager = mock(CacheManager.class);
        when(cacheManager.getTreeCache()).thenReturn(tree);
        when(cacheManager.getRootName()).thenReturn("static-merchant");
        files = new CmsStaticContentFileManagerImpl();
        files.setCacheManager(cacheManager);
        ReflectionTestUtils.invokeMethod(files, "init");
    }

    @AfterEach
    void close() { embedded.stop(); }

    private InputContentFile input(String name) {
        InputContentFile input = new InputContentFile();
        input.setFileName(name);
        input.setFileContentType(FileContentType.STATIC_FILE);
        input.setFile(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        return input;
    }

    @Test
    void addsListsAndRemovesFilesIndependently() throws Exception {
        files.addFile("A", Optional.empty(), input("one.css"));
        files.addFiles("A", Optional.empty(), List.of(input("two.js"), input("three.css")));
        OutputContentFile result = files.getFile("A", Optional.empty(), FileContentType.STATIC_FILE, "one.css");
        assertEquals("one.css", result.getFileName());
        assertEquals(FileContentType.STATIC_FILE, result.getFileContentType());
        assertArrayEquals(new byte[] {1, 2, 3}, result.getFile().toByteArray());
        assertEquals(Set.of("one.css", "two.js", "three.css"),
                new HashSet<>(files.getFileNames("A", Optional.empty(), FileContentType.STATIC_FILE)));
        assertEquals(Set.of("one.css", "two.js", "three.css"),
                files.getFiles("A", Optional.empty(), FileContentType.STATIC_FILE).stream()
                        .map(OutputContentFile::getFileName).collect(Collectors.toSet()));
        files.removeFile("A", FileContentType.STATIC_FILE, "one.css", Optional.empty());
        assertNull(files.getFile("A", Optional.empty(), FileContentType.STATIC_FILE, "one.css"));
        assertEquals(Set.of("two.js", "three.css"),
                new HashSet<>(files.getFileNames("A", Optional.empty(), FileContentType.STATIC_FILE)));
        files.addFile("AB", Optional.empty(), input("other.css"));
        files.removeFiles("A", Optional.empty());
        assertTrue(files.getFiles("A", Optional.empty(), FileContentType.STATIC_FILE).isEmpty());
        assertNotNull(files.getFile("AB", Optional.empty(), FileContentType.STATIC_FILE, "other.css"));
    }

    @Test
    void emptyAndUnavailableCacheBehaviour() throws Exception {
        assertNull(files.getFile("A", Optional.empty(), FileContentType.STATIC_FILE, "absent"));
        assertTrue(files.getFileNames("A", Optional.empty(), FileContentType.STATIC_FILE).isEmpty());
        files.addFiles("A", Optional.empty(), List.of());
        files.removeFile("A", FileContentType.STATIC_FILE, "absent", Optional.empty());
        when(cacheManager.getTreeCache()).thenReturn(null);
        assertThrows(ServiceException.class, () -> files.addFile("A", Optional.empty(), input("x")));
        assertThrows(ServiceException.class, () -> files.getFiles("A", Optional.empty(), FileContentType.STATIC_FILE));
        assertThrows(ServiceException.class, () -> files.removeFiles("A", Optional.empty()));
    }
}
