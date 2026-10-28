package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v0.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void storefrontCanBrowseMaintainCartAndCheckout() throws Exception {
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT&lang=en", String.class)
				.getStatusCode());

		ReadableProduct sample = sampleProduct("uat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
		ResponseEntity<ReadableProductList> productList =
				testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT&lang=en", ReadableProductList.class);
		assertEquals(OK, productList.getStatusCode());
		ReadableProduct product = productList.getBody().getProducts().stream()
				.filter(item -> sample.getSku().equals(item.getSku())).findFirst().orElse(sample);
		ResponseEntity<ReadableProduct> productDetail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT&lang=en",
				ReadableProduct.class);
		assertEquals(OK, productDetail.getStatusCode());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
				new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
		assertEquals(CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody().getCode());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
		assertEquals(CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(OK, readCart.getStatusCode());

		String email = "uat-checkout-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Checkout");
		billing.setCountry("BE");
		customer.setBilling(billing);
		assertEquals(OK, testRestTemplate.postForEntity("/api/v1/customer/register?store=DEFAULT&lang=en",
				new HttpEntity<>(customer, getHeader()), String.class).getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.set("Authorization", "Bearer " + login.getBody().getToken());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(readCart.getBody().getTotal().toPlainString());
		order.setPayment(payment);
		IntegrationModuleConfiguration moneyOrder = new IntegrationModuleConfiguration();
		moneyOrder.setCode("moneyorder");
		moneyOrder.setActive(true);
		moneyOrder.setDefaultSelected(true);
		moneyOrder.getIntegrationKeys().put("address", "Shopizer UAT");
		moneyOrder.getIntegrationKeys().put("transaction", "AUTHORIZECAPTURE");
		assertEquals(OK, testRestTemplate.postForEntity("/api/v1/private/modules/payment?store=DEFAULT",
				new HttpEntity<>(moneyOrder, getHeader()), Void.class).getStatusCode());
		ResponseEntity<ReadableOrderConfirmation> confirmation = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + createdCart.getBody().getCode() + "/checkout?store=DEFAULT&lang=en",
				new HttpEntity<>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertEquals(OK, confirmation.getStatusCode());
		assertNotNull(confirmation.getBody());
		assertNotNull(confirmation.getBody().getId());
		ResponseEntity<ReadableOrder> orderReadback = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + confirmation.getBody().getId() + "?store=DEFAULT&lang=en", HttpMethod.GET,
				new HttpEntity<>(customerHeaders), ReadableOrder.class);
		assertEquals(OK, orderReadback.getStatusCode());
		assertEquals(confirmation.getBody().getId(), orderReadback.getBody().getId());
	}
}
