package com.salesmanager.test.shop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.utils.FilePathUtils;

public class FilePathUtilsPasswordResetUrlTest {

	private FilePathUtils filePathUtils;
	private MerchantStore store;

	@Before
	public void setUp() {
		filePathUtils = new FilePathUtils();
		filePathUtils.properties.setProperty(FilePathUtils.USER_PASSWORD_RESET_ALLOWED_URLS,
				" https://admin.acme.example/console/ , http://localhost:4200");
		store = new MerchantStore();
		store.setDomainName("shop.acme.example");
	}

	@Test
	public void blankReturnUrlUsesFirstConfiguredUrl() {
		assertEquals(Optional.of("https://admin.acme.example/console"),
				filePathUtils.resolveUserPasswordResetBaseUrl(null, store));
		assertEquals(Optional.of("https://admin.acme.example/console"),
				filePathUtils.resolveUserPasswordResetBaseUrl("  ", store));
	}

	@Test
	public void blankReturnUrlFallsBackToStoreDomain() {
		filePathUtils.properties.remove(FilePathUtils.USER_PASSWORD_RESET_ALLOWED_URLS);
		assertEquals(Optional.of("https://shop.acme.example"),
				filePathUtils.resolveUserPasswordResetBaseUrl(null, store));
	}

	@Test
	public void allowedOriginReturnsConfiguredValue() {
		assertEquals(Optional.of("https://admin.acme.example/console"),
				filePathUtils.resolveUserPasswordResetBaseUrl("https://ADMIN.acme.example:443/anything?x=1", store));
		assertEquals(Optional.of("http://localhost:4200"),
				filePathUtils.resolveUserPasswordResetBaseUrl("http://localhost:4200/", store));
		assertEquals(Optional.of("https://shop.acme.example"),
				filePathUtils.resolveUserPasswordResetBaseUrl("https://shop.acme.example/admin", store));
		assertEquals(Optional.of("http://shop.acme.example"),
				filePathUtils.resolveUserPasswordResetBaseUrl("http://shop.acme.example", store));
	}

	@Test
	public void storeDomainWithScheme() {
		store.setDomainName("https://shop.acme.example/");
		filePathUtils.properties.remove(FilePathUtils.USER_PASSWORD_RESET_ALLOWED_URLS);
		assertEquals(Optional.of("https://shop.acme.example"),
				filePathUtils.resolveUserPasswordResetBaseUrl("https://shop.acme.example", store));
		assertFalse(filePathUtils.resolveUserPasswordResetBaseUrl("http://shop.acme.example", store).isPresent());
	}

	@Test
	public void rejectsUrlsOutsideAllowlist() {
		String[] rejected = { "https://mallory.example", "https://admin.acme.example.mallory.example",
				"http://admin.acme.example/console", "https://admin.acme.example:8443",
				"http://localhost:4200@mallory.example", "https://admin.acme.example\\@mallory.example",
				"//mallory.example", "javascript://admin.acme.example/%0aalert(1)", "ftp://localhost:4200",
				"localhost:4200", "not a url" };
		for (String url : rejected) {
			assertFalse(url, filePathUtils.resolveUserPasswordResetBaseUrl(url, store).isPresent());
		}
	}

	@Test
	public void noConfigurationAndNoStoreDomainRejectsEverything() {
		filePathUtils.properties.remove(FilePathUtils.USER_PASSWORD_RESET_ALLOWED_URLS);
		store.setDomainName(null);
		assertFalse(filePathUtils.resolveUserPasswordResetBaseUrl(null, store).isPresent());
		assertFalse(filePathUtils.resolveUserPasswordResetBaseUrl("https://mallory.example", store).isPresent());
	}
}
