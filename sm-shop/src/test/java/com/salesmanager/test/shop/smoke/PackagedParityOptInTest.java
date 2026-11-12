package com.salesmanager.test.shop.smoke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

class PackagedParityOptInTest {
    @Test
    void packagedSuitesOnlyAcceptExplicitLoopbackHttpPorts() throws Exception {
        for (Class<?> suite : new Class<?>[] {PackagedSmokeTest.class,
                Class.forName("com.salesmanager.test.shop.contract.PackagedContractTest")}) {
            EnabledIfSystemProperty gate = suite.getAnnotation(EnabledIfSystemProperty.class);
            assertNotNull(gate);
            assertEquals("parity.url", gate.named());
            assertTrue("http://127.0.0.1:8080".matches(gate.matches()));
            for (String rejected : new String[] {"http://localhost:8080", "https://127.0.0.1:8080",
                    "http://10.0.0.5:8080", "http://127.0.0.1:8080/x", "http://127.0.0.1"}) {
                assertFalse(rejected.matches(gate.matches()), rejected);
            }
        }
    }

    @Test
    void packagedSmokeUsesSharedScenariosWithoutAnEmbeddedContext() {
        assertEquals(BehaviourSmokeScenarios.class, PackagedSmokeTest.class.getSuperclass());
        assertNull(PackagedSmokeTest.class.getAnnotation(SpringBootTest.class));
    }
}
