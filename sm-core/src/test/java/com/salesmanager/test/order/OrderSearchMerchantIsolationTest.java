package com.salesmanager.test.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderCriteria;
import com.salesmanager.core.model.order.OrderList;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.reference.zone.Zone;

public class OrderSearchMerchantIsolationTest extends com.salesmanager.test.common.AbstractSalesManagerCoreTestCase {

	private static final String CUSTOMER_LAST_NAME = "Isolationtest";
	private static final String CUSTOMER_PHONE = "5550001111";

	@Test
	public void searchByNameOrPhoneIsScopedToStore() throws ServiceException {

		Currency currency = currencyService.getByCode(USD_CURRENCY_CODE);
		Country country = countryService.getByCode("US");
		Zone zone = zoneService.getByCode("VT");
		Language en = languageService.getByCode(ENGLISH_LANGUAGE_CODE);

		MerchantStore storeA = createStore("ISOSTOREA", currency, country, zone, en);
		MerchantStore storeB = createStore("ISOSTOREB", currency, country, zone, en);

		createOrder(storeA, currency, country, zone, "a@isolation.test");
		createOrder(storeB, currency, country, zone, "b@isolation.test");

		OrderCriteria byName = new OrderCriteria();
		byName.setCustomerName(CUSTOMER_LAST_NAME);
		assertOnlyStoreOrders(storeA, orderService.getOrders(byName, storeA));
		assertOnlyStoreOrders(storeA, orderService.listByStore(storeA, byName));

		OrderCriteria byPhone = new OrderCriteria();
		byPhone.setCustomerPhone(CUSTOMER_PHONE);
		assertOnlyStoreOrders(storeA, orderService.getOrders(byPhone, storeA));
	}

	private void assertOnlyStoreOrders(MerchantStore store, OrderList list) {
		Assert.assertEquals(1, list.getTotalCount());
		Assert.assertNotNull(list.getOrders());
		Assert.assertFalse(list.getOrders().isEmpty());
		for (Order o : list.getOrders()) {
			Assert.assertEquals(store.getId(), o.getMerchant().getId());
		}
	}

	private MerchantStore createStore(String code, Currency currency, Country country, Zone zone, Language en)
			throws ServiceException {
		List<Language> languages = new ArrayList<Language>();
		languages.add(en);

		MerchantStore store = new MerchantStore();
		store.setCountry(country);
		store.setZone(zone);
		store.setCurrency(currency);
		store.setDefaultLanguage(en);
		store.setLanguages(languages);
		store.setStorename(code);
		store.setCode(code);
		store.setStorephone("888-888-8888");
		store.setStorecity("City");
		store.setStoreaddress("1 Street");
		store.setStorepostalcode("05401");
		store.setStoreEmailAddress(code.toLowerCase() + "@isolation.test");
		merchantService.create(store);
		return store;
	}

	private void createOrder(MerchantStore store, Currency currency, Country country, Zone zone, String email)
			throws ServiceException {
		Billing billing = new Billing();
		billing.setFirstName("Jane");
		billing.setLastName(CUSTOMER_LAST_NAME);
		billing.setTelephone(CUSTOMER_PHONE);
		billing.setAddress("1 Billing street");
		billing.setCity("City");
		billing.setPostalCode("05401");
		billing.setCountry(country);
		billing.setZone(zone);

		Delivery delivery = new Delivery();
		delivery.setAddress("1 Shipping street");
		delivery.setTelephone(CUSTOMER_PHONE);
		delivery.setCountry(country);
		delivery.setZone(zone);

		Order order = new Order();
		order.setMerchant(store);
		order.setCurrency(currency);
		order.setDatePurchased(new Date());
		order.setLastModified(new Date());
		order.setCurrencyValue(new BigDecimal(1));
		order.setStatus(OrderStatus.ORDERED);
		order.setPaymentType(PaymentType.CREDITCARD);
		order.setPaymentModuleCode("payment");
		order.setTotal(new BigDecimal("10.00"));
		order.setCustomerEmailAddress(email);
		order.setBilling(billing);
		order.setDelivery(delivery);
		orderService.create(order);
	}
}
