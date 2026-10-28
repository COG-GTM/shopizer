package com.salesmanager.shop.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;

public class FilePathUtilsTrustedReturnUrlTest {

	private FilePathUtils filePathUtils;
	private MerchantStore store;

	@Before
	public void setUp() {
		filePathUtils = new FilePathUtils();
		filePathUtils.properties.setProperty(FilePathUtils.PASSWORD_RESET_ALLOWED_ORIGINS,
				"https://admin.acme.example, http://localhost:4200");
		store = new MerchantStore();
		store.setCode("DEFAULT");
		store.setDomainName("shop.acme.example");
	}

	@Test
	public void blankReturnUrlUsesStoreDomain() {
		assertEquals("https://shop.acme.example/", filePathUtils.buildTrustedReturnBaseUrl(null, store));
		assertEquals("https://shop.acme.example/", filePathUtils.buildTrustedReturnBaseUrl(" ", store));
	}

	@Test
	public void relativePathIsResolvedOnStoreDomain() {
		assertEquals("https://shop.acme.example/", filePathUtils.buildTrustedReturnBaseUrl("/", store));
		assertEquals("https://shop.acme.example/shop/", filePathUtils.buildTrustedReturnBaseUrl("shop", store));
	}

	@Test
	public void storeDomainWithSchemeIsNotPrefixedTwice() {
		store.setDomainName("https://shop.acme.example/");
		assertEquals("https://shop.acme.example/app/",
				filePathUtils.buildTrustedReturnBaseUrl("https://SHOP.acme.example:443/app", store));
	}

	@Test
	public void configuredOriginsAreAllowed() {
		assertEquals("https://admin.acme.example/",
				filePathUtils.buildTrustedReturnBaseUrl("https://admin.acme.example", store));
		assertEquals("http://localhost:4200/",
				filePathUtils.buildTrustedReturnBaseUrl("http://localhost:4200/", store));
	}

	@Test(expected = ServiceRuntimeException.class)
	public void htmlBreakoutIsRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("\"><img src=\"https://mallory.example/leak?t=", store);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void htmlBreakoutAfterAllowedOriginIsRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("https://shop.acme.example/\"><img src=x>", store);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void foreignOriginIsRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("https://mallory.example/", store);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void protocolRelativeForeignOriginIsRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("//mallory.example/", store);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void lookalikeHostIsRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("https://shop.acme.example.mallory.example/", store);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void userInfoIsRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("https://shop.acme.example@mallory.example/", store);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void wrongPortIsRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("https://shop.acme.example:8443/", store);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void nonHttpSchemeIsRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("javascript:alert(1)", store);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void queryIsRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("https://shop.acme.example/?next=https://mallory.example/", store);
	}

	@Test
	public void hashRouteIsKept() {
		assertEquals("http://localhost:4200/#/",
				filePathUtils.buildTrustedReturnBaseUrl("http://localhost:4200/#", store));
		assertEquals("https://shop.acme.example/app/#/auth/",
				filePathUtils.buildTrustedReturnBaseUrl("https://shop.acme.example/app#/auth", store));
	}

	@Test(expected = ServiceRuntimeException.class)
	public void htmlBreakoutInFragmentIsRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("https://shop.acme.example/#%22%3E%3Cimg", store);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void encodedCharactersInPathAreRejected() {
		filePathUtils.buildTrustedReturnBaseUrl("https://shop.acme.example/%22%3E", store);
	}
}
