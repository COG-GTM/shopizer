package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.MerchantConfigEntity;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableBrand;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class BrandingJourneyUatTest extends ServicesTestSupport {

	@Inject
	private TestRestTemplate restTemplate;

	@Test
	public void adminCanManageBrandingAndForeignIdsCannotChangeDefault() {
		HttpHeaders adminHeaders = adminHeaders();
		ResponseEntity<ReadableBrand> initialResponse = readMarketing("DEFAULT", adminHeaders);
		assertEquals(OK, initialResponse.getStatusCode());

		PersistableBrand allNetworks = brand(
				network(null, "facebook_page_url", "https://facebook.com/uat"),
				network(null, "twitter_handle", "@uat"),
				network(null, "instagram", "https://instagram.com/uat"),
				network(null, "pinterest", "https://pinterest.com/uat"));
		ResponseEntity<Void> saved = postMarketing("DEFAULT", allNetworks, adminHeaders);
		assertEquals(CREATED, saved.getStatusCode());

		ReadableBrand afterCreate = readMarketing("DEFAULT", adminHeaders).getBody();
		Map<String, MerchantConfigEntity> createdNetworks = byKey(afterCreate);
		assertEquals(4, createdNetworks.size());
		assertEquals("https://facebook.com/uat", createdNetworks.get("facebook_page_url").getValue());
		assertEquals("@uat", createdNetworks.get("twitter_handle").getValue());
		assertEquals("https://instagram.com/uat", createdNetworks.get("instagram").getValue());
		assertEquals("https://pinterest.com/uat", createdNetworks.get("pinterest").getValue());

		ResponseEntity<Void> updated = postMarketing("DEFAULT",
				brand(network(null, "twitter_handle", "@updated"),
						network(null, "pinterest", "")), adminHeaders);
		assertEquals(CREATED, updated.getStatusCode());
		Map<String, MerchantConfigEntity> afterUpdate = byKey(readMarketing("DEFAULT", adminHeaders).getBody());
		assertEquals(3, afterUpdate.size());
		assertEquals("@updated", afterUpdate.get("twitter_handle").getValue());
		assertTrue(!afterUpdate.containsKey("pinterest"));

		String otherStoreCode = createStore();
		Map<String, MerchantConfigEntity> beforeTamper = byKey(readMarketing("DEFAULT", adminHeaders).getBody());
		PersistableBrand tamper = new PersistableBrand();
		for (Map.Entry<String, MerchantConfigEntity> entry : beforeTamper.entrySet()) {
			MerchantConfigEntity copied = network(entry.getValue().getId(), entry.getKey(),
					entry.getValue().getValue() + "-tampered");
			tamper.getSocialNetworks().add(copied);
		}
		ResponseEntity<Void> tampered = postMarketing(otherStoreCode, tamper, adminHeaders);
		assertEquals(CREATED, tampered.getStatusCode());

		Map<String, MerchantConfigEntity> defaultAfterTamper = byKey(readMarketing("DEFAULT", adminHeaders).getBody());
		Map<String, MerchantConfigEntity> otherStoreNetworks = byKey(readMarketing(otherStoreCode, adminHeaders).getBody());
		assertEquals(beforeTamper.size(), defaultAfterTamper.size());
		for (Map.Entry<String, MerchantConfigEntity> entry : beforeTamper.entrySet()) {
			MerchantConfigEntity unchanged = defaultAfterTamper.get(entry.getKey());
			assertNotNull(unchanged);
			assertEquals(entry.getValue().getId(), unchanged.getId());
			assertEquals(entry.getValue().getValue(), unchanged.getValue());

			MerchantConfigEntity other = otherStoreNetworks.get(entry.getKey());
			assertNotNull(other);
			assertNotEquals(entry.getValue().getId(), other.getId());
			assertEquals(entry.getValue().getValue() + "-tampered", other.getValue());
		}
	}

	private HttpHeaders adminHeaders() {
		ResponseEntity<AuthenticationResponse> response = restTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, response.getStatusCode());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(response.getBody().getToken());
		return headers;
	}

	private ResponseEntity<ReadableBrand> readMarketing(String code, HttpHeaders headers) {
		return restTemplate.exchange("/api/v1/private/store/" + code + "/marketing", HttpMethod.GET,
				new HttpEntity<Void>(headers), ReadableBrand.class);
	}

	private ResponseEntity<Void> postMarketing(String code, PersistableBrand brand, HttpHeaders headers) {
		return restTemplate.postForEntity("/api/v1/private/store/" + code + "/marketing",
				new HttpEntity<>(brand, headers), Void.class);
	}

	private Map<String, MerchantConfigEntity> byKey(ReadableBrand brand) {
		Map<String, MerchantConfigEntity> networks = new HashMap<>();
		for (MerchantConfigEntity network : brand.getSocialNetworks()) {
			networks.put(network.getKey(), network);
		}
		return networks;
	}

	private PersistableBrand brand(MerchantConfigEntity... networks) {
		PersistableBrand brand = new PersistableBrand();
		brand.setSocialNetworks(Arrays.asList(networks));
		return brand;
	}

	private MerchantConfigEntity network(Long id, String key, String value) {
		MerchantConfigEntity network = new MerchantConfigEntity();
		network.setId(id);
		network.setKey(key);
		network.setValue(value);
		network.setActive(true);
		return network;
	}

	private String createStore() {
		String code = "uat" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 UAT Street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("UAT");
		address.setStateProvince("CA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code + "@example.test");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<ReadableMerchantStore> response = restTemplate.postForEntity(
				"/api/v1/private/store/", new HttpEntity<>(store, adminHeaders()), ReadableMerchantStore.class);
		assertEquals(OK, response.getStatusCode());
		return code;
	}
}
