package com.salesmanager.test.shipping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalResponse;

/** Fixed fixtures run unchanged on both Drools 7.32 and 7.74.1. */
class DroolsRuleParityTest {

    private static final String RULES_PATH = "com/salesmanager/drools/rules/";
    private final DroolsBeanFactory factory = new DroolsBeanFactory();

    @ParameterizedTest
    @CsvSource({
        "61, 65, CA, ON, canadapost, 1",
        "63, 65, CA, QC, priceByDistance, 1",
        "61, 67, CA, QC, priceByDistance, 1",
        "70, 78, CA, QC, priceByDistance, 1",
        "62, 66, CA, QC, , 0",
        "63, 67, CA, ON, , 0",
        "61, 65, US, NY, , 0"
    })
    void shippingDecision(long weight, long size, String country, String province,
            String expectedModule, int expectedRules) {
        ShippingInputParameters input = new ShippingInputParameters();
        input.setWeight(weight);
        input.setSize(size);
        input.setCountry(country);
        input.setProvince(province);
        DecisionResponse response = new DecisionResponse();
        KieSession session = session("ShippingDecision.drl");
        try {
            session.setGlobal("decision", response);
            session.insert(input);
            assertEquals(expectedRules, session.fireAllRules());
            assertEquals(expectedModule, response.getModuleName());
        } finally {
            session.dispose();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "PriceByDistance.drl, 0, 140, 2",
        "PriceByDistance.drl, 530, 140, 2",
        "PriceByDistance.drl, 531, 140, 1",
        "PriceByDistance.drl, 3550, 140, 1",
        "PriceByDistance.drl, 3551, , 0",
        "PriceByDistance2.drl, 0, 75, 1",
        "PriceByDistance2.drl, 40, 75, 1",
        "PriceByDistance2.drl, 41, 120, 1",
        "PriceByDistance2.drl, 80, 120, 1",
        "PriceByDistance2.drl, 81, 140, 1",
        "PriceByDistance2.drl, 2550, 140, 1",
        "PriceByDistance2.drl, 2551, , 0"
    })
    void priceByDistance(String rule, long distance, String expectedPrice, int expectedRules) {
        ShippingInputParameters input = new ShippingInputParameters();
        input.setDistance(distance);
        DecisionResponse response = new DecisionResponse();
        KieSession session = session(rule);
        try {
            session.setGlobal("decision", response);
            session.insert(input);
            assertEquals(expectedRules, session.fireAllRules());
            // Keep the overlapping bands in PriceByDistance.drl unchanged.
            assertEquals(expectedPrice, response.getCustomPrice());
        } finally {
            session.dispose();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "Test1234, 2025-10-30, 0.10, 1",
        "Test1234, 2025-10-31, , 0",
        "Test1234, 2025-11-01, , 0",
        "OTHER, 2025-10-30, , 0"
    })
    void promoCoupon(String promoCode, String date, Double expectedDiscount, int expectedRules) {
        OrderTotalInputParameters input = new OrderTotalInputParameters();
        input.setPromoCode(promoCode);
        input.setDate(Date.from(LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        OrderTotalResponse response = new OrderTotalResponse();
        KieSession session = session("PromoCoupon.drl");
        try {
            session.setGlobal("total", response);
            session.insert(input);
            assertEquals(expectedRules, session.fireAllRules());
            assertEquals(expectedDiscount, response.getDiscount());
        } finally {
            session.dispose();
        }
    }

    private KieSession session(String rule) {
        return factory.getKieSession(ResourceFactory.newClassPathResource(RULES_PATH + rule));
    }
}
