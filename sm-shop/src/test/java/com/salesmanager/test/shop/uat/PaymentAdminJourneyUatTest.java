package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.system.IntegrationModuleSummaryEntity;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PaymentAdminJourneyUatTest {

	private static final String MASKED_VALUE = "********";
	private static final String ADMIN_PASSWORD = "Password1";

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void adminConfiguresAndResubmitsPaymentModuleSecrets() {
		HttpHeaders admin = login("admin@shopizer.com", "password");
		ResponseEntity<IntegrationModuleSummaryEntity[]> listed = testRestTemplate.exchange(
				"/api/v1/private/modules/payment", HttpMethod.GET, new HttpEntity<String>(admin),
				IntegrationModuleSummaryEntity[].class);
		assertEquals(HttpStatus.OK, listed.getStatusCode());
		assertNotNull(listed.getBody());

		Map<String, String> moneyOrderKeys = new HashMap<String, String>();
		moneyOrderKeys.put("address", "UAT Money Order Payee");
		assertEquals(HttpStatus.OK, configure("moneyorder", moneyOrderKeys, admin).getStatusCode());
		assertEquals("UAT Money Order Payee", read("moneyorder", admin).getBody().getIntegrationKeys().get("address"));

		Map<String, String> gatewayKeys = new HashMap<String, String>();
		gatewayKeys.put("publishableKey", "pk_uat_public");
		gatewayKeys.put("secretKey", "sk_uat_secret");
		assertEquals(HttpStatus.OK, configure("stripe", gatewayKeys, admin).getStatusCode());
		IntegrationModuleConfiguration firstRead = read("stripe", admin).getBody();
		assertEquals(MASKED_VALUE, firstRead.getIntegrationKeys().get("secretKey"));
		assertEquals("pk_uat_public", firstRead.getIntegrationKeys().get("publishableKey"));

		assertEquals(HttpStatus.OK, configure("stripe", firstRead.getIntegrationKeys(), admin).getStatusCode());
		IntegrationModuleConfiguration secondRead = read("stripe", admin).getBody();
		assertEquals(MASKED_VALUE, secondRead.getIntegrationKeys().get("secretKey"));
		assertEquals("pk_uat_public", secondRead.getIntegrationKeys().get("publishableKey"));
		assertEquals("UAT Money Order Payee", read("moneyorder", admin).getBody().getIntegrationKeys().get("address"));

		String email = "catalogue-uat-" + UUID.randomUUID() + "@test.com";
		PersistableUser catalogueUser = new PersistableUser();
		catalogueUser.setDefaultLanguage("en");
		catalogueUser.setEmailAddress(email);
		catalogueUser.setFirstName("Catalogue");
		catalogueUser.setLastName("UAT");
		catalogueUser.setUserName(email);
		catalogueUser.setPassword(ADMIN_PASSWORD);
		catalogueUser.setRepeatPassword(ADMIN_PASSWORD);
		catalogueUser.setStore("DEFAULT");
		catalogueUser.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN_CATALOGUE");
		catalogueUser.getGroups().add(group);

		ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/",
				HttpMethod.POST, new HttpEntity<PersistableUser>(catalogueUser, admin), ReadableUser.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		HttpHeaders catalogue = login(email, ADMIN_PASSWORD);
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange(
				"/api/v1/private/modules/payment", HttpMethod.GET,
				new HttpEntity<String>(catalogue), String.class).getStatusCode());
	}

	private HttpHeaders login(String username, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(username, password)),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(response.getBody().getToken());
		return headers;
	}

	private ResponseEntity<Void> configure(String code, Map<String, String> keys, HttpHeaders headers) {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode(code);
		configuration.setActive(true);
		configuration.setIntegrationKeys(new HashMap<String, String>(keys));
		return testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.POST,
				new HttpEntity<IntegrationModuleConfiguration>(configuration, headers), Void.class);
	}

	private ResponseEntity<IntegrationModuleConfiguration> read(String code, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/modules/payment/" + code, HttpMethod.GET,
				new HttpEntity<String>(headers), IntegrationModuleConfiguration.class);
	}
}
