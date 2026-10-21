package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.salesmanager.core.business.configuration.events.products.listeners.IndexProductEventListener;
import com.salesmanager.core.business.services.catalog.category.CategoryService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class Boot27WebConfigurationIntegrationTest extends ServicesTestSupport {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping requestMappingHandlerMapping;

	@Autowired
	private Environment environment;

	@Autowired
	private ProductService productService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private IndexProductEventListener indexProductEventListener;

	@Autowired
	private WebMvcRequestHandlerProvider webMvcRequestHandlerProvider;

	@Test
	public void boot27AntPathAndSpringfoxConfigurationIsActive() {
		assertNull(requestMappingHandlerMapping.getPatternParser());
		assertTrue(requestMappingHandlerMapping.getPathMatcher() instanceof AntPathMatcher);
		assertEquals("ant_path_matcher", environment.getProperty("spring.mvc.pathmatch.matching-strategy"));
		assertTrue(SpringBootVersion.getVersion().startsWith("2.7."));
		assertEquals("true", environment.getProperty("spring.main.allow-circular-references"));
		assertNotNull(productService);
		assertNotNull(categoryService);
		assertNotNull(indexProductEventListener);
		assertNotNull(ReflectionTestUtils.getField(indexProductEventListener, "productService"));

		List<?> handlerMappings = handlerMappings(webMvcRequestHandlerProvider);
		assertFalse(handlerMappings.isEmpty());
		for (Object handlerMapping : handlerMappings) {
			assertTrue(handlerMapping instanceof RequestMappingInfoHandlerMapping);
			assertNull(((RequestMappingInfoHandlerMapping) handlerMapping).getPatternParser());
		}
	}

	@Test
	public void antPathMatchingSupportsExpectedHttpRoutes() {
		ResponseEntity<String> ping = testRestTemplate.getForEntity("/actuator/health/ping/", String.class);
		assertEquals(HttpStatus.OK, ping.getStatusCode());

		ResponseEntity<String> products = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", String.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());

		ResponseEntity<String> trailingSlashProducts = testRestTemplate
				.getForEntity("/api/v1/products/?store=DEFAULT", String.class);
		assertEquals(HttpStatus.OK, trailingSlashProducts.getStatusCode());

		ResponseEntity<String> storeWithSuffix = testRestTemplate
				.getForEntity("/api/v1/store/DEFAULT.json", String.class);
		assertEquals(HttpStatus.NOT_FOUND, storeWithSuffix.getStatusCode());

		String productCode = "ab534bt27pv";
		sampleProduct(productCode);
		ResponseEntity<ReadableProduct> product = testRestTemplate.getForEntity(
				"/api/v2/product/" + productCode + "?store=DEFAULT", ReadableProduct.class);
		assertEquals(HttpStatus.OK, product.getStatusCode());
		assertNotNull(product.getBody());
		assertEquals(productCode, product.getBody().getSku());
	}

	private static List<?> handlerMappings(WebMvcRequestHandlerProvider provider) {
		return (List<?>) ReflectionTestUtils.getField(provider, "handlerMappings");
	}
}
