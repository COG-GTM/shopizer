package com.salesmanager.test.shop.platform;

import static org.junit.Assert.*;
import java.io.DataInputStream;
import java.io.InputStream;
import org.junit.Test;

public class BytecodeLevelTest {
    @Test
    public void productionAndTestClassesUseJava17() throws Exception {
        for (Class<?> type : new Class<?>[] {com.salesmanager.shop.model.store.ReadableMerchantStore.class, com.salesmanager.shop.application.ShopApplication.class, BytecodeLevelTest.class}) {
            try (InputStream stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(type.getName(), stream);
                DataInputStream bytes = new DataInputStream(stream);
                assertEquals(0xCAFEBABE, bytes.readInt());
                bytes.readUnsignedShort();
                assertEquals(type.getName(), 61, bytes.readUnsignedShort());
            }
        }
    }
}
