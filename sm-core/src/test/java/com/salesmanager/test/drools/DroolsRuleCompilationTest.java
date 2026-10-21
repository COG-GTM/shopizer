package com.salesmanager.test.drools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.io.ResourceFactory;

public class DroolsRuleCompilationTest {

	private static final String RULES_PATH = "com/salesmanager/drools/rules/";

	@Test
	public void compilesEachRuleFileAndExposesItsExpectedRules() {
		assertCompiles("ShippingDecision.drl", "Shipping decision Canada Post", "Shipping decision Home Delivery");
		assertCompiles("PriceByDistance.drl", "530 kilometers or less", "3550 kilometers or less");
		assertCompiles("PriceByDistance2.drl", "40 kilometers or less", "80 kilometers or less",
				"2550 kilometers or less");
		assertCompiles("PromoCoupon.drl", "Bam0520");
	}

	private void assertCompiles(String fileName, String... expectedRules) {
		KieServices kieServices = KieServices.Factory.get();
		KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
		ReleaseId releaseId = kieServices.newReleaseId(
				"com.salesmanager.test",
				"drools-rule-compilation-" + UUID.randomUUID(),
				"1.0.0");
		kieFileSystem.generateAndWritePomXML(releaseId);
		kieFileSystem.write(ResourceFactory.newClassPathResource(RULES_PATH + fileName));

		KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
		kieBuilder.buildAll();
		Set<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR).stream()
				.collect(Collectors.toSet());

		assertTrue(fileName + " has Drools build errors: " + errors, errors.isEmpty());

		KieModule kieModule = kieBuilder.getKieModule();
		KieContainer container = kieServices.newKieContainer(kieModule.getReleaseId());
		try {
			KieBase kieBase = container.getKieBase();
			Set<String> actualRules = kieBase.getKiePackages().stream()
					.flatMap(kiePackage -> kiePackage.getRules().stream())
					.map(rule -> rule.getName())
					.collect(Collectors.toSet());
			assertEquals(new HashSet<String>(Arrays.asList(expectedRules)), actualRules);
		} finally {
			container.dispose();
		}
	}
}
