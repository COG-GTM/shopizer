package com.salesmanager.test.business.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.commons.digester.Digester;
import org.junit.Test;

import com.salesmanager.core.business.utils.SecureDigesterFactory;

public class SecureDigesterFactoryTest {

	public static class Holder {
		private String value;

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	@Test
	public void parsesPlainXml() throws Exception {
		Holder holder = parse("<Root><Value>ok</Value></Root>");
		assertEquals("ok", holder.getValue());
	}

	@Test
	public void rejectsExternalEntities() throws Exception {
		File secret = File.createTempFile("digester-secret", ".txt");
		secret.deleteOnExit();
		Files.write(secret.toPath(), "top-secret".getBytes(StandardCharsets.UTF_8));

		String xml = "<?xml version=\"1.0\"?><!DOCTYPE Root [<!ENTITY xxe SYSTEM \"" + secret.toURI() + "\">]>"
				+ "<Root><Value>&xxe;</Value></Root>";

		Holder holder = null;
		try {
			holder = parse(xml);
		} catch (Exception expected) {
			// DOCTYPE is disallowed
		}
		assertNull(holder == null ? null : holder.getValue());
	}

	private static Holder parse(String xml) throws Exception {
		Holder holder = new Holder();
		Digester digester = SecureDigesterFactory.newDigester();
		digester.push(holder);
		digester.addCallMethod("Root/Value", "setValue", 0);
		digester.parse(new StringReader(xml));
		return holder;
	}

}
