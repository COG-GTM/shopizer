package com.salesmanager.shop.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;

public class FilePathUtilsReturnUrlEdgeCasesTest {

	private FilePathUtils filePathUtils;
	private MerchantStore store;

	@Before
	public void setUp() {
		filePathUtils = new FilePathUtils();
		filePathUtils.properties.setProperty(FilePathUtils.PASSWORD_RESET_ALLOWED_ORIGINS,
				"https://admin.acme.example/app , ,not a url%%, HTTP://LocalHost:4200");
		store = new MerchantStore();
		store.setCode("DEFAULT");
		store.setDomainName("shop.acme.example");
	}

	@Test
	public void validUrlNormalizesOnlyBareDomains() {
		assertEquals("https://shop.example", filePathUtils.validUrl("https://shop.example"));
		assertEquals("http://shop.example", filePathUtils.validUrl("http://shop.example"));
		assertEquals("https://shop.example", filePathUtils.validUrl("shop.example"));
		assertEquals("", filePathUtils.validUrl(""));
	}

	@Test
	public void malformedConfiguredOriginsAreIgnoredAndPathsAreDiscarded() {
		assertAccepted("https://admin.acme.example/other", "https://admin.acme.example/other/");
		assertAccepted("http://localhost:4200", "http://localhost:4200/");
		assertAccepted("/shop", "https://shop.acme.example/shop/");
	}

	@Test
	public void defaultPortsAndUppercaseOriginsAreNormalized() {
		assertAccepted("https://admin.acme.example:443/x", "https://admin.acme.example/x/");
		assertAccepted("HTTPS://ADMIN.acme.example", "https://admin.acme.example/");
	}

	@Test
	public void emptyAllowedOriginsStillTrustsStoreOrigin() {
		filePathUtils.properties.setProperty(FilePathUtils.PASSWORD_RESET_ALLOWED_ORIGINS, "");
		assertAccepted("https://shop.acme.example", "https://shop.acme.example/");
		assertRejected("http://localhost:4200/");
	}

	@Test
	public void rejectsUnsafeUrls() {
		for (String value : new String[] {
				"https://shop.acme.example\\@mallory.example/",
				"https://shop.acme.example/\r\nBcc:x",
				"https://shop.acme.example/a b",
				"http://localhost:4200/#/x?y=1",
				"ftp://shop.acme.example/",
				"https://shop.acme.example:80/"
		}) {
			assertRejected(value);
		}
	}

	private void assertAccepted(String value, String expected) {
		String result = filePathUtils.buildTrustedReturnBaseUrl(value, store);
		assertEquals(expected, result);
		assertTrue(result.endsWith("/"));
	}

	private void assertRejected(String value) {
		try {
			filePathUtils.buildTrustedReturnBaseUrl(value, store);
			fail("expected ServiceRuntimeException for " + value);
		} catch (ServiceRuntimeException e) {
			assertEquals("400", e.getErrorCode());
		}
	}
}
