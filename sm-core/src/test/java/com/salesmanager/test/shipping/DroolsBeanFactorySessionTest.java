package com.salesmanager.test.shipping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;

/** AB-542: rule compilation and the DroolsBeanFactory session API used by the shipping/promo modules. */
class DroolsBeanFactorySessionTest {

    private static final String RULES_PATH = "com/salesmanager/drools/rules/";

    @ParameterizedTest
    @CsvSource({ "PriceByDistance.drl", "PriceByDistance2.drl", "PromoCoupon.drl", "ShippingDecision.drl" })
    void ruleFileCompilesWithoutErrorsOrWarnings(String drl) {
        KieServices services = KieServices.Factory.get();
        KieFileSystem fs = services.newKieFileSystem();
        fs.write(ResourceFactory.newClassPathResource(RULES_PATH + drl));
        KieBuilder builder = services.newKieBuilder(fs).buildAll();
        assertTrue(builder.getResults().getMessages(Message.Level.ERROR).isEmpty(),
                () -> drl + " errors: " + builder.getResults().getMessages(Message.Level.ERROR));
        assertTrue(builder.getResults().getMessages(Message.Level.WARNING).isEmpty(),
                () -> drl + " warnings: " + builder.getResults().getMessages(Message.Level.WARNING));
    }

    @Test
    void consecutiveSessionsOnSharedFactoryOnlyContainTheirOwnRules() {
        DroolsBeanFactory factory = new DroolsBeanFactory();
        assertRules(factory, "ShippingDecision.drl",
                "Shipping decision Canada Post", "Shipping decision Home Delivery");
        assertRules(factory, "PromoCoupon.drl", "Bam0520");
        assertRules(factory, "PriceByDistance.drl", "530 kilometers or less", "3550 kilometers or less");
        assertRules(factory, "PriceByDistance2.drl",
                "40 kilometers or less", "80 kilometers or less", "2550 kilometers or less");
        assertRules(factory, "ShippingDecision.drl",
                "Shipping decision Canada Post", "Shipping decision Home Delivery");
    }

    @Test
    void configuredSessionCombinesPriceByDistanceAndShippingDecisionRules() {
        DroolsBeanFactory factory = new DroolsBeanFactory();
        ReflectionTestUtils.setField(factory, "priceByDistance", "PriceByDistance.drl");
        ReflectionTestUtils.setField(factory, "shippingDecision", "ShippingDecision.drl");

        KieSession session = factory.getKieSession();
        try {
            assertEquals(new TreeSet<>(Arrays.asList("530 kilometers or less", "3550 kilometers or less",
                    "Shipping decision Canada Post", "Shipping decision Home Delivery")), ruleNames(session));

            ShippingInputParameters input = new ShippingInputParameters();
            input.setWeight(10);
            input.setSize(20);
            input.setCountry("CA");
            input.setProvince("ON");
            input.setDistance(100);
            DecisionResponse response = new DecisionResponse();
            session.setGlobal("decision", response);
            session.insert(input);
            assertEquals(3, session.fireAllRules());
            assertEquals("canadapost", response.getModuleName());
            assertEquals("140", response.getCustomPrice());
        } finally {
            session.dispose();
        }
    }

    @Test
    void sessionsAreIndependentAcrossRepeatedCalls() {
        DroolsBeanFactory factory = new DroolsBeanFactory();
        for (int i = 0; i < 3; i++) {
            KieSession session = factory.getKieSession(
                    ResourceFactory.newClassPathResource(RULES_PATH + "ShippingDecision.drl"));
            try {
                ShippingInputParameters input = new ShippingInputParameters();
                input.setWeight(70);
                input.setSize(78);
                input.setCountry("CA");
                input.setProvince("QC");
                DecisionResponse response = new DecisionResponse();
                session.setGlobal("decision", response);
                session.insert(input);
                assertEquals(1, session.fireAllRules());
                assertEquals("priceByDistance", response.getModuleName());
                assertEquals(1, session.getFactCount());
            } finally {
                session.dispose();
            }
        }
    }

    private static void assertRules(DroolsBeanFactory factory, String drl, String... expected) {
        KieSession session = factory.getKieSession(ResourceFactory.newClassPathResource(RULES_PATH + drl));
        try {
            assertNotNull(session);
            assertEquals(new TreeSet<>(Arrays.asList(expected)), ruleNames(session), drl);
        } finally {
            session.dispose();
        }
    }

    private static Set<String> ruleNames(KieSession session) {
        return session.getKieBase().getKiePackages().stream()
                .filter(p -> "com.salesmanager.drools.rules".equals(p.getName()))
                .map(KiePackage::getRules)
                .flatMap(rules -> rules.stream().map(Rule::getName))
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
