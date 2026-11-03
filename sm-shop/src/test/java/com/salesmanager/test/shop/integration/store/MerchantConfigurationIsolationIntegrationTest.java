package com.salesmanager.test.shop.integration.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.modules.email.EmailConfig;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.MerchantConfigEntity;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableBrand;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Exercises merchant configuration tenant isolation and per store mail
 * settings through the real Spring context, REST API and H2 database.
 */
public class MerchantConfigurationIsolationIntegrationTest extends ServicesTestSupport {

	private static final String TWITTER = "twitter_handle";
	private static final String ANALYTICS = "google_analytics_url";

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Autowired
	private MerchantConfigurationService merchantConfigurationService;

	@Autowired
	private EmailService emailService;

	@Autowired
	private JavaMailSenderImpl mailSender;

	private String createStore() {
		String code = "ISO" + ThreadLocalRandom.current().nextInt(100000, 999999);
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 isolation street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code.toLowerCase() + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return code;
	}

	private ResponseEntity<String> postMarketing(String storeCode, Long id, String key, String value) {
		MerchantConfigEntity entity = new MerchantConfigEntity();
		entity.setId(id);
		entity.setKey(key);
		entity.setValue(value);
		entity.setActive(true);
		PersistableBrand brand = new PersistableBrand();
		brand.getSocialNetworks().add(entity);
		return testRestTemplate.exchange("/api/v1/private/store/" + storeCode + "/marketing", HttpMethod.POST,
				new HttpEntity<>(brand, getHeader()), String.class);
	}

	private MerchantConfigEntity social(String storeCode, String key) {
		ResponseEntity<ReadableBrand> response = testRestTemplate.exchange(
				"/api/v1/private/store/" + storeCode + "/marketing", HttpMethod.GET, new HttpEntity<>(getHeader()),
				ReadableBrand.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return response.getBody().getSocialNetworks().stream().filter(c -> key.equals(c.getKey())).findFirst()
				.orElse(null);
	}

	private MerchantStore store(String code) throws Exception {
		return merchantStoreService.getByCode(code);
	}

	@Test
	public void foreignConfigurationIdInMarketingBodyDoesNotTouchOtherStore() throws Exception {
		String storeA = createStore();
		String storeB = createStore();
		assertEquals(HttpStatus.CREATED, postMarketing(storeA, null, TWITTER, "@storeA").getStatusCode());
		MerchantConfigEntity rowA = social(storeA, TWITTER);
		assertNotNull(rowA);

		assertEquals(HttpStatus.CREATED, postMarketing(storeB, rowA.getId(), TWITTER, "@storeB").getStatusCode());

		MerchantConfigEntity afterA = social(storeA, TWITTER);
		MerchantConfigEntity afterB = social(storeB, TWITTER);
		assertNotNull(afterA, "store A row must still exist");
		assertEquals("@storeA", afterA.getValue());
		assertEquals(rowA.getId(), afterA.getId());
		assertNotNull(afterB);
		assertEquals("@storeB", afterB.getValue());
		assertNotEquals(rowA.getId(), afterB.getId());
	}

	@Test
	public void reservedConfigurationKeyIsRejectedAndNotPersisted() throws Exception {
		String storeA = createStore();

		ResponseEntity<String> response = postMarketing(storeA, null, Constants.EMAIL_CONFIG,
				"{\"protocol\":\"smtp\",\"host\":\"evil.example\",\"port\":\"25\"}");

		assertFalse(response.getStatusCode().is2xxSuccessful(), "reserved key must be refused, got " + response.getStatusCode());
		assertNull(merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG, store(storeA)));
		assertNull(emailService.getEmailConfiguration(store(storeA)));
		assertNull(social(storeA, Constants.EMAIL_CONFIG));
	}

	@Test
	public void socialKeyBackedByNonSocialRowIsNotOverwritten() throws Exception {
		String storeA = createStore();
		MerchantConfiguration config = new MerchantConfiguration();
		config.setKey(ANALYTICS);
		config.setValue("UA-ORIGINAL");
		config.setMerchantStore(store(storeA));
		config.setMerchantConfigurationType(MerchantConfigurationType.CONFIG);
		merchantConfigurationService.saveOrUpdate(config);

		ResponseEntity<String> response = postMarketing(storeA, null, ANALYTICS, "UA-HIJACK");

		assertFalse(response.getStatusCode().is2xxSuccessful());
		MerchantConfiguration stored = merchantConfigurationService.getMerchantConfiguration(ANALYTICS, store(storeA));
		assertEquals("UA-ORIGINAL", stored.getValue());
		assertEquals(MerchantConfigurationType.CONFIG, stored.getMerchantConfigurationType());
	}

