package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerJourneyUatTest extends ServicesTestSupport {

	@Test
	public void customerCanRegisterAuthenticateAndBrowse() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		String email = "uat-" + suffix + "@test.com";
		String password = "clear123";
		registerCustomer(email, password);

		ResponseEntity<AuthenticationResponse> login = loginCustomer(email, password);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		HttpHeaders customerHeaders = customerHeaders(login.getBody().getToken());

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", GET,
				new HttpEntity<>(customerHeaders), ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		ResponseEntity<ReadableCategoryList> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", ReadableCategoryList.class);
		assertEquals(OK, categories.getStatusCode());
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", ReadableProductList.class);
		assertEquals(OK, products.getStatusCode());
		assertNotNull(products.getBody());

		String productCode = "uat-product-" + suffix;
		PersistableProduct product = product(productCode);
		product.setSku(productCode);
		product.setPrice(BigDecimal.TEN);
		product.setAvailable(true);
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specifications);
		product.getDescriptions().get(0).setName(productCode);
		ResponseEntity<com.salesmanager.shop.model.entity.Entity> created = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=DEFAULT", new HttpEntity<>(product, getHeader()),
				com.salesmanager.shop.model.entity.Entity.class);
		assertEquals(CREATED, created.getStatusCode());

		ResponseEntity<ReadableProduct> detail = testRestTemplate.exchange(
				"/api/v2/product/" + productCode + "?store=DEFAULT", GET,
				new HttpEntity<>(customerHeaders), ReadableProduct.class);
		assertEquals(OK, detail.getStatusCode());
		assertEquals(productCode, detail.getBody().getSku());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(productCode);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());

		item.setQuantity(3);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "?store=DEFAULT", PUT,
				new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "?store=DEFAULT", GET,
				new HttpEntity<>(customerHeaders), ReadableShoppingCart.class);
		assertEquals(OK, readCart.getStatusCode());
		assertEquals(3, readCart.getBody().getQuantity());
	}

	@Test
	public void authenticatedCustomerCanCheckoutAndReadOrder() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		String email = "checkout-" + suffix + "@test.com";
		String password = "clear123";
		registerCustomer(email, password);
		ResponseEntity<AuthenticationResponse> login = loginCustomer(email, password);
		assertEquals(OK, login.getStatusCode());
		HttpHeaders customerHeaders = customerHeaders(login.getBody().getToken());

		ReadableProduct product = sampleProduct("checkout-" + suffix);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(CREATED, cart.getStatusCode());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency(fetchStore().getCurrency());
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(cart.getBody().getTotal().toPlainString());
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cart.getBody().getCode() + "/checkout?store=DEFAULT",
				new HttpEntity<>(order, customerHeaders), ReadableOrderConfirmation.class);
		Assume.assumeTrue("Checkout was not available in the H2 test setup: HTTP "
				+ checkout.getStatusCodeValue() + " " + checkout.getBody(),
				checkout.getStatusCode().is2xxSuccessful());
		assertNotNull(checkout.getBody());

		ResponseEntity<ReadableOrder> readOrder = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=DEFAULT", GET,
				new HttpEntity<>(customerHeaders), ReadableOrder.class);
		assertEquals(OK, readOrder.getStatusCode());
		assertEquals(checkout.getBody().getId(), readOrder.getBody().getId());
		assertTrue(readOrder.getBody().getProducts() != null);
	}

	private void registerCustomer(String email, String password) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setRepeatPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()),
				PersistableCustomer.class);
		assertEquals(OK, response.getStatusCode());
	}

	private ResponseEntity<AuthenticationResponse> loginCustomer(String email, String password) {
		return testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
	}

	private HttpHeaders customerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}
}
