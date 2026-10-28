package com.salesmanager.test.shop.integration.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Arrays;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.system.MerchantConfigurationRepository;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.MerchantConfigEntity;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableBrand;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class MerchantConfigurationOwnershipIntegrationTest extends ServicesTestSupport {

	@Inject
	private TestRestTemplate restTemplate;

	@Inject
	private MerchantConfigurationRepository repository;

	@Inject
	private MerchantConfigurationService configurationService;

	@Inject
	private MerchantStoreService storeService;

	@Test
	public void repositoryFindsOwnerAndReturnsNullForUnknownId() throws Exception {
		MerchantStore store = createStore(uniqueCode());
		MerchantConfiguration row = row(store, "it_repository_owner", "value",
				MerchantConfigurationType.INTEGRATION);
		configurationService.saveOrUpdate(row);

		assertEquals(store.getId(), repository.findMerchantStoreIdById(row.getId()));
		assertNull(repository.findMerchantStoreIdById(Long.MAX_VALUE));
	}

	@Test
	public void serviceRejectsRetargetedRowAndLeavesDatabaseUnchanged() throws Exception {
		MerchantStore owner = createStore(uniqueCode());
		MerchantStore other = createStore(uniqueCode());
		MerchantConfiguration row = row(owner, "it_retarget", "original", MerchantConfigurationType.INTEGRATION);
		configurationService.saveOrUpdate(row);

		row.setMerchantStore(other);
		row.setValue("changed");
		try {
			configurationService.saveOrUpdate(row);
		} catch (ServiceException expected) {
			MerchantConfiguration reloaded = repository.findById(row.getId()).orElse(null);
			assertNotNull(reloaded);
			assertEquals("original", reloaded.getValue());
			assertEquals(owner.getId(), reloaded.getMerchantStore().getId());
			return;
		}
		throw new AssertionError("Expected ServiceException");
	}

	@Test
	public void postWithForeignIdCreatesOwnStoreRowAndKeepsOriginal() throws Exception {
		MerchantStore owner = createStore(uniqueCode());
		MerchantStore target = createStore(uniqueCode());
		MerchantConfiguration foreign = row(owner, "it_secret_cfg", "secret",
				MerchantConfigurationType.INTEGRATION);
		configurationService.saveOrUpdate(foreign);

		MerchantConfigEntity requestRow = network(foreign.getId(), "facebook_page_url", "https://facebook.com/b");
		ResponseEntity<Void> posted = postBrand(target.getCode(), brand(requestRow), getHeader());
		assertEquals(CREATED, posted.getStatusCode());

		MerchantConfiguration original = repository
				.findByMerchantStoreAndKey(owner.getId(), "it_secret_cfg");
		assertEquals("secret", original.getValue());
		assertEquals(owner.getId(), original.getMerchantStore().getId());

		MerchantConfiguration targetRow = repository
				.findByMerchantStoreAndKey(target.getId(), "facebook_page_url");
		assertNotNull(targetRow);
		assertNotEquals(foreign.getId(), targetRow.getId());
		assertEquals("https://facebook.com/b", targetRow.getValue());

		ResponseEntity<ReadableBrand> read = restTemplate.exchange(
				"/api/v1/private/store/" + target.getCode() + "/marketing", HttpMethod.GET,
				new HttpEntity<Void>(getHeader()), ReadableBrand.class);
		assertEquals(OK, read.getStatusCode());
		assertTrue(read.getBody().getSocialNetworks().stream()
				.anyMatch(network -> "facebook_page_url".equals(network.getKey())));
	}

	@Test
	public void unsupportedKeyCannotDeleteIntegrationConfiguration() throws Exception {
		MerchantStore store = createStore(uniqueCode());
		MerchantConfiguration protectedRow = row(store, "it_secret_cfg", "secret",
				MerchantConfigurationType.INTEGRATION);
		configurationService.saveOrUpdate(protectedRow);

		ResponseEntity<Void> response = postBrand(store.getCode(),
				brand(network(null, "it_secret_cfg", "")), getHeader());

		assertEquals(400, response.getStatusCodeValue());
		MerchantConfiguration unchanged = repository
				.findByMerchantStoreAndKey(store.getId(), "it_secret_cfg");
		assertNotNull(unchanged);
		assertEquals("secret", unchanged.getValue());
	}

	@Test
	public void invalidLaterConfigurationRejectsWholeRequestWithoutWrites() throws Exception {
		MerchantStore store = createStore(uniqueCode());
		MerchantConfiguration facebook = row(store, "facebook_page_url", "unchanged",
				MerchantConfigurationType.SOCIAL);
		MerchantConfiguration instagram = row(store, "instagram", "protected",
				MerchantConfigurationType.INTEGRATION);
		configurationService.saveOrUpdate(facebook);
		configurationService.saveOrUpdate(instagram);

		PersistableBrand brand = brand(network(null, "facebook_page_url", "changed"),
				network(null, "instagram", "new"));
		ResponseEntity<Void> response = postBrand(store.getCode(), brand, getHeader());

		assertEquals(400, response.getStatusCodeValue());
		assertEquals("unchanged", repository
				.findByMerchantStoreAndKey(store.getId(), "facebook_page_url").getValue());
		assertEquals("protected", repository
				.findByMerchantStoreAndKey(store.getId(), "instagram").getValue());
	}

	@Test
	public void socialConfigurationUpdatesByKeyAndBlankValueDeletesRow() throws Exception {
		MerchantStore store = createStore(uniqueCode());
		HttpHeaders headers = getHeader();
		ResponseEntity<Void> created = postBrand(store.getCode(),
				brand(network(null, "facebook_page_url", "first")), headers);
		assertEquals(CREATED, created.getStatusCode());
		MerchantConfiguration original = repository
				.findByMerchantStoreAndKey(store.getId(), "facebook_page_url");
		assertNotNull(original);

		ResponseEntity<Void> updated = postBrand(store.getCode(),
				brand(network(999L, "facebook_page_url", "updated")), headers);
		assertEquals(CREATED, updated.getStatusCode());
		MerchantConfiguration changed = repository
				.findByMerchantStoreAndKey(store.getId(), "facebook_page_url");
		assertEquals(original.getId(), changed.getId());
		assertEquals("updated", changed.getValue());

		ResponseEntity<Void> deleted = postBrand(store.getCode(),
				brand(network(original.getId(), "facebook_page_url", "")), headers);
		assertEquals(CREATED, deleted.getStatusCode());
		assertNull(repository.findByMerchantStoreAndKey(store.getId(), "facebook_page_url"));
	}

	@Test
	public void marketingPostWithoutTokenIsUnauthorized() throws Exception {
		MerchantStore store = createStore(uniqueCode());
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/private/store/" + store.getCode() + "/marketing",
				new HttpEntity<>(brand(network(null, "facebook_page_url", "value"))), String.class);

		assertTrue(response.getStatusCode().equals(UNAUTHORIZED) || response.getStatusCode().equals(FORBIDDEN));
	}

	private MerchantStore createStore(String code) throws ServiceException {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Integration Street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Integration");
		address.setStateProvince("CA");

		PersistableMerchantStore request = new PersistableMerchantStore();
		request.setCode(code);
		request.setCurrency("CAD");
		request.setDefaultLanguage("en");
		request.setEmail(code + "@example.test");
		request.setName(code);
		request.setPhone("444-555-6666");
		request.setSupportedLanguages(Arrays.asList("en"));
		request.setAddress(address);

		ResponseEntity<ReadableMerchantStore> response = restTemplate.postForEntity(
				"/api/v1/private/store/", new HttpEntity<>(request, getHeader()), ReadableMerchantStore.class);
		assertEquals(OK, response.getStatusCode());
		return storeService.getByCode(code);
	}

	private MerchantConfiguration row(MerchantStore store, String key, String value,
			MerchantConfigurationType type) {
		MerchantConfiguration row = new MerchantConfiguration();
		row.setMerchantStore(store);
		row.setKey(key);
		row.setValue(value);
		row.setActive(true);
		row.setMerchantConfigurationType(type);
		return row;
	}

	private PersistableBrand brand(MerchantConfigEntity... rows) {
		PersistableBrand brand = new PersistableBrand();
		brand.setSocialNetworks(Arrays.asList(rows));
		return brand;
	}

	private MerchantConfigEntity network(Long id, String key, String value) {
		MerchantConfigEntity row = new MerchantConfigEntity();
		row.setId(id);
		row.setKey(key);
		row.setValue(value);
		row.setActive(true);
		return row;
	}

	private ResponseEntity<Void> postBrand(String code, PersistableBrand brand, HttpHeaders headers) {
		return restTemplate.postForEntity("/api/v1/private/store/" + code + "/marketing",
				new HttpEntity<>(brand, headers), Void.class);
	}

	private String uniqueCode() {
		return "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}
}
