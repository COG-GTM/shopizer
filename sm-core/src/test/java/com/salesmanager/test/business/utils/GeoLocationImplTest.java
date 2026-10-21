package com.salesmanager.test.business.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.salesmanager.core.business.modules.utils.GeoLocationImpl;
import com.salesmanager.core.model.common.Address;

/**
 * GeoLocationImpl reads the bundled GeoLite2 City database through the MaxMind GeoIP2 API
 * (bumped 2.7.0 -> 4.4.0 by AB-538).
 */
public class GeoLocationImplTest {

	@Test
	public void resolvesPublicIpToCountryFromBundledDatabase() throws Exception {
		Address address = new GeoLocationImpl().getAddress("8.8.8.8");
		assertNotNull(address);
		assertEquals("US", address.getCountry());
	}

	@Test
	public void unknownAddressYieldsEmptyAddressInsteadOfError() throws Exception {
		Address address = new GeoLocationImpl().getAddress("127.0.0.1");
		assertNotNull(address);
		assertNull(address.getCountry());
		assertNull(address.getCity());
	}
}
