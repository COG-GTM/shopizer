package com.salesmanager.test.shipping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingDecisionPreProcessorImpl;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.zone.Zone;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.system.IntegrationModule;



public class ShippingMethodDecisionTest extends com.salesmanager.test.common.AbstractSalesManagerCoreTestCase {
	
	@Inject
	ShippingDecisionPreProcessorImpl shippingMethodDecisionProcess;

	@Test
	public void keepsCurrentModuleWhenRuleSelectsUnavailableModule() throws Exception {
		assertSelectedModule(70, 20, 10, 78, "canadapost", "canadapost", false);
	}

	@Test
	public void selectsRuleModuleWhenItIsAvailable() throws Exception {
		assertSelectedModule(70, 20, 10, 78, "canadapost", "priceByDistance", true);
	}

	@Test
	public void selectsCanadaPostForLightPackage() throws Exception {
		assertSelectedModule(10, 10, 10, 10, "ups", "canadapost", false);
	}

	private void assertSelectedModule(
			double weight,
			double height,
			double length,
			double width,
			String initialModuleCode,
			String expectedModuleCode,
			boolean includeDistanceModule) throws Exception {
		ShippingQuote quote = new ShippingQuote();
		PackageDetails pDetail = new PackageDetails();
		pDetail.setShippingHeight(height);
		pDetail.setShippingLength(length);
		pDetail.setShippingWeight(weight);
		pDetail.setShippingWidth(width);
		List<PackageDetails> details = new ArrayList<PackageDetails>();
		details.add(pDetail);

		Delivery delivery = new Delivery();
		delivery.setAddress("358 Du Languedoc");
		delivery.setCity("Boucherville");
		delivery.setPostalCode("J4B 8J9");
		
		Country country = new Country();
		country.setIsoCode("CA");
		country.setName("Canada");
		
		//country.setIsoCode("US");
		//country.setName("United States");
		
		delivery.setCountry(country);
		
		Zone zone = new Zone();
		zone.setCode("QC");
		zone.setName("Quebec");
		
		//zone.setCode("NY");
		//zone.setName("New York");
		
		delivery.setZone(zone);
		
		IntegrationModule currentModule = new IntegrationModule();
		currentModule.setCode(initialModuleCode);
		quote.setCurrentShippingModule(currentModule);
		quote.setShippingModuleCode(currentModule.getCode());
		
		IntegrationModule canadapost = new IntegrationModule();
		canadapost.setCode("canadapost");
		
		IntegrationModule ups = new IntegrationModule();
		ups.setCode("ups");
		
		IntegrationModule inhouse = new IntegrationModule();
		inhouse.setCode("customQuotesRules");
		
		List<IntegrationModule> allModules = new ArrayList<IntegrationModule>();
		allModules.add(canadapost);
		allModules.add(ups);
		allModules.add(inhouse);
		if (includeDistanceModule) {
			IntegrationModule distanceModule = new IntegrationModule();
			distanceModule.setCode("priceByDistance");
			allModules.add(distanceModule);
		}

		shippingMethodDecisionProcess.prePostProcessShippingQuotes(quote, details, null, delivery, null, null, null, currentModule, null, allModules, Locale.CANADA);

		assertEquals(expectedModuleCode, quote.getCurrentShippingModule().getCode());
	}
}
