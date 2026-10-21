package com.salesmanager.test.shop.util;

import org.junit.Assert;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.utils.FilePathUtils;

public class FilePathUtilsReturnUrlTest {

	private final FilePathUtils filePathUtils = new FilePathUtils();
	private final String allowedUrls = "http://localhost:4200, https://admin.shop.com";

	@Test
	public void allowsConfiguredOriginsAndStoreDomain() {
		MerchantStore store = new MerchantStore();
		store.setDomainName("shop.example.com");

		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("http://localhost:4200", store, allowedUrls));
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("http://localhost:4200/admin", store, allowedUrls));
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://ADMIN.shop.com/x", store, allowedUrls));
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://shop.example.com/admin", store, allowedUrls));
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("http://shop.example.com", store, allowedUrls));
	}

	@Test
	public void rejectsUnallowedAndInvalidUrls() {
		MerchantStore store = new MerchantStore();
		store.setDomainName("shop.example.com");

		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("https://evil.example", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("http://localhost:4201", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("https://localhost:4200", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("https://admin.shop.com.evil.com", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("https://admin.shop.com@evil.com", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("javascript:alert(1)", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("ftp://admin.shop.com", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("not a url", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl(null, store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("https://shop.example.com", null, allowedUrls));
	}

	@Test
	public void allowsSchemePrefixedStoreDomains() {
		MerchantStore store = new MerchantStore();
		store.setDomainName("https://shop.example.com");

		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://shop.example.com/admin", store, allowedUrls));

		store.setDomainName("http://shop.example.com/");
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://shop.example.com", store, allowedUrls));

		store.setDomainName("https://shop.example.com");
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("https://evil.example", store, allowedUrls));
	}

	@Test
	public void enforcesStoreDomainPorts() {
		MerchantStore store = new MerchantStore();
		store.setDomainName("localhost:8080");

		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("http://localhost:8080/admin", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("http://localhost:9999", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("http://localhost", store, allowedUrls));

		store.setDomainName("shop.example.com");
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://shop.example.com:443/admin", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("https://shop.example.com:8443", store, allowedUrls));

		store.setDomainName("shop.example.com:443");
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("https://shop.example.com/admin", store, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("http://shop.example.com", store, allowedUrls));

		store.setDomainName("http://shop.example.com:80");
		Assert.assertTrue(filePathUtils.isAllowedReturnUrl("http://shop.example.com", store, allowedUrls));
	}

	@Test
	public void rejectsReturnUrlsWithQueryOrFragment() {
		Assert.assertFalse(
				filePathUtils.isAllowedReturnUrl("http://localhost:4200/admin?lang=en", null, allowedUrls));
		Assert.assertFalse(filePathUtils.isAllowedReturnUrl("http://localhost:4200/#/admin", null, allowedUrls));
	}
}
