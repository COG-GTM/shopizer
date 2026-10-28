package com.salesmanager.core.business.modules.integration.shipping.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.commons.digester.Digester;
import org.junit.Test;
import org.xml.sax.SAXException;

public class CarrierXmlTest {

	public static class Holder {
		private String value;

		public void setValue(String value) {
			this.value = value;
		}
	}

	@Test
	public void parsesCarrierResponse() throws Exception {
		Holder holder = parse("<?xml version=\"1.0\"?><Response><Value>12.50</Value></Response>");
		assertEquals("12.50", holder.value);
	}

	@Test
	public void rejectsExternalEntities() throws Exception {
		File secret = File.createTempFile("carrier-xxe", ".txt");
		secret.deleteOnExit();
		Files.write(secret.toPath(), "top-secret".getBytes(StandardCharsets.UTF_8));
		String xml = "<?xml version=\"1.0\"?><!DOCTYPE Response [<!ENTITY xxe SYSTEM \""
				+ secret.toURI() + "\">]><Response><Value>&xxe;</Value></Response>";
		try {
			Holder holder = parse(xml);
			assertFalse(String.valueOf(holder.value).contains("top-secret"));
			fail("DOCTYPE should be rejected");
		} catch (SAXException expected) {
			// expected
		}
	}

	private static Holder parse(String xml) throws Exception {
		Holder holder = new Holder();
		Digester digester = CarrierXml.newDigester();
		digester.push(holder);
		digester.addCallMethod("Response/Value", "setValue", 0);
		digester.parse(new StringReader(xml));
		return holder;
	}
}
