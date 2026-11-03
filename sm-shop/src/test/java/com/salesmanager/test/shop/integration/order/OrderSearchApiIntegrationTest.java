package com.salesmanager.test.shop.integration.order;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.model.order.v0.ReadableOrderList;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import org.springframework.security.crypto.password.PasswordEncoder;

@RunWith(SpringRunner.class)
public class OrderSearchApiIntegrationTest extends ServicesTestSupport {

	@Inject private MerchantStoreService merchantService;
	@Inject private OrderService orderService;
	@Inject private UserService userService;
	@Inject private GroupService groupService;
	@Inject private PasswordEncoder passwordEncoder;

	private String match;
	private MerchantStore other;
	private Order expected;
	private HttpHeaders headers;

	@Before
	public void seedOrdersAndAdmin() throws Exception {
		match = "Api" + UUID.randomUUID().toString().substring(0, 8);
		MerchantStore store = merchantService.getByCode("DEFAULT");
		other = new MerchantStore();
		other.setCode("OS" + match);
		other.setStorename("Order search " + match);
		other.setCountry(store.getCountry());
		other.setZone(store.getZone());
		other.setCurrency(store.getCurrency());
		other.setDefaultLanguage(store.getDefaultLanguage());
		other.setLanguages(Arrays.asList(store.getDefaultLanguage()));
		other.setStorephone("8888888888");
		other.setStorecity("City");
		other.setStoreaddress("1 Street");
		other.setStorepostalcode("05401");
		other.setStoreEmailAddress("search@test.invalid");
		merchantService.create(other);
		expected = createOrder(store, match);
		createOrder(other, match);
		User admin = new User(match + "@test.invalid", passwordEncoder.encode("Password1"), match + "@test.invalid");
		admin.setMerchantStore(store);
		admin.setDefaultLanguage(store.getDefaultLanguage());
		admin.setFirstName("Order");
		admin.setLastName("Admin");
		admin.setGroups(Arrays.asList(groupService.findByName("ADMIN")));
		userService.create(admin);
		headers = getHeader(admin.getAdminName(), "Password1");
	}

	@Test
	public void nameSearchReturnsOnlyDefaultOrders() {
		assertSearch("name=" + match);
	}

	@Test
	public void phoneSearchReturnsOnlyDefaultOrders() {
		assertSearch("phone=" + match);
	}

	@Test
	public void defaultAdminCannotSearchAnotherStore() {
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/orders?store=" + other.getCode() + "&name=" + match,
				HttpMethod.GET, new HttpEntity<>(headers), String.class);
		assertTrue(response.toString(), response.getStatusCodeValue() == 401 || response.getStatusCodeValue() == 403);
	}

	private void assertSearch(String query) {
		ResponseEntity<ReadableOrderList> response = testRestTemplate.exchange(
				"/api/v1/private/orders?store=DEFAULT&count=10&" + query,
				HttpMethod.GET, new HttpEntity<>(headers), ReadableOrderList.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		ReadableOrderList result = response.getBody();
		assertNotNull(result);
		assertEquals(1, result.getRecordsTotal());
		assertEquals(1, result.getTotalPages());
		assertEquals(1, result.getOrders().size());
		assertEquals(expected.getId(), result.getOrders().get(0).getId());
	}

	private Order createOrder(MerchantStore store, String name) throws Exception {
		Billing billing = new Billing();
		billing.setFirstName("ApiFirst");
		billing.setLastName(name);
		billing.setTelephone("other");
		billing.setCountry(store.getCountry());
		billing.setZone(store.getZone());
		Delivery delivery = new Delivery();
		delivery.setTelephone(name);
		delivery.setCountry(store.getCountry());
		delivery.setZone(store.getZone());
		Order order = new Order();
		order.setMerchant(store);
		order.setBilling(billing);
		order.setDelivery(delivery);
		order.setCurrency(store.getCurrency());
		order.setCurrencyValue(BigDecimal.ONE);
		order.setDatePurchased(new Date());
		order.setLastModified(new Date());
		order.setStatus(OrderStatus.ORDERED);
		order.setPaymentType(PaymentType.MONEYORDER);
		order.setPaymentModuleCode("moneyorder");
		order.setTotal(BigDecimal.TEN);
		order.setCustomerEmailAddress(name + "@test.invalid");
		orderService.create(order);
		return order;
	}
}
