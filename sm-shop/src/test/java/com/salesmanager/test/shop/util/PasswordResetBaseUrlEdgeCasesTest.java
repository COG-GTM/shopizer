package com.salesmanager.test.shop.util;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.utils.FilePathUtils;

public class PasswordResetBaseUrlEdgeCasesTest {

	private FilePathUtils filePathUtils;
	private MerchantStore store;

	@Before
	public void setUp() {
		filePathUtils = new FilePathUtils();
		store = new MerchantStore();
		store.setDomainName("https://store.example.com/");
	}

	@Test
	public void validUrlAcceptsOnlyHttpSchemesAndDefaultsToHttps() {
		assertEquals("https://a.example", filePathUtils.validUrl("https://a.example"));
		assertEquals("http://a.example", filePathUtils.validUrl("http://a.example"));
		assertEquals("https://a.example", filePathUtils.validUrl("a.example"));
	}

	@Test
	public void emptyAllowlistUsesStoreDomain() {
		assertEquals("https://store.example.com/",
				filePathUtils.buildPasswordResetBaseUrl("https://shop.example.com", store));
	}

	@Test
	public void allowlistNormalizesValidUrlsAndRejectsUnsafeOrMismatchedUrls() {
		filePathUtils.properties.setProperty(FilePathUtils.PASSWORD_RESET_ALLOWED_URLS,
				"https://shop.example.com, https://shop.example.com:8443/app/");
		String fallback = "https://store.example.com/";

		for (String url : new String[] { "javascript://shop.example.com", "ftp://shop.example.com",
				"https://shop.example.com/?x=1", "https://shop.example.com/#f", "https://shop.example.com:9999",
				"//shop.example.com", "shop.example.com", "http://shop.example.com" }) {
			assertEquals(url, fallback, filePathUtils.buildPasswordResetBaseUrl(url, store));
		}
		assertEquals("https://shop.example.com/",
				filePathUtils.buildPasswordResetBaseUrl("HTTPS://Shop.Example.COM/", store));
		assertEquals("https://shop.example.com:8443/app/",
				filePathUtils.buildPasswordResetBaseUrl("https://shop.example.com:8443/app", store));
	}

	@Test
	public void nullStoreDomainUsesLocalhostAndCompleteUrlDoesNotDuplicateSchemeOrSlash() {
		store.setDomainName(null);
		assertEquals("https://localhost:8080/", filePathUtils.buildPasswordResetBaseUrl(null, store));
		store.setDomainName("https://store.example.com/");
		assertEquals("https://store.example.com/", filePathUtils.buildPasswordResetBaseUrl(null, store));
	}
}
