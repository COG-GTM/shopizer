package com.salesmanager.test.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.utils.FilePathUtils;

public class FilePathUtilsTest {

	@Test
	public void acceptsPlainFileNames() {
		assertTrue(FilePathUtils.isSafeFileName("logo.png"));
		assertTrue(FilePathUtils.isSafeFileName("my file (1).pdf"));
		assertTrue(FilePathUtils.isSafeFileName(".htaccess-like"));
		assertTrue(FilePathUtils.isSafeFileName("a..b.txt"));
	}

	@Test
	public void rejectsTraversalAndSeparators() {
		String[] invalid = { null, "", " ", ".", "..", "...", ". .", "../evil.txt", "../../ACME/IMAGE/logo.png",
				"a/b.png", "a\\b.png", "..\\..\\evil.txt", "/etc/passwd", "C:evil.txt", "evil.txt\0.png",
				"line\nbreak.png", new String(new char[256]).replace('\0', 'a') };
		for (String name : invalid) {
			assertFalse(String.valueOf(name), FilePathUtils.isSafeFileName(name));
		}
	}

	@Test(expected = ServiceException.class)
	public void requireSafeFileNameThrows() throws Exception {
		FilePathUtils.requireSafeFileName("../../evil.txt");
	}

	@Test
	public void resolveRelativeIgnoresLeadingAndRepeatedSlashes() throws Exception {
		Path base = Paths.get("/srv/static/files");
		assertEquals(Paths.get("/srv/static/files/DEFAULT/12"), FilePathUtils.resolveRelative(base, "/DEFAULT//12"));
	}

	@Test(expected = ServiceException.class)
	public void resolveRelativeRejectsParentSegments() throws Exception {
		FilePathUtils.resolveRelative(Paths.get("/srv/static/files"), "DEFAULT/../../etc");
	}

	@Test(expected = ServiceException.class)
	public void requireWithinRejectsEscapes() throws Exception {
		Path base = Paths.get("/srv/static/files/DEFAULT");
		FilePathUtils.requireWithin(base, base.resolve("../ACME/IMAGE/logo.png"));
	}

	@Test(expected = ServiceException.class)
	public void requireWithinRejectsSiblingPrefix() throws Exception {
		FilePathUtils.requireWithin(Paths.get("/srv/static/files/DEFAULT"), Paths.get("/srv/static/files/DEFAULT2/x"));
	}
}
