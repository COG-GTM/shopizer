package com.salesmanager.core.business.modules.integration.shipping.impl;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Parses carrier responses without DOCTYPE declarations or external entities.
 */
final class CarrierXml {

	private CarrierXml() {
	}

	static Digester newDigester() throws ParserConfigurationException, SAXException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(false);
		factory.setValidating(false);
		factory.setXIncludeAware(false);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		XMLReader reader = factory.newSAXParser().getXMLReader();
		return new Digester(reader);
	}
}
