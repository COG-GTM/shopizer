package com.salesmanager.test.shop.integration.docs;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.application.config.AsyncConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LabelUtils;
import com.salesmanager.test.shop.common.ServicesTestSupport;


@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTest extends ServicesTestSupport {
    @Autowired private ApplicationContext context;

    @Test
    void bothOpenApiDocumentsDescribeTheMigratedContract() {
        for (String path : new String[]{"/v3/api-docs", "/v3/api-docs/shopizer"}) {
            ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(path, JsonNode.class);
            assertEquals(200, response.getStatusCode().value(), path);
            assertTrue(response.getHeaders().getContentType().isCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON));
            JsonNode document = response.getBody();
            assertNotNull(document);
            assertTrue(document.path("openapi").asText().startsWith("3."));
            assertFalse(document.path("paths").isEmpty());
            JsonNode operation = document.path("paths").path("/api/v1/private/users/{id}").path("get");
            assertEquals("Get a specific user profile by user id", operation.path("summary").asText());
            assertParameter(operation, "store", "DEFAULT");
            assertParameter(operation, "lang", "en");
            assertEquals("#/components/schemas/ReadableUser", operation.at("/responses/200/content/*~1*/schema/$ref").asText());
            JsonNode schemas = document.path("components").path("schemas");
            assertEquals("Customer model object", schemas.path("Customer").path("description").asText());
            assertEquals("Customer password", schemas.path("Customer").path("properties").path("password").path("description").asText());
            assertEquals("Customer billing first name", schemas.path("Address").path("properties").path("firstName").path("description").asText());
            Set<String> tags = new HashSet<>();
            document.path("tags").forEach(tag -> tags.add(tag.path("name").asText()));
            assertTrue(tags.contains("User management resource (User Management Api)"));
            assertTrue(tags.contains("Category management resource (Category Management Api)"));
        }
    }

    @Test
    void swaggerUiRedirectsToWorkingIndex() throws Exception {
        int port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) context).getWebServer().getPort();
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            HttpResponse<String> redirect = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui.html")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(redirect.statusCode() >= 300 && redirect.statusCode() < 400);
            assertTrue(redirect.headers().firstValue("location").orElseThrow().contains("/swagger-ui/index.html"));
            HttpResponse<String> index = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui/index.html")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, index.statusCode());
            assertTrue(index.body().contains("swagger-ui"));
        }
    }

    @Test
    void javaConfigurationProvidesFormerXmlBeansAndLocaleBehaviour() throws Exception {
        assertInstanceOf(SessionLocaleResolver.class, context.getBean("localeResolver"));
        assertNotNull(context.getBean(LocaleChangeInterceptor.class));
        assertInstanceOf(ReloadableResourceBundleMessageSource.class, context.getBean("messageSource"));
        assertNotNull(context.getBean(LabelUtils.class));
        assertInstanceOf(ImageFilePath.class, context.getBean("img"));
        SessionLocaleResolver resolver = context.getBean(SessionLocaleResolver.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, resolver);
        request.setParameter("locale", "fr");
        assertTrue(context.getBean(LocaleChangeInterceptor.class).preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(Locale.FRENCH, resolver.resolveLocale(request));
    }

    @Test
    void asyncExecutorDispatchesOffCallingThread() throws Exception {
        long caller = Thread.currentThread().threadId();
        assertTrue(context.containsBean("org.springframework.context.annotation.internalAsyncAnnotationProcessor"));
        java.util.concurrent.ExecutorService executor = (java.util.concurrent.ExecutorService) context.getBean(AsyncConfig.class).getAsyncExecutor();
        try {
            assertNotEquals(caller, executor.submit(() -> Thread.currentThread().threadId()).get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertParameter(JsonNode operation, String name, String defaultValue) {
        JsonNode parameter = null;
        for (JsonNode candidate : operation.path("parameters")) {
            if (name.equals(candidate.path("name").asText())) parameter = candidate;
        }
        assertNotNull(parameter, name);
        assertEquals("query", parameter.path("in").asText());
        assertEquals("string", parameter.path("schema").path("type").asText());
        assertEquals(defaultValue, parameter.path("schema").path("default").asText());
    }

}
