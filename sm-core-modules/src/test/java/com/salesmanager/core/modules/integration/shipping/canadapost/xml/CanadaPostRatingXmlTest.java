package com.salesmanager.core.modules.integration.shipping.canadapost.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class CanadaPostRatingXmlTest {

	private final CanadaPostRatingXml ratingXml = new CanadaPostRatingXml();

	@Test
	public void parsesRealWorldPriceQuotesAndIgnoresUnknownFields() throws Exception {
		CanadaPostRatingXml.Result result = ratingXml.parse(getClass().getResourceAsStream(
				"/canadapost/domestic-price-quotes.xml"));

		assertFalse(result.isMessages());
		assertEquals(3, result.getPriceQuotes().size());
		assertEquals("DOM.RP", result.getPriceQuotes().get(0).getServiceCode());
		assertEquals("Regular Parcel", result.getPriceQuotes().get(0).getServiceName());
		assertEquals(new BigDecimal("12.00"), result.getPriceQuotes().get(0).getBase());
		assertEquals(new BigDecimal("13.56"), result.getPriceQuotes().get(0).getDue());
		assertEquals("Xpresspost", result.getPriceQuotes().get(2).getServiceName());
	}

	@Test
	public void parsesMessagesResponse() throws Exception {
		CanadaPostRatingXml.Result result = ratingXml.parse(getClass().getResourceAsStream(
				"/canadapost/messages-error.xml"));

		assertTrue(result.isMessages());
		assertEquals("12345", result.getMessages().get(0).getCode());
		assertEquals("Invalid account credentials", result.getMessages().get(0).getDescription());
	}

	@Test
	public void writesJaxbCompatibleElementOrderAndOmitsNullValues() throws Exception {
		MailingScenario scenario = new MailingScenario();
		scenario.setCustomerNumber("1234567");
		MailingScenario.ParcelCharacteristics parcel = new MailingScenario.ParcelCharacteristics();
		parcel.setWeight(new BigDecimal("2.0"));
		MailingScenario.Dimensions dimensions = new MailingScenario.Dimensions();
		dimensions.setLength(new BigDecimal("26.0"));
		dimensions.setWidth(new BigDecimal("11.0"));
		dimensions.setHeight(new BigDecimal("6.0"));
		parcel.setDimensions(dimensions);
		scenario.setParcelCharacteristics(parcel);
		scenario.setServiceCodes(java.util.Arrays.asList("DOM.RP", "DOM.EP"));
		scenario.setOriginPostalCode("M5V3A8");
		MailingScenario.Destination destination = new MailingScenario.Destination();
		destination.setDomesticPostalCode("K1A0B1");
		scenario.setDestination(destination);

		String xml = ratingXml.write(scenario);

		assertTrue(xml.contains("xmlns=\"" + CanadaPostRatingXml.RATING_NAMESPACE + "\""));
		assertTrue(xml.indexOf("customer-number") < xml.indexOf("parcel-characteristics"));
		assertTrue(xml.indexOf("parcel-characteristics") < xml.indexOf("services"));
		assertTrue(xml.indexOf("services") < xml.indexOf("origin-postal-code"));
		assertTrue(xml.indexOf("origin-postal-code") < xml.indexOf("destination"));
		assertFalse(xml.contains("contract-id"));
		assertFalse(xml.contains("international"));
	}

	@Test
	public void rejectsUnexpectedRootElement() throws Exception {
		String xml = "<unexpected xmlns=\"" + CanadaPostRatingXml.RATING_NAMESPACE + "\"/>";
		try {
			ratingXml.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		} catch (javax.xml.stream.XMLStreamException expected) {
			assertTrue(expected.getMessage().contains("Unexpected Canada Post XML root"));
			return;
		}
		throw new AssertionError("Expected an unexpected-root exception");
	}
}
