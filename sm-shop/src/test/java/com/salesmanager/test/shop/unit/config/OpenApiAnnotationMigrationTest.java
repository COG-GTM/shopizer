package com.salesmanager.test.shop.unit.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;
import org.springdoc.api.annotations.ParameterObject;

import com.salesmanager.shop.model.customer.CustomerEntity;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.api.v1.user.AuthenticateUserApi;
import com.salesmanager.shop.store.api.v2.product.ProductApiV2;
import com.salesmanager.core.model.catalog.product.ProductCriteria;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;

public class OpenApiAnnotationMigrationTest {

	@Test
	public void customerModelsCarryOpenApiSchemaMetadata() throws Exception {
		Schema customerSchema = PersistableCustomer.class.getAnnotation(Schema.class);
		assertNotNull(customerSchema);
		assertEquals("Customer", customerSchema.name());
		assertEquals("Customer model object", customerSchema.description());

		Field emailAddress = CustomerEntity.class.getDeclaredField("emailAddress");
		assertEquals("Customer email address. Required for registration",
				emailAddress.getAnnotation(Schema.class).description());

		Field country = Address.class.getDeclaredField("country");
		assertTrue(country.getAnnotation(Schema.class).description().contains("country code"));
	}

	@Test
	public void productListUsesParameterObjectForProductCriteria() {
		Method productList = Arrays.stream(ProductApiV2.class.getDeclaredMethods())
				.filter(method -> method.getName().equals("list"))
				.filter(method -> Arrays.stream(method.getParameterTypes())
						.anyMatch(ProductCriteria.class::equals))
				.findFirst()
				.orElseThrow(() -> new AssertionError("ProductApiV2.list(ProductCriteria) was not found"));

		assertTrue(Arrays.stream(productList.getParameters())
				.filter(parameter -> parameter.getType().equals(ProductCriteria.class))
				.anyMatch(parameter -> parameter.isAnnotationPresent(ParameterObject.class)));
	}

	@Test
	public void authenticateUserHandlerHasOpenApiOperationAnnotation() throws Exception {
		Method authenticate = Arrays.stream(AuthenticateUserApi.class.getDeclaredMethods())
				.filter(method -> method.getName().equals("authenticate"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("AuthenticateUserApi.authenticate was not found"));

		assertNotNull(authenticate.getAnnotation(Operation.class));
	}

	@Test
	public void springfoxAnnotationsAreAbsentFromTheTestClasspath() {
		assertClassNotPresent("springfox.documentation.spring.web.plugins.Docket");
		assertClassNotPresent("io.swagger.annotations.ApiModel");
	}

	private void assertClassNotPresent(String className) {
		try {
			Class.forName(className);
			fail("Expected " + className + " to be absent from the test classpath");
		} catch (ClassNotFoundException expected) {
			// Expected: the migration must not leave the Swagger 2 dependency behind.
		}
	}
}
