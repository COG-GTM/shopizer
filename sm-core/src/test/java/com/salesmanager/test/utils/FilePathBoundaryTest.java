package com.salesmanager.test.utils;

import static org.junit.Assert.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.utils.FilePathUtils;

public class FilePathBoundaryTest {
    @Test
    public void acceptsMaximumLengthAndUnicode() throws Exception {
        String maximum = new String(new char[255]).replace('\0', 'a');
        assertEquals(maximum, FilePathUtils.requireSafeFileName(maximum));
        assertFalse(FilePathUtils.isSafeFileName(maximum + "a"));
        assertTrue(FilePathUtils.isSafeFileName("商品-é.png"));
        assertTrue(FilePathUtils.isSafeFileName("a..b.png"));
        assertTrue(FilePathUtils.isSafeFileName("image. "));
    }

    @Test
    public void rejectsDelimitersAndDotSpaceOnlyNames() {
        for (String name : new String[] { ":", "image.png:stream", " . . ", "\t", "image\u007f.png" }) {
            assertFalse(name, FilePathUtils.isSafeFileName(name));
        }
    }

    @Test
    public void resolvesVariantAndEmptyRelativePaths() throws Exception {
        Path base = Paths.get("cms").toAbsolutePath();
        assertEquals(base.resolve("DEFAULT/12"), FilePathUtils.resolveRelative(base, "/DEFAULT/12"));
        assertEquals(base, FilePathUtils.resolveRelative(base, null));
        assertEquals(base, FilePathUtils.resolveRelative(base, ""));
        assertEquals(base.resolve("ok.png"), FilePathUtils.resolveFile(base, "ok.png"));
    }

    @Test
    public void resolveFileRejectsAbsoluteParentAndEmptyTargets() throws Exception {
        for (String name : new String[] { "/etc/x.png", "..", "../x.png", "", "a/../x.png" }) {
            try {
                FilePathUtils.resolveFile(Paths.get("cms"), name);
                fail("Accepted " + name);
            } catch (ServiceException expected) {
                assertTrue(expected.getMessageCode().contains("Invalid file name"));
            }
        }
    }
}
