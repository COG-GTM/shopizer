package com.salesmanager.test.shop.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.junit.Test;

public class JavaReleaseBytecodeTest {

	@Test
	public void shopApplicationUsesJava17Bytecode() throws IOException {
		assertEquals(61, majorVersion("com/salesmanager/shop/application/ShopApplication.class"));
	}

	@Test
	public void shopModelUsesJava17Bytecode() throws IOException {
		assertEquals(61, majorVersion("com/salesmanager/shop/model/catalog/product/ReadableProduct.class"));
	}

	@Test
	public void coreUsesJava17Bytecode() throws IOException {
		assertEquals(61, majorVersion(
				"com/salesmanager/core/business/services/catalog/product/ProductServiceImpl.class"));
	}

	@Test
	public void coreModelUsesJava17Bytecode() throws IOException {
		assertEquals(61, majorVersion("com/salesmanager/core/model/catalog/product/Product.class"));
	}

	private static int majorVersion(String classResource) throws IOException {
		try (InputStream input = JavaReleaseBytecodeTest.class.getClassLoader().getResourceAsStream(classResource)) {
			assertNotNull("Missing class resource " + classResource, input);
			byte[] classBytes = input.readAllBytes();
			assertEquals(0xCAFEBABE, ByteBuffer.wrap(classBytes).getInt());
			return ((classBytes[6] & 0xff) << 8) | (classBytes[7] & 0xff);
		}
	}
}
