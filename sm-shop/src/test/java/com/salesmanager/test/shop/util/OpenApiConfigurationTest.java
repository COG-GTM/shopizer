package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.stereotype.Controller;

import com.salesmanager.shop.application.config.ShopApplicationConfiguration;

import io.swagger.v3.oas.annotations.tags.Tag;

class OpenApiConfigurationTest {
    @Test
    void byteArraysSupportWebpAndExistingMediaTypes() {
        ByteArrayHttpMessageConverter converter = new ShopApplicationConfiguration().byteArrayHttpMessageConverter();
        for (String value : List.of("image/webp", "image/jpeg", "image/gif", "image/png", "application/octet-stream")) {
            MediaType type = MediaType.parseMediaType(value);
            assertTrue(converter.getSupportedMediaTypes().contains(type), value);
            assertTrue(converter.canWrite(byte[].class, type), value);
            assertTrue(converter.canRead(byte[].class, type), value);
        }
        assertFalse(converter.canWrite(String.class, MediaType.parseMediaType("image/webp")));
    }

    @Test
    void legacySwaggerAnnotationsAreAbsent() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("io.swagger.annotations.Api"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("io.swagger.annotations.ApiModelProperty"));
    }

    @Test
    void migratedControllersHaveNonEmptyOpenApiTags() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        for (String version : List.of("v1", "v2")) {
            var candidates = scanner.findCandidateComponents("com.salesmanager.shop.store.api." + version);
            assertFalse(candidates.isEmpty());
            int tagged = 0;
            for (var candidate : candidates) {
                Class<?> controller = Class.forName(candidate.getBeanClassName());
                Tag tag = controller.getAnnotation(Tag.class);
                if (tag != null) {
                    tagged++;
                    assertFalse(tag.name().isBlank(), controller.getName());
                }
            }
            assertTrue(tagged > 0, version);
        }
    }
}
