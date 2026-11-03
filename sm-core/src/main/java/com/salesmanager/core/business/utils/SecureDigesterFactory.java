package com.salesmanager.core.business.utils;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * Creates commons-digester instances that refuse DOCTYPE declarations and
 * external entities, so remote XML responses cannot trigger XXE.
 */
public final class SecureDigesterFactory {

	private SecureDigesterFactory() {
	}

	public static Digester newDigester()
			throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
		Digester digester = new Digester();
		digester.setValidating(false);
		digester.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		digester.setFeature("http://xml.org/sax/features/external-general-entities", false);
		digester.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		digester.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		return digester;
	}

}
