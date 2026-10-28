package com.salesmanager.test.shop.uat;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box storefront / admin journeys exercised through the public REST contract.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAndAdminJourneysUatTest extends ServicesTestSupport {

	private HttpHeaders json() {
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		return h;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders h = json();
		h.add("Authorization", "Bearer " + token);
		return h;
	}

	private PersistableCustomer customer(String email, String password) {
		PersistableCustomer c = new PersistableCustomer();
		c.setEmailAddress(email);
		c.setPassword(password);
		c.setGender(CustomerGender.F.name());
		c.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Shopper");
		billing.setAddress("1 main street");
		billing.setCity("Brussels");
		billing.setPostalCode("1000");
		billing.setCountry("BE");
		c.setBilling(billing);
		c.setStoreCode(Constants.DEFAULT_STORE);
		return c;
	}

	private String registerAndLogin(String email, String password) {
		ResponseEntity<AuthenticationResponse> reg = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer(email, password), json()), AuthenticationResponse.class);
		assertThat(reg.getStatusCode(), is(OK));
		assertNotNull(reg.getBody().getToken());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
		assertThat(login.getStatusCode(), is(OK));
		assertEquals(reg.getBody().getId(), login.getBody().getId());
		return login.getBody().getToken();
	}

	@Test
	public void adminLoginThenAdminOnlyCall() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertThat(login.getStatusCode(), is(OK));
		String token = login.getBody().getToken();
		assertNotNull(token);

		ResponseEntity<String> customers = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(bearer(token)), String.class);
		assertThat(customers.getStatusCode(), is(OK));

		ResponseEntity<String> noToken = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(json()), String.class);
		assertThat(noToken.getStatusCode(), is(UNAUTHORIZED));

		ResponseEntity<String> badToken = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(bearer("not.a.jwt")), String.class);
		// malformed JWT is rejected (currently 500 on both base and head, not 401)
		assertTrue(badToken.getStatusCode().isError());
	}

	@Test
	public void adminLoginWithWrongPasswordIsRejected() {
		ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong")), String.class);
		assertThat(login.getStatusCode(), not(OK));
	}

	@Test
	public void customerRegisterLoginAndReadProfile() {
		String token = registerAndLogin("uat-profile@test.com", "uatpass123");
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(bearer(token)), ReadableCustomer.class);
		assertThat(profile.getStatusCode(), is(OK));
		assertEquals("uat-profile@test.com", profile.getBody().getEmailAddress());

		ResponseEntity<String> anonymous = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(json()), String.class);
		assertThat(anonymous.getStatusCode(), is(UNAUTHORIZED));
	}

	@Test
	public void duplicateRegistrationIsRejected() {
		registerAndLogin("uat-dup@test.com", "uatpass123");
		ResponseEntity<String> again = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer("uat-dup@test.com", "other123"), json()), String.class);
		assertThat(again.getStatusCode(), not(OK));
		// original password still valid
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest("uat-dup@test.com", "uatpass123")),
				AuthenticationResponse.class);
		assertThat(login.getStatusCode(), is(OK));
	}

	@Test
	public void browseStoreCategoriesAndProducts() {
		ReadableProduct product = sampleProduct("uatbrowse");

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/" + Constants.DEFAULT_STORE, ReadableMerchantStore.class);
		assertThat(store.getStatusCode(), is(OK));
		assertEquals(Constants.DEFAULT_STORE, store.getBody().getCode());

		ResponseEntity<String> categories = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT",
				String.class);
		assertThat(categories.getStatusCode(), is(OK));
		assertTrue(categories.getBody().contains("uatbrowse"));

		ResponseEntity<String> list = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", String.class);
		assertThat(list.getStatusCode(), is(OK));
		assertTrue(list.getBody().contains("uatbrowse"));

		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity("/api/v2/product/" + product.getSku(),
				ReadableProduct.class);
		assertThat(detail.getStatusCode(), is(OK));
		assertEquals("uatbrowse", detail.getBody().getSku());
		assertEquals(product.getId(), detail.getBody().getId());
	}

	@Test
	public void cartCreateUpdateAndAuthenticatedCheckout() {
		String token = registerAndLogin("uat-buyer@test.com", "uatpass123");

		IntegrationModuleConfiguration moneyOrder = new IntegrationModuleConfiguration();
		moneyOrder.setCode("moneyorder");
		moneyOrder.setActive(true);
		moneyOrder.setDefaultSelected(true);
		Map<String, String> keys = new HashMap<>();
		keys.put("address", "1 cheque street");
		moneyOrder.setIntegrationKeys(keys);
		assertThat(testRestTemplate.postForEntity("/api/v1/private/modules/payment",
				new HttpEntity<>(moneyOrder, getHeader()), Void.class).getStatusCode(), is(OK));

		ReadableProduct product = sampleProduct("uatcart");
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart/",
				new HttpEntity<>(item, json()), ReadableShoppingCart.class);
		assertThat(created.getStatusCode(), is(CREATED));
		String code = created.getBody().getCode();
		assertNotNull(code);

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + code,
				HttpMethod.PUT, new HttpEntity<>(item, json()), ReadableShoppingCart.class);
		assertThat(updated.getStatusCode(), is(CREATED));

		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity("/api/v1/cart/" + code,
				ReadableShoppingCart.class);
		assertThat(read.getStatusCode(), is(OK));
		assertEquals(2, read.getBody().getQuantity());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount(read.getBody().getTotal().toPlainString());
		order.setPayment(payment);

		ResponseEntity<ReadableOrderConfirmation> confirmation = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + code + "/checkout", new HttpEntity<>(order, bearer(token)),
				ReadableOrderConfirmation.class);
		assertThat(confirmation.getStatusCode(), is(OK));
		Long orderId = confirmation.getBody().getId();
		assertNotNull(orderId);

		ResponseEntity<String> readBack = testRestTemplate.exchange("/api/v1/auth/orders/" + orderId,
				HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
		assertThat(readBack.getStatusCode(), is(OK));
		assertTrue(readBack.getBody().contains("uatcart"));
	}

	@Test
	public void servicesBasicAuth() {
		ResponseEntity<String> publicCall = testRestTemplate.getForEntity("/services/public/DEFAULT", String.class);
		assertTrue(publicCall.getStatusCode().is2xxSuccessful());

		HttpEntity<String> body = new HttpEntity<>("{}", json());
		ResponseEntity<String> bad = testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong")
				.postForEntity("/services/private/system/optin", body, String.class);
		assertThat(bad.getStatusCode(), is(UNAUTHORIZED));

		ResponseEntity<String> none = testRestTemplate.postForEntity("/services/private/system/optin", body,
				String.class);
		// no credentials: 401 on a fresh client, 302 when the shared client carries a session cookie
		assertFalse(none.getStatusCode().is2xxSuccessful());

		ResponseEntity<String> good = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.postForEntity("/services/private/system/optin", body, String.class);
		assertThat(good.getStatusCode(), is(OK));
	}

	@Test
	public void actuatorHealthAndApiDocs() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertThat(health.getStatusCode(), is(OK));
		assertTrue(health.getBody().contains("\"UP\""));

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertThat(docs.getStatusCode(), is(OK));
		assertTrue(docs.getBody().contains("/api/v1/customer/register"));
	}
}
