package com.salesmanager.test.platform;

import static org.junit.jupiter.api.Assertions.*;
import java.io.DataInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

public class BytecodeLevelTest {
    @Test
    public void productionAndTestClassesUseJava17() throws Exception {
        for (Class<?> type : new Class<?>[] {com.salesmanager.core.model.merchant.MerchantStore.class, com.salesmanager.core.business.services.merchant.MerchantStoreServiceImpl.class, BytecodeLevelTest.class}) {
            try (InputStream stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(stream, type.getName());
                DataInputStream bytes = new DataInputStream(stream);
                assertEquals(0xCAFEBABE, bytes.readInt());
                bytes.readUnsignedShort();
                assertEquals(61, bytes.readUnsignedShort(), type.getName());
            }
        }
    }
}
