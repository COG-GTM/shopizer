package com.salesmanager.test.shop.integration.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.core.model.user.Permission;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.system.IntegrationModuleSummaryEntity;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PaymentApiAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String MODULE = "beanstream";
	private static final String PASSWORD = "Password1";
	private static final String MASKED_VALUE = "********";

	@Inject
	private PaymentService paymentService;

	@Inject
	private MerchantStoreService merchantStoreService;

	@Inject
	private GroupService groupService;

	@Inject
	private PermissionService permissionService;

	@Test
	public void missingAndMalformedBearerTokensCannotAccessPaymentEndpoints() {
		HttpHeaders missing = new HttpHeaders();
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange("/api/v1/private/modules/payment",
				HttpMethod.GET, new HttpEntity<String>(missing), String.class).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange(
				"/api/v1/private/modules/payment/" + MODULE, HttpMethod.GET,
				new HttpEntity<String>(missing), String.class).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, postConfiguration("/api/v1/private/modules/payment",
				configuration(MODULE, keys("unauthorized")), missing).getStatusCode());

		HttpHeaders malformed = bearerHeader("Bearer garbage");
		assertFalse(testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.GET,
				new HttpEntity<String>(malformed), String.class).getStatusCode().is2xxSuccessful());
		assertFalse(testRestTemplate.exchange("/api/v1/private/modules/payment/" + MODULE, HttpMethod.GET,
				new HttpEntity<String>(malformed), String.class).getStatusCode().is2xxSuccessful());
		assertFalse(postConfiguration("/api/v1/private/modules/payment",
				configuration(MODULE, keys("unauthorized")), malformed).getStatusCode().is2xxSuccessful());
	}

	@Test
	public void superadminCanListReadAndConfigurePaymentModules() {
		HttpHeaders admin = getHeader();
		Set<String> codes = moduleCodes(admin);
		assertTrue(codes.contains("moneyorder"));
		assertTrue(codes.contains(MODULE));

		Map<String, String> keys = new HashMap<String, String>();
		keys.put("merchantid", "merchant-123");
		keys.put("username", "gateway-user");
		keys.put("password", "superadmin-password");
		assertEquals(HttpStatus.OK, postConfiguration("/api/v1/private/modules/payment",
				configuration(MODULE, keys), admin).getStatusCode());

		ResponseEntity<IntegrationModuleConfiguration> read = readConfiguration(MODULE, admin, null);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(MASKED_VALUE, read.getBody().getIntegrationKeys().get("merchantid"));
		assertEquals(MASKED_VALUE, read.getBody().getIntegrationKeys().get("password"));
	}

	@Test
	public void defaultStoreAdminCanReadMaskedValuesAndConfigurePayments() throws Exception {
		HttpHeaders superadmin = getHeader();
		Map<String, String> keys = new HashMap<String, String>();
		keys.put("merchantid", "default-admin-merchant");
		keys.put("username", "default-admin-user");
		keys.put("password", "default-admin-password");
		keys.put("address", "Default payee");
		assertEquals(HttpStatus.OK, postConfiguration("/api/v1/private/modules/payment",
				configuration(MODULE, keys), superadmin).getStatusCode());

		HttpHeaders admin = createUser("ADMIN", MerchantStore.DEFAULT_STORE);
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.GET,
				new HttpEntity<String>(admin), String.class).getStatusCode());

		ResponseEntity<IntegrationModuleConfiguration> read = readConfiguration(MODULE, admin, null);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(MASKED_VALUE, read.getBody().getIntegrationKeys().get("password"));
		assertEquals("Default payee", read.getBody().getIntegrationKeys().get("address"));

		assertEquals(HttpStatus.OK, postConfiguration("/api/v1/private/modules/payment", read.getBody(), admin)
				.getStatusCode());
		IntegrationConfiguration stored = paymentService.getPaymentConfiguration(MODULE,
				merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE));
		assertEquals("default-admin-password", stored.getIntegrationKeys().get("password"));
	}

	@Test
	public void retailAndOrderAdminsCannotReadOrWritePaymentConfiguration() throws Exception {
		HttpHeaders superadmin = getHeader();
		Map<String, String> originalKeys = keys("before");
		assertEquals(HttpStatus.OK, postConfiguration("/api/v1/private/modules/payment",
				configuration(MODULE, originalKeys), superadmin).getStatusCode());
		Map<String, String> storedBefore = new HashMap<String, String>(storedKeys(MODULE, MerchantStore.DEFAULT_STORE));

		for (String group : Arrays.asList("ADMIN_RETAIL", "ADMIN_ORDER")) {
			HttpHeaders restricted = createUser(group, MerchantStore.DEFAULT_STORE);
			assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange("/api/v1/private/modules/payment",
					HttpMethod.GET, new HttpEntity<String>(restricted), String.class).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange(
					"/api/v1/private/modules/payment/" + MODULE, HttpMethod.GET,
					new HttpEntity<String>(restricted), String.class).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, postConfiguration("/api/v1/private/modules/payment",
					configuration(MODULE, keys("attack")), restricted).getStatusCode());
			assertEquals(storedBefore, storedKeys(MODULE, MerchantStore.DEFAULT_STORE));
		}
	}

	@Test
	public void adminCannotAccessAnotherStorePaymentConfiguration() throws Exception {
		HttpHeaders superadmin = getHeader();
		String otherStore = "payauth";
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Test Street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("CA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(otherStore);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("store-" + UUID.randomUUID() + "@test.com");
		store.setName(otherStore);
		store.setPhone("555-0100");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<PersistableMerchantStore>(store, superadmin), Void.class).getStatusCode());

		HttpHeaders otherStoreAdmin = createUser("ADMIN", otherStore);
		assertEquals(HttpStatus.OK, testRestTemplate.exchange(
				"/api/v1/private/modules/payment?store=" + otherStore, HttpMethod.GET,
				new HttpEntity<String>(otherStoreAdmin), String.class).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange(
				"/api/v1/private/modules/payment/" + MODULE + "?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<String>(otherStoreAdmin), String.class).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, postConfiguration(
				"/api/v1/private/modules/payment?store=DEFAULT", configuration(MODULE, keys("attack")),
				otherStoreAdmin).getStatusCode());
	}

	@Test
	public void seededPaymentModulesMaskSecretsAndAllowSecretRotation() {
		HttpHeaders admin = getHeader();
		Set<String> codes = moduleCodes(admin);

		if (codes.contains("stripe")) {
			Map<String, String> stripeKeys = new HashMap<String, String>();
			stripeKeys.put("publishableKey", "pk_test_public");
			stripeKeys.put("secretKey", "sk_test_original");
			assertEquals(HttpStatus.OK, postConfiguration("/api/v1/private/modules/payment",
					configuration("stripe", stripeKeys), admin).getStatusCode());
			Map<String, String> returned = readConfiguration("stripe", admin, null).getBody().getIntegrationKeys();
			assertEquals("pk_test_public", returned.get("publishableKey"));
			assertEquals(MASKED_VALUE, returned.get("secretKey"));
		}

		if (codes.contains("braintree")) {
			Map<String, String> braintreeKeys = new HashMap<String, String>();
			braintreeKeys.put("public_key", "public-key");
			braintreeKeys.put("merchant_id", "merchant-id");
			braintreeKeys.put("private_key", "private-key");
			braintreeKeys.put("tokenization_key", "tokenization-key");
			assertEquals(HttpStatus.OK, postConfiguration("/api/v1/private/modules/payment",
					configuration("braintree", braintreeKeys), admin).getStatusCode());
			Map<String, String> returned = readConfiguration("braintree", admin, null).getBody()
					.getIntegrationKeys();
			assertEquals("public-key", returned.get("public_key"));
			assertEquals(MASKED_VALUE, returned.get("merchant_id"));
			assertEquals(MASKED_VALUE, returned.get("private_key"));
		}

		Map<String, String> moneyOrderKeys = new HashMap<String, String>();
		moneyOrderKeys.put("address", "Money Order Payee");
		assertEquals(HttpStatus.OK, postConfiguration("/api/v1/private/modules/payment",
				configuration("moneyorder", moneyOrderKeys), admin).getStatusCode());
		assertEquals("Money Order Payee", readConfiguration("moneyorder", admin, null).getBody()
				.getIntegrationKeys().get("address"));

		assertTrue(codes.contains(MODULE));
		Map<String, String> beanstreamKeys = keys("original");
		assertEquals(HttpStatus.OK, postConfiguration("/api/v1/private/modules/payment",
				configuration(MODULE, beanstreamKeys), admin).getStatusCode());
		Map<String, String> rotatedKeys = new HashMap<String, String>(beanstreamKeys);
		rotatedKeys.put("password", "rotated-password");
		assertEquals(HttpStatus.OK, postConfiguration("/api/v1/private/modules/payment",
				configuration(MODULE, rotatedKeys), admin).getStatusCode());
		assertEquals("rotated-password", storedKeys(MODULE, MerchantStore.DEFAULT_STORE).get("password"));
	}

	private Set<String> moduleCodes(HttpHeaders headers) {
		ResponseEntity<IntegrationModuleSummaryEntity[]> response = testRestTemplate.exchange(
				"/api/v1/private/modules/payment", HttpMethod.GET, new HttpEntity<String>(headers),
				IntegrationModuleSummaryEntity[].class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return Arrays.stream(response.getBody()).map(IntegrationModuleSummaryEntity::getCode)
				.collect(Collectors.toSet());
	}

	private HttpHeaders createUser(String groupName, String storeCode) throws Exception {
		if (groupService.findByName(groupName) == null) {
			Permission authPermission = permissionService.listPermission().stream()
					.filter(permission -> "AUTH".equals(permission.getPermissionName()))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("AUTH permission is unavailable"));
			Group group = new Group(groupName);
			group.setGroupType(GroupType.ADMIN);
			groupService.create(group);
			group.getPermissions().add(authPermission);
			groupService.update(group);
		}

		String email = "payment-" + UUID.randomUUID() + "@test.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Payment");
		user.setLastName("Admin");
		user.setUserName(email);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setStore(storeCode);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
				new HttpEntity<PersistableUser>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		return getHeader(email, PASSWORD);
	}

	private HttpHeaders bearerHeader(String value) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", value);
		return headers;
	}

	private IntegrationModuleConfiguration configuration(String code, Map<String, String> keys) {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode(code);
		configuration.setActive(true);
		configuration.setIntegrationKeys(keys);
		return configuration;
	}

	private ResponseEntity<String> postConfiguration(String path, IntegrationModuleConfiguration configuration,
			HttpHeaders headers) {
		return testRestTemplate.exchange(path, HttpMethod.POST,
				new HttpEntity<IntegrationModuleConfiguration>(configuration, headers), String.class);
	}

	private ResponseEntity<IntegrationModuleConfiguration> readConfiguration(String code, HttpHeaders headers,
			String storeCode) {
		String path = "/api/v1/private/modules/payment/" + code;
		if (storeCode != null) {
			path += "?store=" + storeCode;
		}
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<String>(headers),
				IntegrationModuleConfiguration.class);
	}

	private Map<String, String> keys(String value) {
		Map<String, String> keys = new HashMap<String, String>();
		keys.put("merchantid", "merchant-" + value);
		keys.put("username", "user-" + value);
		keys.put("password", "password-" + value);
		return keys;
	}

	private Map<String, String> storedKeys(String code, String storeCode) {
		try {
			IntegrationConfiguration stored = paymentService.getPaymentConfiguration(code,
					merchantStoreService.getByCode(storeCode));
			assertNotNull(stored);
			return new HashMap<String, String>(stored.getIntegrationKeys());
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}
}
