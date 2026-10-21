package com.salesmanager.test.shipping;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingDecisionPreProcessorImpl;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.constants.Constants;

public class ShippingDecisionPreProcessorUnitTest {

	@Test
	public void usesDeliveryStateWhenZoneIsMissing() throws Exception {
		ShippingQuote quote = new ShippingQuote();
		Delivery delivery = delivery();
		delivery.setState("QC");
		ArgumentCaptor<ShippingInputParameters> input = process(
				quote, delivery, packages(packageDetails(10, 10, 10, 10)));

		assertEquals("QC", input.getValue().getProvince());
	}

	@Test
	public void aggregatesWeightAndUsesLargestVolumeAndDimension() throws Exception {
		PackageDetails first = packageDetails(30, 10, 10, 10);
		PackageDetails second = packageDetails(35, 20, 20, 20);
		ArgumentCaptor<ShippingInputParameters> input = process(
				new ShippingQuote(), delivery(), packages(first, second));

		assertEquals(65, input.getValue().getWeight());
		assertEquals(8000, input.getValue().getVolume());
		assertEquals(20, input.getValue().getSize());
	}

	@Test
	public void copiesDistanceFromQuoteInformation() throws Exception {
		ShippingQuote quote = new ShippingQuote();
		quote.getQuoteInformations().put(Constants.DISTANCE_KEY, 1000.0);
		ArgumentCaptor<ShippingInputParameters> input = process(
				quote, delivery(), packages(packageDetails(10, 10, 10, 10)));

		assertEquals(1000, input.getValue().getDistance());
	}

	private ArgumentCaptor<ShippingInputParameters> process(
			ShippingQuote quote,
			Delivery delivery,
			java.util.List<PackageDetails> packages) throws Exception {
		DroolsBeanFactory factory = mock(DroolsBeanFactory.class);
		KieSession session = mock(KieSession.class);
		when(factory.getKieSession(any(Resource.class))).thenReturn(session);

		ShippingDecisionPreProcessorImpl processor = new ShippingDecisionPreProcessorImpl();
		ReflectionTestUtils.setField(processor, "droolsBeanFactory", factory);
		IntegrationModule currentModule = new IntegrationModule();
		currentModule.setCode("canadapost");
		processor.prePostProcessShippingQuotes(
				quote,
				packages,
				BigDecimal.ZERO,
				delivery,
				null,
				null,
				null,
				currentModule,
				null,
				Collections.singletonList(currentModule),
				null);

		ArgumentCaptor<ShippingInputParameters> input = ArgumentCaptor.forClass(ShippingInputParameters.class);
		verify(session).insert(input.capture());
		verify(session).fireAllRules();
		return input;
	}

	private Delivery delivery() {
		Delivery delivery = new Delivery();
		Country country = new Country();
		country.setIsoCode("CA");
		delivery.setCountry(country);
		return delivery;
	}

	private PackageDetails packageDetails(double weight, double length, double height, double width) {
		PackageDetails details = new PackageDetails();
		details.setShippingWeight(weight);
		details.setShippingLength(length);
		details.setShippingHeight(height);
		details.setShippingWidth(width);
		return details;
	}

	private java.util.List<PackageDetails> packages(PackageDetails... packages) {
		return Arrays.asList(packages);
	}
}