	@Test
	public void serviceRefusesToUpdateRowOfAnotherStore() throws Exception {
		String storeA = createStore();
		String storeB = createStore();
		assertEquals(HttpStatus.CREATED, postMarketing(storeA, null, TWITTER, "@storeA").getStatusCode());
		MerchantConfiguration rowA = merchantConfigurationService.getMerchantConfiguration(TWITTER, store(storeA));

		MerchantConfiguration forged = new MerchantConfiguration();
		forged.setId(rowA.getId());
		forged.setKey(TWITTER);
		forged.setValue("@forged");
		forged.setMerchantStore(store(storeB));
		forged.setMerchantConfigurationType(MerchantConfigurationType.SOCIAL);

		assertThrows(ServiceException.class, () -> merchantConfigurationService.saveOrUpdate(forged));
		assertEquals("@storeA", merchantConfigurationService.getMerchantConfiguration(TWITTER, store(storeA)).getValue());
		assertNull(merchantConfigurationService.getMerchantConfiguration(TWITTER, store(storeB)));
	}

	@Test
	public void legacySocialEmailConfigRowIsNotUsedAsMailSettings() throws Exception {
		String storeA = createStore();
		MerchantConfiguration legacy = new MerchantConfiguration();
		legacy.setKey(Constants.EMAIL_CONFIG);
		legacy.setValue("{\"protocol\":\"smtp\",\"host\":\"evil.example\",\"port\":\"25\"}");
		legacy.setMerchantStore(store(storeA));
		legacy.setMerchantConfigurationType(MerchantConfigurationType.SOCIAL);
		merchantConfigurationService.saveOrUpdate(legacy);

		assertNull(emailService.getEmailConfiguration(store(storeA)));
	}

	@Test
	public void storeMailSettingsAreUsedWithoutMutatingSharedSender() throws Exception {
		String storeA = createStore();
		String sharedHost = mailSender.getHost();
		int sharedPort = mailSender.getPort();
		String sharedUser = mailSender.getUsername();

		try (ServerSocket smtp = new ServerSocket(0)) {
			CountDownLatch contacted = new CountDownLatch(1);
			Thread listener = new Thread(() -> {
				try (Socket socket = smtp.accept()) {
					contacted.countDown();
					OutputStream out = socket.getOutputStream();
					out.write("421 test server closing\r\n".getBytes(StandardCharsets.US_ASCII));
					out.flush();
				} catch (Exception ignore) {
					// listener closed
				}
			});
			listener.setDaemon(true);
			listener.start();

			EmailConfig config = new EmailConfig();
			config.setProtocol("smtp");
			config.setHost("127.0.0.1");
			config.setPort(String.valueOf(smtp.getLocalPort()));
			config.setUsername("store-a-user");
			config.setPassword("store-a-pass");
			config.setSmtpAuth(false);
			config.setStarttls(false);
			emailService.saveEmailConfiguration(config, store(storeA));

			MerchantConfiguration saved = merchantConfigurationService.getMerchantConfiguration(Constants.EMAIL_CONFIG,
					store(storeA));
			assertEquals(MerchantConfigurationType.INTEGRATION, saved.getMerchantConfigurationType());
			assertEquals("127.0.0.1", emailService.getEmailConfiguration(store(storeA)).getHost());

			Email email = new Email();
			email.setFrom(storeA);
			email.setFromEmail("store@test.com");
			email.setTo("customer@test.com");
			email.setSubject("isolation");
			email.setTemplateName("email_template_isolation_test.ftl");
			email.setTemplateTokens(new HashMap<>());

			assertThrows(Exception.class, () -> emailService.sendHtmlEmail(store(storeA), email));

			assertTrue(contacted.await(10, TimeUnit.SECONDS), "store SMTP server should have been contacted");
		}

		assertEquals(sharedHost, mailSender.getHost());
		assertEquals(sharedPort, mailSender.getPort());
		assertEquals(sharedUser, mailSender.getUsername());
	}
}
