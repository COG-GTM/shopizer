package com.salesmanager.test.shop.integration.junit5;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.salesmanager.core.modules.integration.shipping.model.ShippingQuoteModule;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class Junit5SpringContextIntegrationTest extends ServicesTestSupport {

	@Autowired
	private ApplicationContext applicationContext;

	private HttpHeaders adminHeaders;

	@BeforeEach
	void buildAdminHeader() {
		adminHeaders = getHeader();
	}

	@Test
	void springContextAndJupiterIntegrationAreReady() {
		ResponseEntity<ReadableUser> profile = testRestTemplate.exchange(
				"/api/v1/private/user/profile", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), ReadableUser.class);
		Map<String, ShippingQuoteModule> shippingModules =
				applicationContext.getBeansOfType(ShippingQuoteModule.class);
		ClassLoader loader = applicationContext.getClassLoader() == null
				? Thread.currentThread().getContextClassLoader()
				: applicationContext.getClassLoader();
		int port = ((ServletWebServerApplicationContext) applicationContext).getWebServer().getPort();

		assertAll(
				() -> assertTrue(port > 0),
				() -> assertEquals(HttpStatus.OK, profile.getStatusCode()),
				() -> assertEquals("admin@shopizer.com", profile.getBody().getEmailAddress()),
				() -> assertTrue(shippingModules.containsKey("canadapost")),
				() -> assertThrows(ClassNotFoundException.class,
						() -> Class.forName("org.junit.Test", false, loader)),
				() -> assertThrows(ClassNotFoundException.class,
						() -> Class.forName("org.junit.Assert", false, loader)),
				() -> assertNotNull(testRestTemplate));
	}
}
