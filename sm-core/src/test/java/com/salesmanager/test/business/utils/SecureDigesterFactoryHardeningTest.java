package com.salesmanager.test.business.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;

import org.apache.commons.digester.Digester;
import org.junit.Test;

import com.salesmanager.core.business.utils.SecureDigesterFactory;

public class SecureDigesterFactoryHardeningTest {

	public static class ResponseValues {
		private String value;

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	@Test
	public void rejectsInternalEntityExpansion() throws Exception {
		String xml = "<!DOCTYPE Root [<!ENTITY a \"1234567890\"><!ENTITY b \"&a;&a;&a;&a;\">]>"
				+ "<Root><Value>&b;</Value></Root>";
		assertThrows(Exception.class, () -> parse(xml, "Root/Value"));
	}

	@Test
	public void rejectsExternalEntity() throws Exception {
		String xml = "<!DOCTYPE Root [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
				+ "<Root><Value>&xxe;</Value></Root>";
		assertThrows(Exception.class, () -> parse(xml, "Root/Value"));
	}

	@Test
	public void disallowsDoctypeDeclarations() throws Exception {
		Digester digester = SecureDigesterFactory.newDigester();
		assertTrue(digester.getXMLReader().getFeature("http://apache.org/xml/features/disallow-doctype-decl"));
	}

	@Test
	public void parsesCarrierResponseXmlWithoutDoctype() throws Exception {
		ResponseValues ups = parse("<RatingServiceSelectionResponse><Response><ResponseStatusCode>1</ResponseStatusCode>"
				+ "</Response></RatingServiceSelectionResponse>",
				"RatingServiceSelectionResponse/Response/ResponseStatusCode");
		assertEquals("1", ups.getValue());

		ResponseValues usps = parse("<RateV3Response><Package ID=\"1\"><Postage><Rate>8.95</Rate></Postage>"
				+ "</Package></RateV3Response>", "RateV3Response/Package/Postage/Rate");
		assertEquals("8.95", usps.getValue());
	}

	private static ResponseValues parse(String xml, String path) throws Exception {
		ResponseValues values = new ResponseValues();
		Digester digester = SecureDigesterFactory.newDigester();
		digester.push(values);
		digester.addCallMethod(path, "setValue", 0);
		digester.parse(new StringReader(xml));
		return values;
	}
}
