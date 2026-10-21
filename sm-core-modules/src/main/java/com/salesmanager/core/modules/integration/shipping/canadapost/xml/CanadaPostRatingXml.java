package com.salesmanager.core.modules.integration.shipping.canadapost.xml;

import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public class CanadaPostRatingXml {

	public static final String RATING_NAMESPACE = "http://www.canadapost.ca/ws/ship/rate-v4";
	public static final String MESSAGES_NAMESPACE = "http://www.canadapost.ca/ws/messages";

	public String write(MailingScenario scenario) {
		try {
			StringWriter output = new StringWriter();
			XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
			writer.writeStartDocument("UTF-8", "1.0");
			start(writer, "mailing-scenario");
			writer.writeDefaultNamespace(RATING_NAMESPACE);

			writeElement(writer, "customer-number", scenario.getCustomerNumber());
			if (scenario.getParcelCharacteristics() != null) {
				start(writer, "parcel-characteristics");
				writeElement(writer, "weight", decimal(scenario.getParcelCharacteristics().getWeight()));
				if (scenario.getParcelCharacteristics().getDimensions() != null) {
					start(writer, "dimensions");
					writeElement(writer, "length", decimal(scenario.getParcelCharacteristics().getDimensions().getLength()));
					writeElement(writer, "width", decimal(scenario.getParcelCharacteristics().getDimensions().getWidth()));
					writeElement(writer, "height", decimal(scenario.getParcelCharacteristics().getDimensions().getHeight()));
					writer.writeEndElement();
				}
				writer.writeEndElement();
			}
			if (scenario.getServiceCodes() != null && !scenario.getServiceCodes().isEmpty()) {
				start(writer, "services");
				for (String serviceCode : scenario.getServiceCodes()) {
					writeElement(writer, "service-code", serviceCode);
				}
				writer.writeEndElement();
			}
			writeElement(writer, "origin-postal-code", scenario.getOriginPostalCode());
			if (scenario.getDestination() != null) {
				start(writer, "destination");
				MailingScenario.Destination destination = scenario.getDestination();
				if (destination.getInternationalCountryCode() != null) {
					start(writer, "international");
					writeElement(writer, "country-code", destination.getInternationalCountryCode());
					writer.writeEndElement();
				}
				if (destination.getUnitedStatesZipCode() != null) {
					start(writer, "united-states");
					writeElement(writer, "zip-code", destination.getUnitedStatesZipCode());
					writer.writeEndElement();
				}
				if (destination.getDomesticPostalCode() != null) {
					start(writer, "domestic");
					writeElement(writer, "postal-code", destination.getDomesticPostalCode());
					writer.writeEndElement();
				}
				writer.writeEndElement();
			}
			writer.writeEndElement();
			writer.writeEndDocument();
			writer.close();
			return output.toString();
		} catch (XMLStreamException e) {
			throw new IllegalStateException("Unable to write Canada Post rating XML", e);
		}
	}

	public Result parse(InputStream input) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newFactory();
		factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
		XMLStreamReader reader = factory.createXMLStreamReader(input);
		try {
			while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT) {
			}
			if (reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
				throw new XMLStreamException("Canada Post XML has no root element");
			}
			if ("price-quotes".equals(reader.getLocalName())
					&& RATING_NAMESPACE.equals(reader.getNamespaceURI())) {
				return parseQuotes(reader);
			}
			if ("messages".equals(reader.getLocalName())
					&& MESSAGES_NAMESPACE.equals(reader.getNamespaceURI())) {
				return parseMessages(reader);
			}
			throw new XMLStreamException("Unexpected Canada Post XML root: " + reader.getLocalName());
		} finally {
			reader.close();
		}
	}

	private Result parseQuotes(XMLStreamReader reader) throws XMLStreamException {
		List<PriceQuote> quotes = new ArrayList<PriceQuote>();
		while (reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.END_ELEMENT && "price-quotes".equals(reader.getLocalName())) {
				break;
			}
			if (event == XMLStreamConstants.START_ELEMENT) {
				if (isRatingElement(reader, "price-quote")) {
					quotes.add(parseQuote(reader));
				} else {
					skipElement(reader);
				}
			}
		}
		return Result.forQuotes(quotes);
	}

	private PriceQuote parseQuote(XMLStreamReader reader) throws XMLStreamException {
		PriceQuote quote = new PriceQuote();
		while (reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.END_ELEMENT && "price-quote".equals(reader.getLocalName())) {
				break;
			}
			if (event != XMLStreamConstants.START_ELEMENT) {
				continue;
			}
			if (isRatingElement(reader, "service-code")) {
				quote.setServiceCode(reader.getElementText());
			} else if (isRatingElement(reader, "service-name")) {
				quote.setServiceName(reader.getElementText());
			} else if (isRatingElement(reader, "price-details")) {
				parsePriceDetails(reader, quote);
			} else {
				skipElement(reader);
			}
		}
		return quote;
	}

	private void parsePriceDetails(XMLStreamReader reader, PriceQuote quote) throws XMLStreamException {
		while (reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.END_ELEMENT && "price-details".equals(reader.getLocalName())) {
				return;
			}
			if (event != XMLStreamConstants.START_ELEMENT) {
				continue;
			}
			if (isRatingElement(reader, "base")) {
				quote.setBase(new BigDecimal(reader.getElementText().trim()));
			} else if (isRatingElement(reader, "due")) {
				quote.setDue(new BigDecimal(reader.getElementText().trim()));
			} else {
				skipElement(reader);
			}
		}
	}

	private Result parseMessages(XMLStreamReader reader) throws XMLStreamException {
		List<CanadaPostMessage> messages = new ArrayList<CanadaPostMessage>();
		while (reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.END_ELEMENT && "messages".equals(reader.getLocalName())) {
				break;
			}
			if (event == XMLStreamConstants.START_ELEMENT) {
				if (isMessagesElement(reader, "message")) {
					messages.add(parseMessage(reader));
				} else {
					skipElement(reader);
				}
			}
		}
		return Result.forMessages(messages);
	}

	private CanadaPostMessage parseMessage(XMLStreamReader reader) throws XMLStreamException {
		CanadaPostMessage message = new CanadaPostMessage();
		while (reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.END_ELEMENT && "message".equals(reader.getLocalName())) {
				break;
			}
			if (event != XMLStreamConstants.START_ELEMENT) {
				continue;
			}
			if (isMessagesElement(reader, "code")) {
				message.setCode(reader.getElementText());
			} else if (isMessagesElement(reader, "description")) {
				message.setDescription(reader.getElementText());
			} else {
				skipElement(reader);
			}
		}
		return message;
	}

	private void skipElement(XMLStreamReader reader) throws XMLStreamException {
		int depth = 1;
		while (depth > 0 && reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.START_ELEMENT) {
				depth++;
			} else if (event == XMLStreamConstants.END_ELEMENT) {
				depth--;
			}
		}
	}

	private boolean isRatingElement(XMLStreamReader reader, String localName) {
		return localName.equals(reader.getLocalName()) && RATING_NAMESPACE.equals(reader.getNamespaceURI());
	}

	private boolean isMessagesElement(XMLStreamReader reader, String localName) {
		return localName.equals(reader.getLocalName()) && MESSAGES_NAMESPACE.equals(reader.getNamespaceURI());
	}

	private void start(XMLStreamWriter writer, String name) throws XMLStreamException {
		writer.writeStartElement("", name, RATING_NAMESPACE);
	}

	private void writeElement(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
		if (value != null) {
			start(writer, name);
			writer.writeCharacters(value);
			writer.writeEndElement();
		}
	}

	private String decimal(BigDecimal value) {
		return value == null ? null : value.toPlainString();
	}

	public static class Result {
		private final List<PriceQuote> priceQuotes;
		private final List<CanadaPostMessage> messages;

		private Result(List<PriceQuote> priceQuotes, List<CanadaPostMessage> messages) {
			this.priceQuotes = priceQuotes;
			this.messages = messages;
		}

		private static Result forQuotes(List<PriceQuote> quotes) {
			return new Result(quotes, null);
		}

		private static Result forMessages(List<CanadaPostMessage> messages) {
			return new Result(null, messages);
		}

		public boolean isMessages() {
			return messages != null;
		}

		public List<PriceQuote> getPriceQuotes() {
			return priceQuotes;
		}

		public List<CanadaPostMessage> getMessages() {
			return messages;
		}
	}
}
