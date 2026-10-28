package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

public class ProductWriteRouteMappingTest {

	@Test
	public void productWriteMappingsDoNotExposeCustomerAliases() {
		assertNoCustomerWriteAliases(ProductApi.class);
		assertNoCustomerWriteAliases(ProductImageApi.class);
	}

	@Test
	public void productWriteRoutesRemainAvailableUnderPrivatePaths() {
		assertMapping(ProductApi.class, "create", RequestMethod.POST, "/private/product");
		assertMapping(ProductApi.class, "update", RequestMethod.PUT, "/private/product/{id}");
		assertMapping(ProductApi.class, "delete", RequestMethod.DELETE, "/private/product/{id}");
		assertMapping(ProductApi.class, "changeProductOrder", RequestMethod.PATCH, "/private/product/{id}");
		assertMapping(ProductImageApi.class, "uploadImage", RequestMethod.POST, "/private/product/{id}/image");
		assertMapping(ProductImageApi.class, "deleteImage", RequestMethod.DELETE, "/private/product/image/{id}");
		assertMapping(ProductImageApi.class, "imageDetails", RequestMethod.PATCH,
				"/private/product/{id}/image/{imageId}");
	}

	private void assertNoCustomerWriteAliases(Class<?> controller) {
		for (Method method : controller.getDeclaredMethods()) {
			RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
			if (mapping == null || !hasWriteMethod(mapping)) {
				continue;
			}
			for (String path : mapping.value()) {
				assertFalse(controller.getSimpleName() + "." + method.getName() + " exposes " + path,
						path.startsWith("/auth/"));
			}
		}
	}

	private boolean hasWriteMethod(RequestMapping mapping) {
		return Arrays.stream(mapping.method()).anyMatch(method -> method == RequestMethod.POST
				|| method == RequestMethod.PUT || method == RequestMethod.PATCH || method == RequestMethod.DELETE);
	}

	private void assertMapping(Class<?> controller, String methodName, RequestMethod requestMethod, String path) {
		boolean found = Arrays.stream(controller.getDeclaredMethods())
				.filter(method -> method.getName().equals(methodName))
				.map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
				.filter(mapping -> mapping != null && Arrays.asList(mapping.method()).contains(requestMethod))
				.anyMatch(mapping -> Arrays.asList(mapping.value()).contains(path));
		assertTrue(controller.getSimpleName() + "." + methodName + " should map " + requestMethod + " " + path,
				found);
	}
}
