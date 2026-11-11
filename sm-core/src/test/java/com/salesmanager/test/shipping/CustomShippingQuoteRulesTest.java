package com.salesmanager.test.shipping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/** AB-542: CustomShippingQuoteRules prices quotes from PriceByDistance.drl via the real Drools runtime. */
class CustomShippingQuoteRulesTest {

    private CustomShippingQuoteRules module;

    @BeforeEach
    void setUp() {
        module = new CustomShippingQuoteRules();
        ReflectionTestUtils.setField(module, "droolsBeanFactory", new DroolsBeanFactory());
    }

    @ParameterizedTest
    @CsvSource({ "0.0, 140", "100.4, 140", "530.9, 140", "531.0, 140", "3550.9, 140" })
    void addsCustomQuoteOptionForDistance(double distance, String expectedPrice) throws Exception {
        ShippingQuote quote = quoteWithDistance(distance);
        List<ShippingOption> options = module.getShippingQuotes(quote, packages(), BigDecimal.TEN,
                delivery("J4B 8J9"), null, null, null, module("customQuotesRules"), null, Locale.CANADA);

        assertEquals(1, options.size());
        ShippingOption option = options.get(0);
        assertEquals(new BigDecimal(expectedPrice), option.getOptionPrice());
        assertEquals(CustomShippingQuoteRules.MODULE_CODE, option.getShippingModuleCode());
        assertEquals(CustomShippingQuoteRules.MODULE_CODE, option.getOptionCode());
        assertEquals(CustomShippingQuoteRules.MODULE_CODE, option.getOptionId());
        assertEquals(options, quote.getShippingOptions());
    }

    @Test
    void noOptionBeyondLastDistanceBand() throws Exception {
        ShippingQuote quote = quoteWithDistance(3551.0);
        List<ShippingOption> options = module.getShippingQuotes(quote, packages(), BigDecimal.TEN,
                delivery("J4B 8J9"), null, null, null, module("customQuotesRules"), null, Locale.CANADA);
        assertTrue(options.isEmpty());
    }

    @Test
    void missingDistanceDefaultsToZeroKilometres() throws Exception {
        ShippingQuote quote = new ShippingQuote();
        List<ShippingOption> options = module.getShippingQuotes(quote, packages(), BigDecimal.TEN,
                delivery("J4B 8J9"), null, null, null, module("customQuotesRules"), null, Locale.CANADA);
        assertEquals(1, options.size());
        assertEquals(new BigDecimal("140"), options.get(0).getOptionPrice());
    }

    @Test
    void returnsNullWithoutPostalCode() throws Exception {
        assertNull(module.getShippingQuotes(quoteWithDistance(10.0), packages(), BigDecimal.TEN,
                delivery(" "), null, null, null, module("customQuotesRules"), null, Locale.CANADA));
    }

    private static ShippingQuote quoteWithDistance(double distance) {
        ShippingQuote quote = new ShippingQuote();
        quote.getQuoteInformations().put(Constants.DISTANCE_KEY, distance);
        return quote;
    }

    private static List<PackageDetails> packages() {
        PackageDetails pack = new PackageDetails();
        pack.setShippingHeight(20);
        pack.setShippingLength(10);
        pack.setShippingWidth(30);
        pack.setShippingWeight(5);
        return Collections.singletonList(pack);
    }

    private static Delivery delivery(String postalCode) {
        Country country = new Country();
        country.setIsoCode("CA");
        Zone zone = new Zone();
        zone.setCode("QC");
        Delivery delivery = new Delivery();
        delivery.setCountry(country);
        delivery.setZone(zone);
        delivery.setPostalCode(postalCode);
        return delivery;
    }

    private static IntegrationModule module(String code) {
        IntegrationModule module = new IntegrationModule();
        module.setCode(code);
        return module;
    }
}
