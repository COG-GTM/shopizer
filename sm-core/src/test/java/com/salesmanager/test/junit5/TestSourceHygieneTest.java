package com.salesmanager.test.junit5;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TestSourceHygieneTest {

	@Test
	void testSourcesUseOnlyJupiterAnnotationsAndDocumentDisabledTests() throws Exception {
		Path classRoot = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
		Path testPackage = classRoot.resolve("com/salesmanager/test");
		List<Path> classFiles;
		try (Stream<Path> paths = Files.walk(testPackage)) {
			classFiles = paths.filter(path -> path.toString().endsWith(".class"))
					.filter(path -> !path.getFileName().toString().equals("package-info.class"))
					.collect(Collectors.toList());
		}

		ClassLoader loader = getClass().getClassLoader();
		List<Class<?>> classes = new ArrayList<>();
		List<String> loadFailures = new ArrayList<>();
		for (Path classFile : classFiles) {
			String className = testPackage.relativize(classFile).toString()
					.replace(classFile.getFileSystem().getSeparator(), ".");
			className = className.substring(0, className.length() - ".class".length());
			try {
				classes.add(Class.forName("com.salesmanager.test." + className, false, loader));
			} catch (ClassNotFoundException | LinkageError error) {
				loadFailures.add(className + ": " + error.getClass().getSimpleName());
			}
		}

		List<String> disabledWithoutReason = new ArrayList<>();
		List<String> legacyAnnotations = new ArrayList<>();
		for (Class<?> testClass : classes) {
			Disabled classDisabled = testClass.getDeclaredAnnotation(Disabled.class);
			if (classDisabled != null && classDisabled.value().trim().isEmpty()) {
				disabledWithoutReason.add(testClass.getName());
			}
			checkAnnotations(testClass.getName(), testClass.getDeclaredAnnotations(), legacyAnnotations);
			for (Method method : testClass.getDeclaredMethods()) {
				Disabled methodDisabled = method.getDeclaredAnnotation(Disabled.class);
				if (methodDisabled != null && methodDisabled.value().trim().isEmpty()) {
					disabledWithoutReason.add(testClass.getName() + "#" + method.getName());
				}
				checkAnnotations(testClass.getName() + "#" + method.getName(),
						method.getDeclaredAnnotations(), legacyAnnotations);
			}
			for (Field field : testClass.getDeclaredFields()) {
				checkAnnotations(testClass.getName() + "." + field.getName(),
						field.getDeclaredAnnotations(), legacyAnnotations);
			}
		}

		Class<?> shippingTest = Class.forName(
				"com.salesmanager.test.shipping.ShippingMethodDecisionTest", false, loader);
		Disabled shippingDisabled = shippingTest.getDeclaredAnnotation(Disabled.class);
		Class<?> baseTest = Class.forName(
				"com.salesmanager.test.common.AbstractSalesManagerCoreTestCase", false, loader);
		boolean baseDisabled = baseTest.isAnnotationPresent(Disabled.class);
		List<String> baseTestMethods = Stream.of(baseTest.getDeclaredMethods())
				.filter(method -> method.isAnnotationPresent(Test.class))
				.map(Method::getName)
				.collect(Collectors.toList());

		assertAll(
				() -> assertTrue(loadFailures.isEmpty(), () -> "Could not load: " + loadFailures),
				() -> assertTrue(disabledWithoutReason.isEmpty(),
						() -> "Disabled tests without reasons: " + disabledWithoutReason),
				() -> assertTrue(legacyAnnotations.isEmpty(),
						() -> "JUnit 4 annotations: " + legacyAnnotations),
				() -> assertTrue(shippingDisabled != null
								&& shippingDisabled.value().contains("AB-542"),
						"ShippingMethodDecisionTest must document AB-542"),
				() -> assertFalse(baseDisabled, "AbstractSalesManagerCoreTestCase must not be disabled"),
				() -> assertTrue(baseTestMethods.isEmpty(),
						() -> "AbstractSalesManagerCoreTestCase declares @Test methods: " + baseTestMethods));
	}

	private static void checkAnnotations(
			String element,
			Annotation[] annotations,
			List<String> offenders) {
		for (Annotation annotation : annotations) {
			String annotationName = annotation.annotationType().getName();
			if (annotationName.startsWith("org.junit.")
					&& !annotationName.startsWith("org.junit.jupiter.")) {
				offenders.add(element + " -> " + annotationName);
			}
		}
	}
}
