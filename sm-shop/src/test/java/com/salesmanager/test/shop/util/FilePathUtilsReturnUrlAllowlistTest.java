package com.salesmanager.test.shop.util;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.utils.FilePathUtils;

public class FilePathUtilsReturnUrlAllowlistTest {

	private final FilePathUtils filePathUtils = new FilePathUtils();

	@Test
	public void blankAllowlistFallsBackToStoreDomainHost() {
		MerchantStore store = new MerchantStore();
		store.setDomainName("shop.example.com");
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://shop.example.com/admin", store, ""));
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://shop.example.com/admin", store, null));

		MerchantStore blankDomainStore = new MerchantStore();
		blankDomainStore.setDomainName("");
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("https://shop.example.com/admin", blankDomainStore, null));
	}

	@Test
	public void matchesAllowlistOriginsWithoutPathsAndIgnoresInvalidEntries() {
		String allowlist = "http://localhost:4200/, https://admin.shop.com/app";
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("http://localhost:4200/admin", null, allowlist));
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://admin.shop.com/x", null, allowlist));
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://admin.shop.com/x", null,
				"not a url,javascript:x,,https://admin.shop.com/app,"));
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://admin.shop.com/x", null,
				"HTTPS://Admin.Shop.COM/x"));
	}

	@Test
	public void storeDomainMatchingIgnoresSchemeAndPort() {
		MerchantStore store = new MerchantStore();
		store.setDomainName("shop.example.com:8080");

		// Store-domain matching compares only the host, not its scheme or port.
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://shop.example.com/admin", store, null));
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("http://shop.example.com:9999", store, null));
	}

	@Test
	public void rejectsLookalikeOrUnsupportedReturnUrls() throws Exception {
		MerchantStore store = new MerchantStore();
		store.setDomainName("shop.example.com");
		List<String> returnUrls = Arrays.asList(
				"https://evil.shop.example.com",
				"http://localhost:4200.evil.com",
				"//admin.shop.com",
				" https://admin.shop.com",
				"https://evil.com\\@admin.shop.com",
				"https://user:pass@admin.shop.com",
				"http://",
				"mailto:a@admin.shop.com");

		for (String returnUrl : returnUrls) {
			Assert.assertFalse(returnUrl, filePathUtils.isAllowedReturnUrl(returnUrl, store,
					"http://localhost:4200,https://admin.shop.com"));
		}

		String encodedUserInfo = "https://admin.shop.com%40evil.com";
		Assert.assertNull(new URI(encodedUserInfo).getHost());
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl(encodedUserInfo, store, "https://admin.shop.com"));
	}

}
