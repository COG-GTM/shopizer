package com.salesmanager.core.modules.integration.shipping.canadapost.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.w3c.dom.Document;

public class CanadaPostRatingXmlHardeningTest {

	private final CanadaPostRatingXml ratingXml = new CanadaPostRatingXml();

	@Test
	public void doesNotExposeExternalEntityContent() throws Exception {
		Path secretFile = Files.createTempFile("canadapost-xxe", ".txt");
		try {
			String marker = "SECRET-XXE-MARKER";
			Files.write(secretFile, marker.getBytes(StandardCharsets.UTF_8));
			String xml = "<!DOCTYPE price-quotes [<!ENTITY xxe SYSTEM \"" + secretFile.toUri()
					+ "\">]><price-quotes xmlns=\"" + CanadaPostRatingXml.RATING_NAMESPACE
					+ "\"><price-quote><service-code>&xxe;</service-code></price-quote></price-quotes>";

			try {
				CanadaPostRatingXml.Result result = ratingXml.parse(
						new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
				String parsedFields = result.getPriceQuotes().get(0).getServiceCode() + " "
						+ result.getPriceQuotes().get(0).getServiceName();
				assertFalse(parsedFields.contains(marker));
			} catch (XMLStreamException expected) {
				return;
			}
		} finally {
			Files.deleteIfExists(secretFile);
		}
	}

	@Test
	public void escapesMarkupWhenWritingCustomerNumber() throws Exception {
		MailingScenario scenario = new MailingScenario();
		scenario.setCustomerNumber("a<b&c");
		scenario.setServiceCodes(Collections.singletonList("DOM.RP"));
		String xml = ratingXml.write(scenario);
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		Document document = factory.newDocumentBuilder().parse(
				new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

		assertEquals("a<b&c", document.getElementsByTagNameNS(
				CanadaPostRatingXml.RATING_NAMESPACE, "customer-number").item(0).getTextContent());
	}

	@Test
	public void rejectsPriceQuotesWithWrongNamespace() {
		String xml = "<price-quotes xmlns=\"urn:other\"/>";

		assertThrows(XMLStreamException.class, () -> ratingXml.parse(
				new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	public void rejectsEmptyInput() {
		assertThrows(XMLStreamException.class,
				() -> ratingXml.parse(new ByteArrayInputStream(new byte[0])));
	}
}
