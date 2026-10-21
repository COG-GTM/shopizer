package com.salesmanager.test.drools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.jsoup.helper.Validate;
import org.junit.Test;
import org.mvel2.MVEL;

public class DroolsDependencyContractTest {

	@Test
	public void removedDroolsIntegrationsAreNotOnTheRuntimeClasspath() {
		assertClassMissing("org.kie.scanner.KieRepositoryScannerImpl");
		assertClassMissing("org.kie.spring.KModuleBeanFactoryPostProcessor");
	}

	@Test
	public void jsoupAndMvelRuntimeDependenciesAreAvailable() {
		assertNotNull(Validate.class);

		try {
			Validate.notNull(null, "x");
			fail("Validate.notNull must reject null");
		} catch (IllegalArgumentException expected) {
			assertEquals("x", expected.getMessage());
		}

		assertEquals(2, ((Number) MVEL.eval("1 + 1")).intValue());
	}

	private void assertClassMissing(String className) {
		try {
			Class.forName(className);
			fail(className + " must not be present");
		} catch (ClassNotFoundException expected) {
			assertNotNull(expected);
		}
	}
}
