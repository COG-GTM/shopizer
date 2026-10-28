package com.salesmanager.test.shop.integration.order;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class OrderPaymentApiIntegrationTest extends ServicesTestSupport {

	private static final String CAPTURABLE = "/api/v1/private/orders/payment/capturable?startDate=2020-01-01";
	private static final String CATALOGUE_USER = "catalogue-capturable@test.com";
	private static final String CATALOGUE_PASSWORD = "Password1";

	@Test
	public void capturableOrdersRequireOrderAdminGroup() throws Exception {

		HttpHeaders admin = getHeader();

		ResponseEntity<String> allowed = testRestTemplate.exchange(CAPTURABLE, HttpMethod.GET,
				new HttpEntity<String>(admin), String.class);
		assertEquals(HttpStatus.ACCEPTED, allowed.getStatusCode());

		PersistableUser newUser = new PersistableUser();
		newUser.setDefaultLanguage("en");
		newUser.setEmailAddress(CATALOGUE_USER);
		newUser.setFirstName("Catalogue");
		newUser.setLastName("User");
		newUser.setUserName(CATALOGUE_USER);
		newUser.setPassword(CATALOGUE_PASSWORD);
		newUser.setRepeatPassword(CATALOGUE_PASSWORD);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN_CATALOGUE");
		newUser.getGroups().add(group);
		ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
				new HttpEntity<PersistableUser>(newUser, admin), ReadableUser.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());

		HttpHeaders catalogue = getHeader(CATALOGUE_USER, CATALOGUE_PASSWORD);

		ResponseEntity<String> denied = testRestTemplate.exchange(CAPTURABLE, HttpMethod.GET,
				new HttpEntity<String>(catalogue), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());
	}

}
