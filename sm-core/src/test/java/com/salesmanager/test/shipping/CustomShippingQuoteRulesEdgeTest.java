package com.salesmanager.test.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.CustomShippingQuoteRules;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.system.IntegrationModule;

public class CustomShippingQuoteRulesEdgeTest {

	private CustomShippingQuoteRules module;

	@Before
	public void setUp() {
		module = new CustomShippingQuoteRules();
		ReflectionTestUtils.setField(module, "droolsBeanFactory", new DroolsBeanFactory());
	}

	@Test
	public void appendsDefaultProvinceNoDistanceQuoteToExistingOptions() throws Exception {
		ShippingQuote quote = new ShippingQuote();
		ShippingOption existing = new ShippingOption();
		quote.setShippingOptions(new ArrayList<ShippingOption>(Collections.singletonList(existing)));
		Delivery delivery = new Delivery();
		delivery.setPostalCode("H3Z 2Y7");
		Country country = new Country();
		country.setIsoCode("CA");
		delivery.setCountry(country);

		DroolsBeanFactory factory = org.mockito.Mockito.mock(DroolsBeanFactory.class);
		Resource rule = ResourceFactory.newClassPathResource("com/salesmanager/drools/rules/PriceByDistance.drl");
		KieSession session = org.mockito.Mockito.spy(new DroolsBeanFactory().getKieSession(rule));
		org.mockito.Mockito.when(factory.getKieSession(org.mockito.ArgumentMatchers.any(Resource.class)))
				.thenReturn(session);
		ReflectionTestUtils.setField(module, "droolsBeanFactory", factory);

		try {
			List<ShippingOption> options = module.getShippingQuotes(
					quote,
					Collections.singletonList(packageDetails()),
					BigDecimal.ZERO,
					delivery,
					null,
					null,
					null,
					moduleConfiguration(),
					null,
					null);

			ArgumentCaptor<ShippingInputParameters> input =
					ArgumentCaptor.forClass(ShippingInputParameters.class);
			org.mockito.Mockito.verify(session).insert(input.capture());
			assertEquals("*", input.getValue().getProvince());
			assertEquals(0, input.getValue().getDistance());
			assertSame(quote.getShippingOptions(), options);
			assertEquals(2, options.size());
			assertSame(existing, options.get(0));
			assertEquals("customQuotesRules", options.get(1).getOptionCode());
			assertEquals(new BigDecimal("140"), options.get(1).getOptionPrice());
		} finally {
			session.dispose();
		}
	}

	private PackageDetails packageDetails() {
		PackageDetails details = new PackageDetails();
		details.setShippingWeight(10);
		details.setShippingLength(10);
		details.setShippingHeight(10);
		details.setShippingWidth(10);
		return details;
	}

	private IntegrationModule moduleConfiguration() {
		IntegrationModule module = new IntegrationModule();
		module.setCode(CustomShippingQuoteRules.MODULE_CODE);
		return module;
	}
}
