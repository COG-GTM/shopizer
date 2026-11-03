package com.salesmanager.test.shop.integration.payment;

import static org.junit.Assert.assertEquals;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PaymentApiIntegrationTest extends ServicesTestSupport {

	private static final String CATALOGUE_USER = "catalogue.payment@test.com";
	private static final String CATALOGUE_PASSWORD = "Password1";

	@Inject
	private TestRestTemplate testRestTemplate;

	@Test
	public void adminCanAccessPaymentModules() {
		HttpEntity<String> entity = new HttpEntity<>(getHeader());

		ResponseEntity<String> list = testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.GET,
				entity, String.class);
		assertEquals(HttpStatus.OK, list.getStatusCode());

		ResponseEntity<String> module = testRestTemplate.exchange("/api/v1/private/modules/payment/moneyorder",
				HttpMethod.GET, entity, String.class);
		assertEquals(HttpStatus.OK, module.getStatusCode());
	}

	@Test
	public void catalogueUserCannotAccessPaymentModules() throws Exception {
		createCatalogueUser();
		HttpHeaders headers = getHeader(CATALOGUE_USER, CATALOGUE_PASSWORD);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<String> list = testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.GET,
				entity, String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, list.getStatusCode());

		ResponseEntity<String> module = testRestTemplate.exchange("/api/v1/private/modules/payment/moneyorder",
				HttpMethod.GET, entity, String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, module.getStatusCode());

		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		ResponseEntity<String> configure = testRestTemplate.exchange("/api/v1/private/modules/payment",
				HttpMethod.POST, new HttpEntity<>(configuration, headers), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, configure.getStatusCode());
	}

	private void createCatalogueUser() throws Exception {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(CATALOGUE_USER);
		user.setFirstName("Catalogue");
		user.setLastName("User");
		user.setUserName(CATALOGUE_USER);
		user.setPassword(CATALOGUE_PASSWORD);
		user.setRepeatPassword(CATALOGUE_PASSWORD);

		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN_CATALOGUE");
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		if (response.getStatusCode() != HttpStatus.OK) {
			throw new Exception(response.toString());
		}
	}
}
