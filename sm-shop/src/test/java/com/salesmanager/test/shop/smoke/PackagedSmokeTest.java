package com.salesmanager.test.shop.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

/** Opt-in only; no embedded Spring context or in-process persistence substitutes. */
@EnabledIfSystemProperty(named = "parity.url", matches = "http://127\\.0\\.0\\.1:[0-9]+")
class PackagedSmokeTest extends BehaviourSmokeScenarios {
    PackagedSmokeTest() {
        http = new TestRestTemplate(new RestTemplateBuilder().rootUri(System.getProperty("parity.url")));
    }

    @Override
    @Test
    void checkoutCreatesOrderThatAdminCanRead() throws Exception {
        Files.writeString(Path.of(System.getProperty("parity.output"), "checkout-mail.json"), checkout().toString());
    }
}
