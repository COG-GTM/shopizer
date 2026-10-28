package com.salesmanager.test.content;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.content.local.CmsStaticContentFileManagerImpl;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.InputContentFile;

public class CmsStaticContentFileManagerImplTest {

	private Path sandbox;
	private Path root;
	private Path victim;
	private CmsStaticContentFileManagerImpl manager;

	@Before
	public void setUp() throws IOException {
		sandbox = Files.createTempDirectory("cms-static");
		root = Files.createDirectories(sandbox.resolve("static"));
		manager = new CmsStaticContentFileManagerImpl();
		manager.setRootName(root.toString());

		victim = root.resolve("files/ACME/IMAGE/logo.png");
		Files.createDirectories(victim.getParent());
		Files.write(victim, "acme".getBytes(StandardCharsets.UTF_8));
	}

	@After
	public void tearDown() throws IOException {
		FileUtils.deleteDirectory(sandbox.toFile());
	}

	@Test
	public void addFileWritesUnderStoreFolder() throws Exception {
		manager.addFile("GLOBEX", Optional.empty(), file("banner.png", FileContentType.IMAGE, "globex"));

		Path written = root.resolve("files/GLOBEX/IMAGE/banner.png");
		assertArrayEquals("globex".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(written));
		assertEquals(Collections.singletonList("banner.png"),
				manager.getFileNames("GLOBEX", Optional.empty(), FileContentType.IMAGE));
	}

	@Test
	public void addFileRejectsTraversal() throws Exception {
		String[] names = { "../../ACME/IMAGE/logo.png", "../../../evil.txt", "..\\..\\evil.txt", "..",
				sandbox.resolve("evil.txt").toString() };
		for (String name : names) {
			try {
				manager.addFile("GLOBEX", Optional.empty(), file(name, FileContentType.IMAGE, "pwned"));
				fail("expected rejection of " + name);
			} catch (ServiceException expected) {
				// expected
			}
		}
		assertVictimIntact();
		assertTrue(Files.notExists(sandbox.resolve("evil.txt")));
		assertTrue(Files.notExists(root.resolve("evil.txt")));
	}

	@Test
	public void addFilesRejectsBatchBeforeWritingAnything() throws Exception {
		try {
			manager.addFiles("GLOBEX", Optional.empty(), Arrays.asList(
					file("ok.css", FileContentType.STATIC_FILE, "ok"),
					file("../../ACME/IMAGE/logo.png", FileContentType.IMAGE, "pwned")));
			fail("expected rejection");
		} catch (ServiceException expected) {
			// expected
		}
		assertVictimIntact();
		assertTrue(Files.notExists(root.resolve("files/GLOBEX/STATIC_FILE/ok.css")));
	}

	@Test
	public void addFilesWritesEachFileInItsTypeFolder() throws Exception {
		manager.addFiles("GLOBEX", Optional.empty(), Arrays.asList(
				file("a.css", FileContentType.STATIC_FILE, "a"),
				file("b.png", FileContentType.IMAGE, "b")));

		assertTrue(Files.exists(root.resolve("files/GLOBEX/STATIC_FILE/a.css")));
		assertTrue(Files.exists(root.resolve("files/GLOBEX/IMAGE/b.png")));
	}

	@Test
	public void removeFileRejectsTraversal() throws Exception {
		try {
			manager.removeFile("GLOBEX", FileContentType.IMAGE, "../../ACME/IMAGE/logo.png", Optional.empty());
			fail("expected rejection");
		} catch (ServiceException expected) {
			// expected
		}
		assertVictimIntact();
	}

	@Test
	public void removeFileDeletesOwnFile() throws Exception {
		manager.addFile("GLOBEX", Optional.empty(), file("banner.png", FileContentType.IMAGE, "globex"));
		manager.removeFile("GLOBEX", FileContentType.IMAGE, "banner.png", Optional.empty());
		assertTrue(Files.notExists(root.resolve("files/GLOBEX/IMAGE/banner.png")));
	}

	@Test
	public void variantStorePathIsKeptUnderRoot() throws Exception {
		manager.addFile("/GLOBEX/12", Optional.empty(), file("v.png", FileContentType.VARIANT, "v"));
		assertTrue(Files.exists(root.resolve("files/GLOBEX/12/VARIANT/v.png")));

		manager.removeFile("/GLOBEX/12", FileContentType.VARIANT, "v.png", Optional.empty());
		assertTrue(Files.notExists(root.resolve("files/GLOBEX/12/VARIANT/v.png")));
	}

	@Test
	public void storeCodeCannotEscapeRoot() throws Exception {
		for (String code : new String[] { "..", "../files/ACME", "", "/" }) {
			try {
				manager.removeFile(code, FileContentType.IMAGE, "logo.png", Optional.empty());
				fail("expected rejection of store code " + code);
			} catch (ServiceException expected) {
				// expected
			}
		}
		assertVictimIntact();
	}

	@Test
	public void foldersStayUnderStoreFolder() throws Exception {
		manager.addFolder("GLOBEX", "banners", Optional.of("/marketing"));
		assertTrue(Files.isDirectory(root.resolve("files/GLOBEX/marketing/banners")));

		for (String folder : new String[] { "..", "../../ACME", "a/b" }) {
			try {
				manager.addFolder("GLOBEX", folder, Optional.empty());
				fail("expected rejection of folder " + folder);
			} catch (ServiceException expected) {
				// expected
			}
		}
		try {
			manager.removeFolder("GLOBEX", "IMAGE", Optional.of("/../ACME"));
			fail("expected rejection");
		} catch (ServiceException expected) {
			// expected
		}
		try (Stream<Path> s = Files.list(sandbox)) {
			assertEquals(1L, s.count());
		}
		assertVictimIntact();
	}

	private void assertVictimIntact() throws IOException {
		assertArrayEquals("acme".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(victim));
	}

	private static InputContentFile file(String name, FileContentType type, String content) {
		InputContentFile f = new InputContentFile();
		f.setFileName(name);
		f.setFileContentType(type);
		f.setFile(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
		return f;
	}
}
