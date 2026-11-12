package com.salesmanager.test.shop.integration.parity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PackagedParityInProcessIntegrationTest {
    @Autowired private ServletWebServerApplicationContext context;
    @Autowired private TestRestTemplate http;
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void clearProperties() {
        for (String property : new String[] {"parity.url", "parity.output", "parity.database"}) {
            System.clearProperty(property);
        }
    }

    @Test
    void packagedClientsExerciseContractAndNonMailScenariosAgainstEmbeddedServer() throws Throwable {
        Path output = Path.of("target", "parity-it");
        JsonNode health = json.readTree(http.getForObject("/actuator/health", String.class));
        String database = health.path("components").path("db").path("details").path("database").asText();
        assertEquals("H2", database);
        System.setProperty("parity.url", "http://127.0.0.1:" + context.getWebServer().getPort());
        System.setProperty("parity.output", output.toString());
        System.setProperty("parity.database", database);
        Files.deleteIfExists(output.resolve("contract-diff.json"));
        invoke(instance("com.salesmanager.test.shop.contract.PackagedContractTest"),
                "openApiInventoryAndOperationalEndpointsPreserveContract");
        JsonNode diff = json.readTree(output.resolve("contract-diff.json").toFile());
        assertTrue(diff.path("removed").isArray());
        assertEquals(0, diff.path("removed").size());
        assertTrue(diff.path("baselineCount").asInt() > 0);
        assertEquals(diff.path("baselineCount"), diff.path("currentCount"));
        Object smoke = instance("com.salesmanager.test.shop.smoke.PackagedSmokeTest");
        for (String scenario : new String[] {"adminLoginJwtCanAccessPrivateEndpoint",
                "customerRegistersLogsInAndReadsAuthenticatedProfile",
                "missingTokenAndCustomerTokenCannotAccessAdminApi", "actuatorHealthIsUp"}) {
            invoke(smoke, scenario);
        }
    }

    private Object instance(String name) throws Exception {
        Constructor<?> constructor = Class.forName(name).getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private void invoke(Object instance, String name) throws Throwable {
        Class<?> type = instance.getClass();
        while (type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                try {
                    method.invoke(instance);
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
                return;
            } catch (NoSuchMethodException missing) {
                type = type.getSuperclass();
            }
        }
        fail("Missing scenario " + name);
    }
}
