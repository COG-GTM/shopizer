package com.salesmanager.test.shipping;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalResponse;

class DroolsBeanFactoryTest {
    private static final String PATH = "com/salesmanager/drools/rules/";

    @Test
    void repeatedContainersHaveConsistentReleaseIdsAndIndependentWorkingMemory() throws Exception {
        DroolsBeanFactory factory = new DroolsBeanFactory();
        ReflectionTestUtils.setField(factory, "priceByDistance", PATH + "PriceByDistance.drl");
        ReflectionTestUtils.setField(factory, "shippingDecision", PATH + "ShippingDecision.drl");
        KieContainer first = factory.getKieContainer();
        KieContainer second = factory.getKieContainer();
        try {
            assertNotSame(first, second);
            assertEquals(first.getReleaseId(), second.getReleaseId());
            KieSession populated = first.newKieSession();
            KieSession empty = second.newKieSession();
            try {
                assertShippingDecision(populated);
                assertEquals(0, empty.getFactCount());
                assertEquals(0, empty.fireAllRules());
            } finally {
                populated.dispose();
                empty.dispose();
            }
        } finally {
            first.dispose();
            second.dispose();
        }
    }

    @Test
    void configuredSessionBuildsAndFiresShippingRules() {
        DroolsBeanFactory factory = new DroolsBeanFactory();
        ReflectionTestUtils.setField(factory, "priceByDistance", "PriceByDistance.drl");
        ReflectionTestUtils.setField(factory, "shippingDecision", "ShippingDecision.drl");
        KieSession session = factory.getKieSession();
        try {
            assertShippingDecision(session);
        } finally {
            session.dispose();
        }
    }

    @Test
    void resourceSessionFiresPromoAndDoesNotRetainFactsAcrossBuilds() {
        DroolsBeanFactory factory = new DroolsBeanFactory();
        for (int i = 0; i < 2; i++) {
            KieSession session = factory.getKieSession(ResourceFactory.newClassPathResource(PATH + "PromoCoupon.drl"));
            try {
                assertEquals(0, session.getFactCount());
                OrderTotalInputParameters input = new OrderTotalInputParameters();
                input.setPromoCode("Test1234");
                input.setDate(Date.from(LocalDate.of(2025, 10, 30).atStartOfDay(ZoneId.systemDefault()).toInstant()));
                OrderTotalResponse total = new OrderTotalResponse();
                session.setGlobal("total", total);
                session.insert(input);
                assertEquals(1, session.fireAllRules());
                assertEquals(0.10, total.getDiscount());
                assertEquals(0, session.fireAllRules());
            } finally {
                session.dispose();
            }
        }
    }

    @Test
    void invalidDrlFailsFastWithBuildDiagnostics() {
        var resource = ResourceFactory.newByteArrayResource(
                "package broken; rule invalid when NotARealFact() then end".getBytes(StandardCharsets.UTF_8));
        resource.setSourcePath("src/main/resources/broken.drl");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new DroolsBeanFactory().getKieSession(resource));
        assertTrue(error.getMessage().contains("Unable to build Drools rules"));
        assertTrue(error.getMessage().contains("NotARealFact"));
    }

    private void assertShippingDecision(KieSession session) {
        ShippingInputParameters input = new ShippingInputParameters();
        input.setWeight(61);
        input.setSize(65);
        input.setCountry("CA");
        input.setProvince("ON");
        DecisionResponse decision = new DecisionResponse();
        session.setGlobal("decision", decision);
        session.insert(input);
        assertTrue(session.fireAllRules() > 0);
        assertEquals("canadapost", decision.getModuleName());
    }
}
