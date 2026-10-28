package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.salesmanager.shop.store.security.AbstractCustomerServices;

public class CustomerStoreCodeResolutionTest {

	@Before
	public void setUp() {
		RequestContextHolder.resetRequestAttributes();
	}

	@After
	public void tearDown() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	public void storeCodeUsesRequestParameterOrDefault() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("store", "GLOBEX");
		assertEquals("GLOBEX", AbstractCustomerServices.storeCode(request));

		request = new MockHttpServletRequest();
		assertEquals("DEFAULT", AbstractCustomerServices.storeCode(request));

		request.setParameter("store", "  ");
		assertEquals("DEFAULT", AbstractCustomerServices.storeCode(request));
	}

	@Test
	public void currentStoreDefaultsWithoutRequestAttributes() {
		assertEquals("DEFAULT", AbstractCustomerServices.currentStoreCode());
	}

	@Test
	public void currentStoreUsesServletRequestParameter() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("store", "GLOBEX");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		assertEquals("GLOBEX", AbstractCustomerServices.currentStoreCode());
	}
}
