package com.salesmanager.test.shop.smoke;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeScenarioInventoryTest {
    private static final Set<String> AB533 = Set.of(
            "adminLoginJwtCanAccessPrivateEndpoint",
            "customerRegistersLogsInAndReadsAuthenticatedProfile",
            "missingTokenAndCustomerTokenCannotAccessAdminApi",
            "privateProductCreateReadUpdateDeletePersistsChanges",
            "cartCreateAddUpdateRemovePersistsQuantities",
            "checkoutCreatesOrderThatAdminCanRead",
            "deletedPrivateProductShouldReturnNotFound",
            "servicesBasicAuthAcceptsAdminAndRejectsMissingOrWrongCredentials",
            "actuatorHealthIsUp");

    private Map<String, Method> scenarios(Class<?> suite) {
        Map<String, Method> methods = new HashMap<>();
        for (Class<?> type = suite; type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Test.class)) methods.putIfAbsent(method.getName(), method);
            }
        }
        return methods;
    }

    @Test
    void embeddedAndPackagedSuitesPreserveTheBaselineInventory() {
        for (Class<?> suite : new Class<?>[] {BehaviourSmokeTest.class, PackagedSmokeTest.class}) {
            assertEquals(AB533, scenarios(suite).keySet(), suite.getName());
        }
    }

    @Test
    void knownFailureRemainsDisabledInBothSuites() {
        for (Class<?> suite : new Class<?>[] {BehaviourSmokeTest.class, PackagedSmokeTest.class}) {
            Disabled disabled = scenarios(suite).get("deletedPrivateProductShouldReturnNotFound")
                    .getAnnotation(Disabled.class);
            assertNotNull(disabled);
            assertTrue(disabled.value().contains("KF-1"));
        }
    }
}
