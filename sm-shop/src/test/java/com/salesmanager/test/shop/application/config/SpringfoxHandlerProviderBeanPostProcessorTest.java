package com.salesmanager.test.shop.application.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import com.fasterxml.classmate.TypeResolver;
import com.salesmanager.shop.application.config.DocumentationConfiguration;

import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;
import springfox.documentation.spring.web.readers.operation.HandlerMethodResolver;

public class SpringfoxHandlerProviderBeanPostProcessorTest {

	@Test
	public void removesMappingsUsingPatternParser() throws Exception {
		RequestMappingHandlerMapping ant = new RequestMappingHandlerMapping();
		RequestMappingHandlerMapping parsed = new RequestMappingHandlerMapping();
		assertNull(ant.getPatternParser());
		parsed.setPatternParser(new PathPatternParser());
		WebMvcRequestHandlerProvider provider = provider(ant, parsed);
		BeanPostProcessor postProcessor = handlerProviderBeanPostProcessor();

		assertSame(provider, postProcessor.postProcessAfterInitialization(provider, "provider"));
		assertEquals(Collections.singletonList(ant), handlerMappings(provider));
	}

	@Test
	public void leavesAntMappingsUnchanged() throws Exception {
		RequestMappingHandlerMapping first = new RequestMappingHandlerMapping();
		RequestMappingHandlerMapping second = new RequestMappingHandlerMapping();
		WebMvcRequestHandlerProvider provider = provider(first, second);
		BeanPostProcessor postProcessor = handlerProviderBeanPostProcessor();

		postProcessor.postProcessAfterInitialization(provider, "provider");

		assertEquals(Arrays.asList(first, second), handlerMappings(provider));
	}

	@Test
	public void leavesNonProviderBeansAndPatternParsedMappingsUnchanged() throws Exception {
		RequestMappingHandlerMapping parsed = new RequestMappingHandlerMapping();
		parsed.setPatternParser(new PathPatternParser());
		Object other = new Object();
		BeanPostProcessor postProcessor = handlerProviderBeanPostProcessor();

		assertSame(other, postProcessor.postProcessAfterInitialization(other, "other"));
		assertSame(parsed, postProcessor.postProcessAfterInitialization(parsed, "mapping"));
		assertTrue(parsed.getPatternParser() != null);
	}

	@Test
	public void beanPostProcessorFactoryMethodIsStatic() throws Exception {
		Method method = DocumentationConfiguration.class
				.getDeclaredMethod("springfoxHandlerProviderBeanPostProcessor");

		assertTrue(Modifier.isStatic(method.getModifiers()));
	}

	private static WebMvcRequestHandlerProvider provider(RequestMappingHandlerMapping... mappings) {
		List<RequestMappingInfoHandlerMapping> handlerMappings = new ArrayList<>(Arrays.asList(mappings));
		return new WebMvcRequestHandlerProvider(new HandlerMethodResolver(new TypeResolver()), handlerMappings);
	}

	private static BeanPostProcessor handlerProviderBeanPostProcessor() throws Exception {
		Method method = DocumentationConfiguration.class
				.getDeclaredMethod("springfoxHandlerProviderBeanPostProcessor");
		return (BeanPostProcessor) method.invoke(null);
	}

	@SuppressWarnings("unchecked")
	private static List<Object> handlerMappings(WebMvcRequestHandlerProvider provider) {
		return (List<Object>) ReflectionTestUtils.getField(provider, "handlerMappings");
	}
}
