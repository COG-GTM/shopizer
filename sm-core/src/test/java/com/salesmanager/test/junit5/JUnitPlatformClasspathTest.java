package com.salesmanager.test.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class JUnitPlatformClasspathTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"org.junit.Test",
			"org.junit.Assert",
			"org.junit.Ignore",
			"org.junit.runner.RunWith",
			"org.junit.vintage.engine.VintageTestEngine"
	})
	void junitFourClassesAreNotLoadable(String className) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();

		assertThrows(ClassNotFoundException.class, () -> Class.forName(className, false, loader));
	}

	@Test
	void onlyJupiterEngineIsDiscovered() throws Exception {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Class<?> engineType = Class.forName(
				"org.junit.platform.engine.TestEngine", false, loader);
		List<String> engineIds = new ArrayList<>();

		for (Object engine : ServiceLoader.load(engineType, loader)) {
			Method getId = engineType.getMethod("getId");
			engineIds.add((String) getId.invoke(engine));
		}

		assertEquals(List.of("junit-jupiter"), engineIds);
	}
}
