package com.salesmanager.test.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.services.catalog.category.CategoryService;
import com.salesmanager.core.business.services.catalog.product.ProductService;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigurationTest.class)
public class SpringBoot27ContextTest {

	@Autowired
	private Environment environment;

	@Autowired
	private ProductService productService;

	@Autowired
	private CategoryService categoryService;

	@Test
	public void boot27LoadsServicesWithCircularReferencesEnabled() {
		assertTrue(SpringBootVersion.getVersion().startsWith("2.7."));
		assertEquals("true", environment.getProperty("spring.main.allow-circular-references"));
		assertNotNull(productService);
		assertNotNull(categoryService);
	}
}
