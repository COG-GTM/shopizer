package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
public class AdminOrderPaymentJourneyUatTest extends ServicesTestSupport {

	private static final String CAPTURABLE = "/api/v1/private/orders/payment/capturable";
	private static final String PASSWORD = "Password1";

	@Test
	public void adminAndOrderStaffCanListCapturableOrdersButAnonymousAndCatalogueStaffCannot() {
		HttpHeaders admin = getHeader();
		ResponseEntity<String> adminResponse = getCapturable(admin);
		ResponseEntity<String> anonymousResponse = getCapturable(new HttpHeaders());
		HttpHeaders catalogueStaff = createStaffHeader("ADMIN_CATALOGUE");
		ResponseEntity<String> catalogueResponse = getCapturable(catalogueStaff);
		HttpHeaders orderStaff = createStaffHeader("ADMIN_ORDER");
		ResponseEntity<String> orderResponse = getCapturable(orderStaff);

		assertEquals(HttpStatus.ACCEPTED, adminResponse.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, anonymousResponse.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, catalogueResponse.getStatusCode());
		assertTrue(catalogueResponse.getBody().toLowerCase().contains("not authorized"));
		assertEquals(HttpStatus.ACCEPTED, orderResponse.getStatusCode());
	}

	private ResponseEntity<String> getCapturable(HttpHeaders headers) {
		return testRestTemplate.exchange(CAPTURABLE, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private HttpHeaders createStaffHeader(String groupName) {
		String username = "cap-order-uat-" + System.nanoTime() + "@test.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Capturable");
		user.setLastName("UAT");
		user.setUserName(username);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		return getHeader(username, PASSWORD);
	}
}
