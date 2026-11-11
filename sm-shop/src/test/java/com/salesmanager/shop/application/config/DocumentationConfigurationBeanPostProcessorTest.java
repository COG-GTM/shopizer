package com.salesmanager.shop.application.config;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

public class DocumentationConfigurationBeanPostProcessorTest {
    private final BeanPostProcessor processor = DocumentationConfiguration.springfoxHandlerProviderBeanPostProcessor();

    @Test
    public void removesParsedMappingsAndPreservesAntMapping() {
        RequestMappingHandlerMapping parsed = new RequestMappingHandlerMapping();
        parsed.setPatternParser(new PathPatternParser());
        RequestMappingHandlerMapping ant = new RequestMappingHandlerMapping();
        List<RequestMappingInfoHandlerMapping> mappings = new ArrayList<>(Arrays.asList(parsed, ant));
        WebMvcRequestHandlerProvider provider = new WebMvcRequestHandlerProvider(null, mappings);
        assertSame(provider, processor.postProcessAfterInitialization(provider, "provider"));
        assertEquals(Arrays.asList(ant), mappings);
    }

    @Test
    public void leavesOtherBeansUnchanged() {
        Object bean = new Object();
        assertSame(bean, processor.postProcessAfterInitialization(bean, "other"));
    }

    @Test
    public void acceptsEmptyMappings() {
        List<RequestMappingInfoHandlerMapping> mappings = new ArrayList<>();
        WebMvcRequestHandlerProvider provider = new WebMvcRequestHandlerProvider(null, mappings);
        assertSame(provider, processor.postProcessAfterInitialization(provider, "empty"));
        assertTrue(mappings.isEmpty());
    }
}
