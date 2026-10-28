package com.salesmanager.test.shop.integration.customer;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * A client supplied customer id must never let a create path overwrite an existing customer row.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerIdOverwriteIntegrationTest extends ServicesTestSupport {

	private PersistableCustomer customer(String email, String password, Long id) {
		PersistableCustomer c = new PersistableCustomer();
		c.setId(id);
		c.setEmailAddress(email);
		c.setPassword(password);
		c.setGender(CustomerGender.M.name());
		c.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("first");
		billing.setLastName("last");
		billing.setAddress("1 main street");
		billing.setCity("Brussels");
		billing.setPostalCode("1000");
		billing.setCountry("BE");
		c.setBilling(billing);
		c.setStoreCode(Constants.DEFAULT_STORE);
		return c;
	}

	private HttpHeaders json() {
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		return h;
	}

	private Long registerVictim(String email, String password) {
		ResponseEntity<AuthenticationResponse> r = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer(email, password, null), json()), AuthenticationResponse.class);
		assertThat(r.getStatusCode(), is(OK));
		assertNotNull(r.getBody().getId());
		return r.getBody().getId();
	}

	private ReadableCustomer adminGet(Long id) {
		ResponseEntity<ReadableCustomer> r = testRestTemplate.exchange("/api/v1/private/customer/" + id,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableCustomer.class);
		assertThat(r.getStatusCode(), is(OK));
		return r.getBody();
	}

	private ResponseEntity<AuthenticationResponse> login(String email, String password) {
		return testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
	}

	private void assertVictimIntact(Long victimId, String email, String password) {
		ReadableCustomer victim = adminGet(victimId);
		assertEquals(victimId, Long.valueOf(victim.getId()));
		assertEquals(email, victim.getEmailAddress());
		ResponseEntity<AuthenticationResponse> l = login(email, password);
		assertThat(l.getStatusCode(), is(OK));
		assertEquals(victimId, l.getBody().getId());
	}

	@Test
	public void anonymousRegisterWithVictimIdCreatesNewCustomer() {
		Long victimId = registerVictim("ow-reg-victim@test.com", "victim123");

		ResponseEntity<AuthenticationResponse> attacker = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer("ow-reg-attacker@test.com", "attacker123", victimId), json()),
				AuthenticationResponse.class);
		assertThat(attacker.getStatusCode(), is(OK));
		assertThat(attacker.getBody().getId(), not(victimId));

		assertVictimIntact(victimId, "ow-reg-victim@test.com", "victim123");
		ResponseEntity<AuthenticationResponse> attackerLogin = login("ow-reg-attacker@test.com", "attacker123");
		assertThat(attackerLogin.getStatusCode(), is(OK));
		assertEquals(attacker.getBody().getId(), attackerLogin.getBody().getId());
	}

	@Test
	public void adminCreateWithExistingIdCreatesNewCustomer() {
		Long victimId = registerVictim("ow-adm-victim@test.com", "victim123");

		ResponseEntity<ReadableCustomer> created = testRestTemplate.postForEntity("/api/v1/private/customer",
				new HttpEntity<>(customer("ow-adm-new@test.com", "newpass123", victimId), getHeader()),
				ReadableCustomer.class);
		assertThat(created.getStatusCode(), is(OK));
		assertThat(created.getBody().getId(), not(victimId));
		assertEquals("ow-adm-new@test.com", created.getBody().getEmailAddress());

		assertVictimIntact(victimId, "ow-adm-victim@test.com", "victim123");
	}

	@Test
	public void anonymousCheckoutWithVictimIdDoesNotOverwriteVictim() {
		Long victimId = registerVictim("ow-co-victim@test.com", "victim123");

		IntegrationModuleConfiguration moneyOrder = new IntegrationModuleConfiguration();
		moneyOrder.setCode("moneyorder");
		moneyOrder.setActive(true);
		moneyOrder.setDefaultSelected(true);
		Map<String, String> keys = new HashMap<>();
		keys.put("address", "1 cheque street");
		moneyOrder.setIntegrationKeys(keys);
		ResponseEntity<Void> configured = testRestTemplate.postForEntity("/api/v1/private/modules/payment",
				new HttpEntity<>(moneyOrder, getHeader()), Void.class);
		assertThat(configured.getStatusCode(), is(OK));

		ReadableShoppingCart cart = sampleCart();
		assertNotNull(cart.getCode());

		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		order.setCustomer(customer("ow-co-attacker@test.com", null, victimId));
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount(cart.getTotal().toPlainString());
		order.setPayment(payment);

		ResponseEntity<ReadableOrderConfirmation> confirmation = testRestTemplate.postForEntity(
				"/api/v1/cart/" + cart.getCode() + "/checkout", new HttpEntity<>(order, json()),
				ReadableOrderConfirmation.class);
		assertThat(confirmation.getStatusCode(), is(OK));
		assertNotNull(confirmation.getBody().getId());

		assertVictimIntact(victimId, "ow-co-victim@test.com", "victim123");
	}

	@Test
	public void adminUpdateByIdStillUpdatesThatCustomer() {
		Long id = registerVictim("ow-upd-admin@test.com", "pass1234");

		PersistableCustomer update = customer("ow-upd-admin@test.com", null, null);
		update.getBilling().setFirstName("Renamed");
		ResponseEntity<PersistableCustomer> r = testRestTemplate.exchange("/api/v1/private/customer/" + id,
				HttpMethod.PUT, new HttpEntity<>(update, getHeader()), PersistableCustomer.class);
		assertThat(r.getStatusCode(), is(OK));
		assertEquals(id, r.getBody().getId());

		ReadableCustomer after = adminGet(id);
		assertEquals("Renamed", after.getBilling().getFirstName());
		assertEquals("ow-upd-admin@test.com", after.getEmailAddress());
	}

	@Test
	public void authenticatedSelfUpdateStillUpdatesOwnProfileAndIgnoresBodyId() {
		Long otherId = registerVictim("ow-self-other@test.com", "other123");
		Long selfId = registerVictim("ow-self@test.com", "self1234");
		String token = login("ow-self@test.com", "self1234").getBody().getToken();

		HttpHeaders auth = json();
		auth.add("Authorization", "Bearer " + token);
		PersistableCustomer update = customer("ow-self@test.com", null, otherId);
		update.getBilling().setFirstName("SelfRenamed");
		ResponseEntity<PersistableCustomer> r = testRestTemplate.exchange("/api/v1/auth/customer/",
				HttpMethod.PATCH, new HttpEntity<>(update, auth), PersistableCustomer.class);
		assertThat(r.getStatusCode(), is(OK));
		assertEquals(selfId, r.getBody().getId());

		assertEquals("SelfRenamed", adminGet(selfId).getBilling().getFirstName());
		assertVictimIntact(otherId, "ow-self-other@test.com", "other123");
	}
}
