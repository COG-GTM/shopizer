package com.salesmanager.test.shop.integration.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Parent store (retailerStore) rules on POST/PUT /api/v1/private/store and the
 * parent-to-child direction of store authorization, through the full Spring context.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class MerchantStoreParentChangeIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void retailerAdminCanOnlyCreateChildStoresUnderOwnStore() {
		HttpHeaders superadmin = getHeader();
		PersistableMerchantStore retail = store("pcretail", null);
		retail.setRetailer(true);
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/", HttpMethod.POST, retail, superadmin).getStatusCode());
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/", HttpMethod.POST, store("pcforeign", null), superadmin).getStatusCode());
		createUser("pcretail", "retail@pcretail.com", "ADMIN_RETAILER", superadmin);

		HttpHeaders retailer = getHeader("retail@pcretail.com", PASSWORD);

		assertEquals(HttpStatus.OK,
				send("/api/v1/private/store/", HttpMethod.POST, store("pcownchild", "pcretail"), retailer).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED,
				send("/api/v1/private/store/", HttpMethod.POST, store("pcforeignchild", "pcforeign"), retailer).getStatusCode());
		assertEquals(HttpStatus.OK,
				send("/api/v1/private/store/", HttpMethod.POST, store("pcorphan", null), retailer).getStatusCode());

		assertEquals("pcretail", parentOf("pcownchild", superadmin));
		// rejected store was not created
		assertEquals(HttpStatus.NOT_FOUND.value() / 100,
				send("/api/v1/store/pcforeignchild", HttpMethod.GET, null, superadmin).getStatusCodeValue() / 100);

		// parent admin reaches the child it created
		assertEquals(HttpStatus.OK, send("/api/v1/private/customers?store=pcownchild", HttpMethod.GET, null, retailer).getStatusCode());
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/pcownchild", HttpMethod.GET, null, retailer).getStatusCode());
		// but not an unrelated store
		assertEquals(HttpStatus.UNAUTHORIZED, send("/api/v1/private/customers?store=pcforeign", HttpMethod.GET, null, retailer).getStatusCode());
	}

	@Test
	public void storeAdminCannotChangeOwnParentButSuperadminCan() {
		HttpHeaders superadmin = getHeader();
		PersistableMerchantStore parent = store("pcparent", null);
		parent.setRetailer(true);
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/", HttpMethod.POST, parent, superadmin).getStatusCode());
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/", HttpMethod.POST, store("pcother", null), superadmin).getStatusCode());
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/", HttpMethod.POST, store("pckid", "pcparent"), superadmin).getStatusCode());
		createUser("pckid", "admin@pckid.com", "ADMIN", superadmin);
		createUser("pcparent", "admin@pcparent.com", "ADMIN", superadmin);
		createUser("pcother", "admin@pcother.com", "ADMIN", superadmin);

		HttpHeaders kid = getHeader("admin@pckid.com", PASSWORD);
		HttpHeaders parentAdmin = getHeader("admin@pcparent.com", PASSWORD);
		HttpHeaders otherAdmin = getHeader("admin@pcother.com", PASSWORD);

		// re-submitting the unchanged parent is accepted
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/pckid", HttpMethod.PUT, store("pckid", "pcparent"), kid).getStatusCode());
		assertEquals("pcparent", parentOf("pckid", superadmin));

		// re-parenting by the store admin is refused and leaves the link untouched
		assertEquals(HttpStatus.UNAUTHORIZED, send("/api/v1/private/store/pckid", HttpMethod.PUT, store("pckid", "pcother"), kid).getStatusCode());
		assertEquals("pcparent", parentOf("pckid", superadmin));

		// the parent admin is not superadmin either: cannot move the child elsewhere
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/pckid", HttpMethod.PUT, store("pckid", "pcparent"), parentAdmin).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, send("/api/v1/private/store/pckid", HttpMethod.PUT, store("pckid", "pcother"), parentAdmin).getStatusCode());

		// unrelated admin cannot update the store at all
		assertEquals(HttpStatus.UNAUTHORIZED, send("/api/v1/private/store/pckid", HttpMethod.PUT, store("pckid", "pcother"), otherAdmin).getStatusCode());

		// direction of access: parent -> child yes, child -> parent no
		assertEquals(HttpStatus.OK, send("/api/v1/private/customers?store=pckid", HttpMethod.GET, null, parentAdmin).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, send("/api/v1/private/customers?store=pcparent", HttpMethod.GET, null, kid).getStatusCode());

		// superadmin may re-parent; access follows the new link
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/pckid", HttpMethod.PUT, store("pckid", "pcother"), superadmin).getStatusCode());
		assertEquals("pcother", parentOf("pckid", superadmin));
		assertEquals(HttpStatus.UNAUTHORIZED, send("/api/v1/private/customers?store=pckid", HttpMethod.GET, null, parentAdmin).getStatusCode());
		assertEquals(HttpStatus.OK, send("/api/v1/private/customers?store=pckid", HttpMethod.GET, null, otherAdmin).getStatusCode());
	}

	private String parentOf(String code, HttpHeaders headers) {
		ResponseEntity<String> response = send("/api/v1/private/store/" + code, HttpMethod.GET, null, headers);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		try {
			JsonNode parent = mapper.readTree(response.getBody()).get("parent");
			assertTrue(parent != null && !parent.isNull(), "store " + code + " has no parent: " + response.getBody());
			return parent.get("code").asText();
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private ResponseEntity<String> send(String url, HttpMethod method, Object body, HttpHeaders headers) {
		return testRestTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
	}

	private void createUser(String storeCode, String userName, String groupName, HttpHeaders superadmin) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(userName);
		user.setFirstName("Store");
		user.setLastName("Admin");
		user.setUserName(userName);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);
		ResponseEntity<String> response = send("/api/v1/private/user/?store=" + storeCode, HttpMethod.POST, user, superadmin);
		assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
	}

	private PersistableMerchantStore store(String code, String retailerStore) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		store.setRetailerStore(retailerStore);
		return store;
	}
}
