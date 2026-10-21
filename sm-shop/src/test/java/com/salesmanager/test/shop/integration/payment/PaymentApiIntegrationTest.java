package com.salesmanager.test.shop.integration.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.HashMap;
import java.util.Map;

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

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PaymentApiIntegrationTest extends ServicesTestSupport {

	private static final String MODULE = "beanstream";
	private static final String SECRET = "gateway-secret-password";
	private static final String CATALOGUE_USER = "catalogue-payment@test.com";
	private static final String CATALOGUE_PASSWORD = "Password1";

	@Inject
	private TestRestTemplate testRestTemplate;

	@Inject
	private PaymentService paymentService;

	@Inject
	private MerchantStoreService merchantStoreService;

	@Test
	public void paymentConfigurationRequiresAdminAndMasksSecrets() throws Exception {

		HttpHeaders admin = getHeader();

		Map<String, String> keys = new HashMap<String, String>();
		keys.put("merchantid", "merchant-123");
		keys.put("username", "gateway-user");
		keys.put("password", SECRET);

		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode(MODULE);
		configuration.setActive(true);
		configuration.setIntegrationKeys(keys);

		ResponseEntity<Void> saved = testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.POST,
				new HttpEntity<IntegrationModuleConfiguration>(configuration, admin), Void.class);
		assertEquals(HttpStatus.OK, saved.getStatusCode());

		ResponseEntity<IntegrationModuleConfiguration> read = testRestTemplate.exchange(
				"/api/v1/private/modules/payment/" + MODULE, HttpMethod.GET, new HttpEntity<String>(admin),
				IntegrationModuleConfiguration.class);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		Map<String, String> returnedKeys = read.getBody().getIntegrationKeys();
		assertNotEquals(SECRET, returnedKeys.get("password"));
		assertNotEquals("merchant-123", returnedKeys.get("merchantid"));
		assertEquals("gateway-user", returnedKeys.get("username"));

		// re-submitting the masked values must not overwrite stored secrets
		IntegrationModuleConfiguration resubmit = read.getBody();
		resubmit.getIntegrationKeys().put("username", "gateway-user-2");
		saved = testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.POST,
				new HttpEntity<IntegrationModuleConfiguration>(resubmit, admin), Void.class);
		assertEquals(HttpStatus.OK, saved.getStatusCode());

		MerchantStore store = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);
		IntegrationConfiguration stored = paymentService.getPaymentConfiguration(MODULE, store);
		assertEquals(SECRET, stored.getIntegrationKeys().get("password"));
		assertEquals("merchant-123", stored.getIntegrationKeys().get("merchantid"));
		assertEquals("gateway-user-2", stored.getIntegrationKeys().get("username"));

		// low privilege admin user cannot read or write payment configuration
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

		ResponseEntity<String> denied = testRestTemplate.exchange("/api/v1/private/modules/payment/" + MODULE,
				HttpMethod.GET, new HttpEntity<String>(catalogue), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());

		denied = testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.GET,
				new HttpEntity<String>(catalogue), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());

		Map<String, String> attackerKeys = new HashMap<String, String>();
		attackerKeys.put("merchantid", "attacker");
		attackerKeys.put("username", "attacker");
		attackerKeys.put("password", "attacker");
		IntegrationModuleConfiguration attack = new IntegrationModuleConfiguration();
		attack.setCode(MODULE);
		attack.setActive(true);
		attack.setIntegrationKeys(attackerKeys);
		denied = testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.POST,
				new HttpEntity<IntegrationModuleConfiguration>(attack, catalogue), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());

		stored = paymentService.getPaymentConfiguration(MODULE, store);
		assertEquals(SECRET, stored.getIntegrationKeys().get("password"));
	}

}
