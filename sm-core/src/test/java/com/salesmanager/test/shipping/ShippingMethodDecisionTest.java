package com.salesmanager.test.shipping;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingDecisionPreProcessorImpl;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.zone.Zone;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.system.IntegrationModule;

class ShippingMethodDecisionTest {

    private ShippingDecisionPreProcessorImpl processor;

    @BeforeEach
    void setUp() {
        processor = new ShippingDecisionPreProcessorImpl();
        ReflectionTestUtils.setField(processor, "droolsBeanFactory", new DroolsBeanFactory());
    }

    @Test
    void validateShippingMethod() throws Exception {
        // Original oversized Quebec fixture: the rule selects priceByDistance.
        assertShippingMethod(70, 78, "CA", "QC", "priceByDistance");
    }

    @Test
    void selectsCanadaPostForSmallCanadianPackage() throws Exception {
        assertShippingMethod(61, 65, "CA", "ON", "canadapost");
    }

    @Test
    void retainsCurrentModuleWhenNoRuleMatches() throws Exception {
        assertShippingMethod(70, 78, "US", "NY", "ups");
    }

    private void assertShippingMethod(double weight, double width, String countryCode,
            String zoneCode, String expectedCode) throws Exception {
        PackageDetails pack = new PackageDetails();
        pack.setShippingHeight(20);
        pack.setShippingLength(10);
        pack.setShippingWeight(weight);
        pack.setShippingWidth(width);

        Country country = new Country();
        country.setIsoCode(countryCode);
        Zone zone = new Zone();
        zone.setCode(zoneCode);
        Delivery delivery = new Delivery();
        delivery.setCountry(country);
        delivery.setZone(zone);

        IntegrationModule canadaPost = module("canadapost");
        IntegrationModule ups = module("ups");
        IntegrationModule priceByDistance = module("priceByDistance");
        ShippingQuote quote = new ShippingQuote();
        quote.setCurrentShippingModule(ups);
        quote.setShippingModuleCode(ups.getCode());

        processor.prePostProcessShippingQuotes(quote, Collections.singletonList(pack), null,
                delivery, null, null, null, ups, null,
                Arrays.asList(canadaPost, ups, priceByDistance), Locale.CANADA);

        IntegrationModule expected = "canadapost".equals(expectedCode) ? canadaPost
                : "priceByDistance".equals(expectedCode) ? priceByDistance : ups;
        assertSame(expected, quote.getCurrentShippingModule());
    }

    private IntegrationModule module(String code) {
        IntegrationModule module = new IntegrationModule();
        module.setCode(code);
        return module;
    }
}
