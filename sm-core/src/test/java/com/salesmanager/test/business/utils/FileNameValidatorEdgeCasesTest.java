package com.salesmanager.test.business.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.salesmanager.core.business.utils.FileNameValidator;

public class FileNameValidatorEdgeCasesTest {

    @Test
    public void acceptsSafeNamesWithPunctuationAndUnicode() {
        assertTrue(FileNameValidator.isSafeFileName("my.image.v2.png"));
        assertTrue(FileNameValidator.isSafeFileName(".hidden.png"));
        assertTrue(FileNameValidator.isSafeFileName("photo 1.jpg"));
        assertTrue(FileNameValidator.isSafeFileName("café-ñ.png"));
        assertTrue(FileNameValidator.isSafeFileName("%2e%2e%2fevil.png"));
    }

    @Test
    public void rejectsRepeatedDotsColonsAndTabs() {
        assertFalse(FileNameValidator.isSafeFileName("foo..png"));
        assertFalse(FileNameValidator.isSafeFileName("evil.png:stream"));
        assertFalse(FileNameValidator.isSafeFileName("image\t.jpg"));
    }
}
