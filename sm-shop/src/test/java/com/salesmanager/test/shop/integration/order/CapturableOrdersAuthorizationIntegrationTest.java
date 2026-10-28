package com.salesmanager.test.shop.integration.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.payments.TransactionService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.order.v0.ReadableOrderList;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CapturableOrdersAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String CAPTURABLE = "/api/v1/private/orders/payment/capturable";
	private static final String PASSWORD = "Password1";

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Autowired
	private CountryService countryService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private TransactionService transactionService;

	@Test
	public void adminCanListCapturableOrders() throws Exception {
		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		Date now = new Date();
		Order order = new Order();
		order.setMerchant(store);
		order.setCurrency(store.getCurrency());
		order.setCustomerEmailAddress("capturable-" + System.nanoTime() + "@test.com");
		order.setDatePurchased(now);
		order.setTotal(BigDecimal.ONE);
		order.setStatus(OrderStatus.PROCESSED);
		order.setPaymentType(PaymentType.MONEYORDER);
		order.setPaymentModuleCode("moneyorder");
		Billing billing = new Billing();
		billing.setFirstName("Capturable");
		billing.setLastName("Order");
		billing.setCountry(countryService.getByCode("BE"));
		order.setBilling(billing);
		orderService.create(order);

		Transaction transaction = new Transaction();
		transaction.setOrder(order);
		transaction.setTransactionDate(now);
		transaction.setTransactionType(TransactionType.AUTHORIZE);
		transaction.setPaymentType(PaymentType.MONEYORDER);
		transaction.setAmount(BigDecimal.ONE);
		transactionService.create(transaction);

		ResponseEntity<ReadableOrderList> response = testRestTemplate.exchange(CAPTURABLE, HttpMethod.GET,
				new HttpEntity<>(getHeader()), ReadableOrderList.class);

		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getOrders());
		assertTrue(!response.getBody().getOrders().isEmpty());
	}

	@Test
	public void orderAdminCanListCapturableOrders() {
		HttpHeaders orderAdmin = createStaffHeader("ADMIN_ORDER");
		ResponseEntity<String> response = testRestTemplate.exchange(CAPTURABLE, HttpMethod.GET,
				new HttpEntity<>(orderAdmin), String.class);

		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
	}

	@Test
	public void catalogueStaffCannotListCapturableOrdersOrPaymentTransactions() {
		HttpHeaders catalogueStaff = createStaffHeader("ADMIN_CATALOGUE");
		ResponseEntity<String> capturable = testRestTemplate.exchange(CAPTURABLE, HttpMethod.GET,
				new HttpEntity<>(catalogueStaff), String.class);
		ResponseEntity<String> transactions = testRestTemplate.exchange(
				"/api/v1/private/orders/1/payment/transactions", HttpMethod.GET, new HttpEntity<>(catalogueStaff),
				String.class);

		assertEquals(HttpStatus.UNAUTHORIZED, capturable.getStatusCode());
		assertTrue(capturable.getBody().contains("not authorized"));
		assertEquals(HttpStatus.UNAUTHORIZED, transactions.getStatusCode());
	}

	@Test
	public void missingBearerTokenIsUnauthorized() {
		ResponseEntity<String> missing = testRestTemplate.exchange(CAPTURABLE, HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()), String.class);

		assertEquals(HttpStatus.UNAUTHORIZED, missing.getStatusCode());
	}

	@Test
	public void explicitDateRangeIsAccepted() {
		ResponseEntity<String> response = testRestTemplate.exchange(
				CAPTURABLE + "?startDate=2020-01-01&endDate=2030-01-01", HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);

		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
	}

	private HttpHeaders createStaffHeader(String groupName) {
		String username = "cap-order-" + System.nanoTime() + "@test.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Capturable");
		user.setLastName("Staff");
		user.setUserName(username);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		return getHeader(username, PASSWORD);
	}
}
