package com.salesmanager.test.shop.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import com.salesmanager.shop.application.config.DocumentationConfiguration;

import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

public class DocumentationHandlerMappingsTest {

    private final BeanPostProcessor processor =
            DocumentationConfiguration.springfoxHandlerProviderBeanPostProcessor();

    @Test
    public void removesOnlyParsedMappingsAndKeepsLegacyOrder() {
        RequestMappingHandlerMapping first = new RequestMappingHandlerMapping();
        RequestMappingHandlerMapping parsed = new RequestMappingHandlerMapping();
        PathPatternParser parser = new PathPatternParser();
        parsed.setPatternParser(parser);
        RequestMappingHandlerMapping last = new RequestMappingHandlerMapping();
        List<RequestMappingInfoHandlerMapping> mappings =
                new ArrayList<>(Arrays.asList(first, parsed, last));
        WebMvcRequestHandlerProvider provider = provider(mappings);

        assertSame(provider, processor.postProcessAfterInitialization(provider, "provider"));

        assertSame(mappings, ReflectionTestUtils.getField(provider, "handlerMappings"));
        assertEquals(Arrays.asList(first, last), mappings);
        assertSame(parser, parsed.getPatternParser());
    }

    @Test
    public void leavesLegacyMappingsUntouchedOnRepeatedProcessing() {
        RequestMappingHandlerMapping legacy = new RequestMappingHandlerMapping();
        List<RequestMappingInfoHandlerMapping> mappings = new ArrayList<>(Arrays.asList(legacy));
        WebMvcRequestHandlerProvider provider = provider(mappings);

        processor.postProcessAfterInitialization(provider, "provider");
        processor.postProcessAfterInitialization(provider, "provider");

        assertEquals(Arrays.asList(legacy), mappings);
    }

    @Test
    public void acceptsEmptyProviderMappings() {
        List<RequestMappingInfoHandlerMapping> mappings = new ArrayList<>();
        WebMvcRequestHandlerProvider provider = provider(mappings);

        assertSame(provider, processor.postProcessAfterInitialization(provider, "provider"));
        assertEquals(0, mappings.size());
    }

    @Test
    public void ignoresUnrelatedBeans() {
        Object bean = new Object();
        assertSame(bean, processor.postProcessAfterInitialization(bean, "other"));
    }

    private WebMvcRequestHandlerProvider provider(List<RequestMappingInfoHandlerMapping> mappings) {
        WebMvcRequestHandlerProvider provider = mock(WebMvcRequestHandlerProvider.class);
        ReflectionTestUtils.setField(provider, "handlerMappings", mappings);
        return provider;
    }
}
