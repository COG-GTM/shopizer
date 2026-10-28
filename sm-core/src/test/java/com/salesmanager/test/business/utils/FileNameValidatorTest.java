package com.salesmanager.test.business.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.salesmanager.core.business.utils.FileNameValidator;

public class FileNameValidatorTest {

    @Test
    public void acceptsPlainFileNames() {
        assertTrue(FileNameValidator.isSafeFileName("image.jpg"));
        assertTrue(FileNameValidator.isSafeFileName("my image-01_v2.PNG"));
    }

    @Test
    public void rejectsTraversalAndSeparators() {
        assertFalse(FileNameValidator.isSafeFileName(null));
        assertFalse(FileNameValidator.isSafeFileName(""));
        assertFalse(FileNameValidator.isSafeFileName("   "));
        assertFalse(FileNameValidator.isSafeFileName("."));
        assertFalse(FileNameValidator.isSafeFileName(".."));
        assertFalse(FileNameValidator.isSafeFileName("../evil.png"));
        assertFalse(FileNameValidator.isSafeFileName("../../../ACME/SKU1/LARGE/main.jpg"));
        assertFalse(FileNameValidator.isSafeFileName("..\\..\\evil.png"));
        assertFalse(FileNameValidator.isSafeFileName("/etc/passwd"));
        assertFalse(FileNameValidator.isSafeFileName("dir/image.jpg"));
        assertFalse(FileNameValidator.isSafeFileName("C:\\Users\\a\\image.jpg"));
        assertFalse(FileNameValidator.isSafeFileName("C:image.jpg"));
        assertFalse(FileNameValidator.isSafeFileName("image.jpg\u0000.png"));
        assertFalse(FileNameValidator.isSafeFileName("image\n.jpg"));
    }
}
