package com.salesmanager.test.shop.unit.product;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.v1.product.ProductApi;

public class ProductApiWriteMappingTest {

	@Test
	public void writeMappingsDoNotExposeAuthAliases() {
		for (Method method : ProductApi.class.getMethods()) {
			if (!isWriteMapping(method)) {
				continue;
			}
			for (String path : paths(method)) {
				assertFalse(method.getName() + " exposes " + path, path.startsWith("/auth/"));
			}
		}
	}

	@Test
	public void productWritesMapToPrivateRoutes() throws Exception {
		assertTrue(paths(ProductApi.class.getMethod("create", PersistableProduct.class, MerchantStore.class,
				Language.class)).contains("/private/product"));
		assertTrue(paths(ProductApi.class.getMethod("update", Long.class, PersistableProduct.class,
				MerchantStore.class, javax.servlet.http.HttpServletRequest.class,
				javax.servlet.http.HttpServletResponse.class)).contains("/private/product/{id}"));
		assertTrue(paths(ProductApi.class.getMethod("delete", Long.class, MerchantStore.class, Language.class))
				.contains("/private/product/{id}"));
		assertTrue(paths(ProductApi.class.getMethod("changeProductOrder", Long.class, Integer.class,
				MerchantStore.class, Language.class)).contains("/private/product/{id}"));
	}

	private boolean isWriteMapping(Method method) {
		if (method.isAnnotationPresent(PostMapping.class) || method.isAnnotationPresent(PutMapping.class)
				|| method.isAnnotationPresent(DeleteMapping.class) || method.isAnnotationPresent(PatchMapping.class)) {
			return true;
		}
		RequestMapping mapping = method.getAnnotation(RequestMapping.class);
		if (mapping == null) {
			return false;
		}
		for (RequestMethod requestMethod : mapping.method()) {
			if (requestMethod == RequestMethod.POST || requestMethod == RequestMethod.PUT
					|| requestMethod == RequestMethod.DELETE || requestMethod == RequestMethod.PATCH) {
				return true;
			}
		}
		return false;
	}

	private List<String> paths(Method method) {
		List<String> paths = new ArrayList<>();
		RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
		if (requestMapping != null) {
			paths.addAll(Arrays.asList(requestMapping.value()));
			paths.addAll(Arrays.asList(requestMapping.path()));
		}
		PostMapping postMapping = method.getAnnotation(PostMapping.class);
		if (postMapping != null) {
			paths.addAll(Arrays.asList(postMapping.value()));
			paths.addAll(Arrays.asList(postMapping.path()));
		}
		PutMapping putMapping = method.getAnnotation(PutMapping.class);
		if (putMapping != null) {
			paths.addAll(Arrays.asList(putMapping.value()));
			paths.addAll(Arrays.asList(putMapping.path()));
		}
		DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
		if (deleteMapping != null) {
			paths.addAll(Arrays.asList(deleteMapping.value()));
			paths.addAll(Arrays.asList(deleteMapping.path()));
		}
		PatchMapping patchMapping = method.getAnnotation(PatchMapping.class);
		if (patchMapping != null) {
			paths.addAll(Arrays.asList(patchMapping.value()));
			paths.addAll(Arrays.asList(patchMapping.path()));
		}
		return paths;
	}
}
