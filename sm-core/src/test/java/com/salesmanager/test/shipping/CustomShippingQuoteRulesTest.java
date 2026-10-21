package com.salesmanager.test.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.CustomShippingQuoteRules;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.zone.Zone;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.constants.Constants;

public class CustomShippingQuoteRulesTest {

	private CustomShippingQuoteRules module;

	@Before
	public void setUp() {
		module = new CustomShippingQuoteRules();
		ReflectionTestUtils.setField(module, "droolsBeanFactory", new DroolsBeanFactory());
	}

	@Test
	public void returnsPriceByDistanceQuote() throws Exception {
		ShippingQuote quote = new ShippingQuote();
		quote.getQuoteInformations().put(Constants.DISTANCE_KEY, 1000.0);

		List<ShippingOption> options = module.getShippingQuotes(
				quote,
				Collections.singletonList(packageDetails()),
				null,
				delivery("J4B 8J9"),
				null,
				null,
				null,
				new IntegrationModule(),
				null,
				null);

		assertNotNull(options);
		assertEquals(1, options.size());
		assertEquals(new BigDecimal("140"), options.get(0).getOptionPrice());
		assertEquals("customQuotesRules", options.get(0).getOptionCode());
	}

	@Test
	public void returnsNullWhenDeliveryHasNoPostalCode() throws Exception {
		List<ShippingOption> options = module.getShippingQuotes(
				new ShippingQuote(),
				Collections.singletonList(packageDetails()),
				null,
				delivery(" "),
				null,
				null,
				null,
				new IntegrationModule(),
				null,
				null);

		assertNull(options);
	}

	private PackageDetails packageDetails() {
		PackageDetails details = new PackageDetails();
		details.setShippingWeight(10);
		details.setShippingLength(10);
		details.setShippingHeight(10);
		details.setShippingWidth(10);
		return details;
	}

	private Delivery delivery(String postalCode) {
		Delivery delivery = new Delivery();
		delivery.setPostalCode(postalCode);

		Country country = new Country();
		country.setIsoCode("CA");
		delivery.setCountry(country);

		Zone zone = new Zone();
		zone.setCode("QC");
		delivery.setZone(zone);

		return delivery;
	}
}
