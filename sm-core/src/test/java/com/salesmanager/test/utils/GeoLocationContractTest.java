package com.salesmanager.test.utils;

import static org.junit.Assert.*;
import org.junit.Test;
import com.salesmanager.core.business.modules.utils.GeoLocationImpl;
import com.salesmanager.core.model.common.Address;

public class GeoLocationContractTest {
    @Test
    public void resolvesPublicAddressCountry() throws Exception {
        Address address = new GeoLocationImpl().getAddress("8.8.8.8");
        assertNotNull(address.getCountry());
        assertEquals("US", address.getCountry());
    }

    @Test
    public void privateAndLoopbackAddressesReturnEmptyAddress() throws Exception {
        GeoLocationImpl lookup = new GeoLocationImpl();
        for (String ip : new String[] {"192.168.1.1", "127.0.0.1"}) {
            Address address = lookup.getAddress(ip);
            assertNotNull(address);
            assertNull(address.getCountry());
            assertNull(address.getCity());
            assertNull(address.getZone());
            assertNull(address.getPostalCode());
        }
    }
}
