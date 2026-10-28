package com.salesmanager.test.shop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.utils.FilePathUtils;

/**
 * Additional edge cases for FilePathUtils.resolveUserPasswordResetBaseUrl:
 * the returned base must always be a configured value, never client input.
 */
public class FilePathUtilsPasswordResetUrlEdgeCasesTest {

	private FilePathUtils filePathUtils;
	private MerchantStore store;

	@Before
	public void setUp() {
		filePathUtils = new FilePathUtils();
		filePathUtils.properties.setProperty(FilePathUtils.USER_PASSWORD_RESET_ALLOWED_URLS, "http://localhost:4200");
		store = new MerchantStore();
	}

	private Optional<String> resolve(String url) {
		return filePathUtils.resolveUserPasswordResetBaseUrl(url, store);
	}

	@Test
	public void schemeAndHostAreCaseInsensitiveAndInputIsTrimmed() {
		assertEquals(Optional.of("http://localhost:4200"), resolve("HTTP://LocalHost:4200"));
		assertEquals(Optional.of("http://localhost:4200"), resolve("  http://localhost:4200/\n"));
	}

	@Test
	public void queryFragmentAndPathOfRequestAreNeverCopied() {
		assertEquals(Optional.of("http://localhost:4200"),
				resolve("http://localhost:4200/#/reset?next=https://mallory.example"));
		assertEquals(Optional.of("http://localhost:4200"), resolve("http://localhost:4200/a/b/../../c?x=%2F%2Fevil"));
		assertEquals(Optional.of("http://localhost:4200"), resolve("http://localhost:4200#@mallory.example"));
	}

	@Test
	public void defaultPortsAreEquivalentToImplicitPorts() {
		filePathUtils.properties.setProperty(FilePathUtils.USER_PASSWORD_RESET_ALLOWED_URLS,
				"https://admin.acme.example, http://intranet.acme.example:80/admin");
		assertEquals(Optional.of("https://admin.acme.example"), resolve("https://admin.acme.example:443/"));
		assertEquals(Optional.of("http://intranet.acme.example:80/admin"), resolve("http://intranet.acme.example/admin"));
		assertFalse(resolve("http://admin.acme.example:443").isPresent());
	}

	@Test
	public void invalidConfiguredEntriesAreIgnored() {
		filePathUtils.properties.setProperty(FilePathUtils.USER_PASSWORD_RESET_ALLOWED_URLS,
				"ftp://files.acme.example, not a url, ,javascript://x, http://localhost:4200/ ");
		assertEquals(Optional.of("http://localhost:4200"), resolve("http://localhost:4200"));
		assertFalse(resolve("ftp://files.acme.example").isPresent());
	}

	@Test
	public void obfuscatedHostsAreRejected() {
		String[] rejected = { "http://localhost:4200%40mallory.example", "http://localhost%3A4200@mallory.example",
				"http://mallory.example#localhost:4200", "http://mallory.example?localhost:4200",
				"http://localhost:4200.mallory.example", "http://127.0.0.1:4200", "http://[::1]:4200",
				"http:///localhost:4200", "http:localhost:4200", "https://localhost:4200" };
		for (String url : rejected) {
			assertFalse(url, resolve(url).isPresent());
		}
	}

	@Test
	public void storeDomainWithPortKeepsThePort() {
		store.setDomainName("shop.acme.example:8443");
		assertEquals(Optional.of("https://shop.acme.example:8443"), resolve("https://shop.acme.example:8443/admin"));
		assertFalse(resolve("https://shop.acme.example").isPresent());
		assertFalse(resolve("http://shop.acme.example:8443").isPresent());
	}

	@Test
	public void storeDomainWithExplicitHttpSchemeIsHonoured() {
		store.setDomainName("http://shop.acme.example");
		assertEquals(Optional.of("http://shop.acme.example"), resolve("http://shop.acme.example/"));
		assertFalse(resolve("https://shop.acme.example").isPresent());
	}

	@Test
	public void configuredEntryWinsOverStoreDomainOnSameOrigin() {
		filePathUtils.properties.setProperty(FilePathUtils.USER_PASSWORD_RESET_ALLOWED_URLS,
				"https://shop.acme.example/admin");
		store.setDomainName("shop.acme.example");
		assertEquals(Optional.of("https://shop.acme.example/admin"), resolve("https://shop.acme.example/admin/login"));
		assertEquals(Optional.of("https://shop.acme.example"), resolve("https://shop.acme.example/other"));
	}
}
