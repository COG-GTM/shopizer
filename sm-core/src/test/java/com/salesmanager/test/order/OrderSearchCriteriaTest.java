package com.salesmanager.test.order;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderCriteria;
import com.salesmanager.core.model.order.OrderList;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

public class OrderSearchCriteriaTest extends AbstractSalesManagerCoreTestCase {

	private MerchantStore store;
	private Order first;
	private Order last;
	private String match;

	@Before
	public void seedOrders() throws Exception {
		match = "Criteria" + UUID.randomUUID().toString().substring(0, 8);
		store = store("CA" + match);
		MerchantStore other = store("CB" + match);
		first = order(store, match, "Unmatched", match, "other", "first@test.invalid");
		last = order(store, "Unmatched", match, "other", match, "last@test.invalid");
		order(other, match, match, match, match, "first@test.invalid");
		order(other, "Foreign", match + "Foreign", "other", match, "foreign@test.invalid");
	}

	@Test
	public void firstOrLastNameMatchesInBothQueries() throws Exception {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerName(match);
		assertOrders(orderService.getOrders(criteria, store), first, last);
		assertOrders(orderService.listByStore(store, criteria), first, last);
	}

	@Test
	public void billingOrDeliveryTelephoneMatches() throws Exception {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerPhone(match);
		assertOrders(orderService.getOrders(criteria, store), first, last);
	}

	@Test
	public void nameAndPhoneAreCombined() throws Exception {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerName(match);
		criteria.setCustomerPhone(match);
		assertOrders(orderService.getOrders(criteria, store), first, last);
		criteria.setCustomerPhone("AbsentPhone");
		assertOrders(orderService.getOrders(criteria, store));
	}

	@Test
	public void foreignNameWithMatchingPhoneReturnsEmpty() throws Exception {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerName(match + "Foreign");
		criteria.setCustomerPhone(match);
		assertOrders(orderService.getOrders(criteria, store));
		assertOrders(orderService.listByStore(store, criteria));
	}

	@Test
	public void nameAndEmailFiltersAreBothApplied() throws Exception {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerName(match);
		criteria.setEmail("first@test.invalid");
		assertOrders(orderService.getOrders(criteria, store), first);
		criteria.setEmail("absent@test.invalid");
		assertOrders(orderService.getOrders(criteria, store));
	}

	private void assertOrders(OrderList result, Order... expected) {
		assertEquals(expected.length, result.getTotalCount());
		if (expected.length == 0) {
			assertTrue(result.getOrders() == null || result.getOrders().isEmpty());
			return;
		}
		assertEquals(result.getTotalCount(), result.getOrders().size());
		Set<Long> ids = Arrays.stream(expected).map(Order::getId).collect(Collectors.toSet());
		assertEquals(ids, result.getOrders().stream().map(Order::getId).collect(Collectors.toSet()));
		for (Order order : result.getOrders()) {
			assertEquals(store.getId(), order.getMerchant().getId());
		}
	}

	private MerchantStore store(String code) throws Exception {
		MerchantStore value = new MerchantStore();
		value.setCode(code);
		value.setStorename(code);
		value.setCountry(countryService.getByCode("US"));
		value.setZone(zoneService.getByCode("VT"));
		value.setCurrency(currencyService.getByCode("USD"));
		value.setDefaultLanguage(languageService.getByCode("en"));
		value.setLanguages(Arrays.asList(value.getDefaultLanguage()));
		value.setStorephone("8888888888");
		value.setStorecity("City");
		value.setStoreaddress("1 Street");
		value.setStorepostalcode("05401");
		value.setStoreEmailAddress("criteria@test.invalid");
		merchantService.create(value);
		return value;
	}

	private Order order(MerchantStore merchant, String firstName, String lastName,
			String billingPhone, String deliveryPhone, String email) throws Exception {
		Billing billing = new Billing();
		billing.setFirstName(firstName);
		billing.setLastName(lastName);
		billing.setTelephone(billingPhone);
		billing.setCountry(merchant.getCountry());
		billing.setZone(merchant.getZone());
		Delivery delivery = new Delivery();
		delivery.setTelephone(deliveryPhone);
		delivery.setCountry(merchant.getCountry());
		delivery.setZone(merchant.getZone());
		Order value = new Order();
		value.setMerchant(merchant);
		value.setBilling(billing);
		value.setDelivery(delivery);
		value.setCurrency(merchant.getCurrency());
		value.setCurrencyValue(BigDecimal.ONE);
		value.setDatePurchased(new Date());
		value.setLastModified(new Date());
		value.setStatus(OrderStatus.ORDERED);
		value.setPaymentType(PaymentType.MONEYORDER);
		value.setPaymentModuleCode("moneyorder");
		value.setTotal(BigDecimal.TEN);
		value.setCustomerEmailAddress(email);
		orderService.create(value);
		return value;
	}
}
