package com.salesmanager.core.business.modules.integration.payment.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.utils.CoreConfiguration;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;

import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;
import com.paypal.sdk.exceptions.ApiException;
import com.paypal.sdk.models.AmountBreakdown;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.AuthorizationWithAdditionalData;
import com.paypal.sdk.models.AuthorizeOrderInput;
import com.paypal.sdk.models.CaptureAuthorizedPaymentInput;
import com.paypal.sdk.models.CaptureOrderInput;
import com.paypal.sdk.models.CaptureRequest;
import com.paypal.sdk.models.CapturedPayment;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.CreateOrderInput;
import com.paypal.sdk.models.ItemRequest;
import com.paypal.sdk.models.LinkDescription;
import com.paypal.sdk.models.Money;
import com.paypal.sdk.models.OrderApplicationContext;
import com.paypal.sdk.models.OrderAuthorizeResponse;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.OrdersCapture;
import com.paypal.sdk.models.PaymentCollection;
import com.paypal.sdk.models.PurchaseUnit;
import com.paypal.sdk.models.PurchaseUnitRequest;
import com.paypal.sdk.models.Refund;
import com.paypal.sdk.models.RefundCapturedPaymentInput;
import com.paypal.sdk.models.RefundRequest;

/**
 * PayPal checkout based on the PayPal Orders v2 REST API (paypal-server-sdk).
 * Replaces the retired NVP/SOAP merchantsdk (SetExpressCheckout / DoExpressCheckoutPayment).
 *
 * Integration keys: clientId, clientSecret (REST app credentials) and transaction (AUTHORIZE or AUTHORIZECAPTURE).
 * The payment token is the PayPal order id returned by {@link #initPaypalTransaction}.
 */
public class PayPalExpressCheckoutPayment implements PaymentModule {

	private static final Logger LOGGER = LoggerFactory.getLogger(PayPalExpressCheckoutPayment.class);

	public static final String CLIENT_ID = "clientId";
	public static final String CLIENT_SECRET = "clientSecret";
	private static final String CONTENT_TYPE = "application/json";
	private static final String APPROVE_LINK = "approve";
	private static final String PAYER_ACTION_LINK = "payer-action";

	@Inject
	private PricingService pricingService;

	@Inject
	private CoreConfiguration coreConfiguration;

	@Override
	public void validateModuleConfiguration(
			IntegrationConfiguration integrationConfiguration,
			MerchantStore store) throws IntegrationException {

		List<String> errorFields = new ArrayList<String>();

		Map<String,String> keys = integrationConfiguration.getIntegrationKeys();
		if(keys==null || StringUtils.isBlank(keys.get(CLIENT_ID))) {
			errorFields.add(CLIENT_ID);
		}
		if(keys==null || StringUtils.isBlank(keys.get(CLIENT_SECRET))) {
			errorFields.add(CLIENT_SECRET);
		}

		if(!errorFields.isEmpty()) {
			IntegrationException ex = new IntegrationException(IntegrationException.ERROR_VALIDATION_SAVE);
			ex.setErrorFields(errorFields);
			throw ex;
		}
	}

	@Override
	public Transaction initTransaction(MerchantStore store, Customer customer,
			BigDecimal amount, Payment payment,
			IntegrationConfiguration configuration, IntegrationModule module)
			throws IntegrationException {

			throw new IntegrationException("Not imlemented");
	}

	@Override
	public Transaction authorize(MerchantStore store, Customer customer,
			List<ShoppingCartItem> items, BigDecimal amount, Payment payment,
			IntegrationConfiguration configuration, IntegrationModule module)
			throws IntegrationException {

		com.salesmanager.core.model.payments.PaypalPayment paypalPayment = (com.salesmanager.core.model.payments.PaypalPayment)payment;
		Validate.notNull(paypalPayment.getPaymentToken(), "A paypal payment token is required to process this transaction");

		return processTransaction(store, customer, items, amount, paypalPayment, configuration, module);
	}

	/**
	 * Creates a PayPal order; the buyer must then be redirected to the APPROVAL_URL transaction detail.
	 */
	public Transaction initPaypalTransaction(MerchantStore store,
			List<ShoppingCartItem> items, OrderTotalSummary summary, Payment payment,
			IntegrationConfiguration configuration, IntegrationModule module)
			throws IntegrationException {

		Validate.notNull(configuration, "Configuration must not be null");
		Validate.notNull(payment, "Payment must not be null");
		Validate.notNull(summary, "OrderTotalSummary must not be null");

		try {

			String currency = store.getCurrency().getCode();

			CheckoutPaymentIntent intent = CheckoutPaymentIntent.AUTHORIZE;
			if(TransactionType.AUTHORIZECAPTURE.name().equalsIgnoreCase(configuration.getIntegrationKeys().get("transaction"))) {
				intent = CheckoutPaymentIntent.CAPTURE;
			}

			List<ItemRequest> lineItems = new ArrayList<ItemRequest>();
			for(ShoppingCartItem cartItem : items) {
				lineItems.add(new ItemRequest.Builder()
						.name(cartItem.getProduct().getProductDescription().getName())
						.quantity(String.valueOf(cartItem.getQuantity()))
						.unitAmount(money(payment.getCurrency().getCode(), cartItem.getFinalPrice().getFinalPrice(), store))
						.build());
			}

			AmountBreakdown.Builder breakdown = new AmountBreakdown.Builder()
					.itemTotal(money(currency, summary.getSubTotal(), store));

			BigDecimal tax = null;
			for(OrderTotal total : summary.getTotals()) {
				if(total.getModule().equals(Constants.OT_SHIPPING_MODULE_CODE)) {
					breakdown.shipping(money(currency, total.getValue(), store));
				}
				if(total.getModule().equals(Constants.OT_HANDLING_MODULE_CODE)) {
					breakdown.handling(money(currency, total.getValue(), store));
				}
				if(total.getModule().equals(Constants.OT_TAX_MODULE_CODE)) {
					tax = (tax == null ? BigDecimal.ZERO : tax).add(total.getValue());
				}
			}
			if(tax!=null) {
				breakdown.taxTotal(money(currency, tax, store));
			}

			AmountWithBreakdown orderTotal = new AmountWithBreakdown.Builder()
					.currencyCode(currency)
					.value(pricingService.getStringAmount(summary.getTotal(), store))
					.breakdown(breakdown.build())
					.build();

			String baseUrl = returnBaseUrl(store);
			String checkoutUrl = baseUrl + Constants.SHOP_URI + "/paypal/checkout" + coreConfiguration.getProperty("URL_EXTENSION", ".html");

			OrderRequest orderRequest = new OrderRequest.Builder()
					.intent(intent)
					.purchaseUnits(List.of(new PurchaseUnitRequest.Builder()
							.amount(orderTotal)
							.softDescriptor("Shopizer_Cart_AP")
							.items(lineItems)
							.build()))
					.applicationContext(new OrderApplicationContext.Builder()
							.returnUrl(checkoutUrl + "/success")
							.cancelUrl(checkoutUrl + "/cancel")
							.build())
					.build();

			com.paypal.sdk.models.Order order = client(configuration).getOrdersController()
					.createOrder(new CreateOrderInput.Builder().contentType(CONTENT_TYPE).body(orderRequest).build())
					.getResult();

			Transaction transaction = new Transaction();
			transaction.setAmount(summary.getTotal());
			transaction.setTransactionDate(new Date());
			transaction.setTransactionType(TransactionType.INIT);
			transaction.setPaymentType(PaymentType.PAYPAL);
			transaction.getTransactionDetails().put("TOKEN", order.getId());
			approvalUrl(order.getLinks()).ifPresent(url -> transaction.getTransactionDetails().put("APPROVAL_URL", url));

			return transaction;

		} catch(Exception e) {
			throw toIntegrationException(e);
		}
	}

	@Override
	public Transaction authorizeAndCapture(MerchantStore store,
			Customer customer, List<ShoppingCartItem> items, BigDecimal amount, Payment payment,
			IntegrationConfiguration configuration, IntegrationModule module)
			throws IntegrationException {

		com.salesmanager.core.model.payments.PaypalPayment paypalPayment = (com.salesmanager.core.model.payments.PaypalPayment)payment;
		Validate.notNull(paypalPayment.getPaymentToken(), "A paypal payment token is required to process this transaction");

		return processTransaction(store, customer, items, amount, paypalPayment, configuration, module);
	}

	@Override
	public Transaction refund(boolean partial, MerchantStore store,
			Transaction transaction, Order order, BigDecimal amount,
			IntegrationConfiguration configuration, IntegrationModule module)
			throws IntegrationException {

		try {

			Validate.notNull(transaction,"Transaction cannot be null");
			Validate.notNull(transaction.getTransactionDetails().get("TRANSACTIONID"), "Transaction details must contain a TRANSACTIONID");
			Validate.notNull(order,"Order must not be null");
			Validate.notNull(order.getCurrency(),"Order nust contain Currency object");

			RefundRequest.Builder refundRequest = new RefundRequest.Builder();
			if(partial) {
				refundRequest.amount(money(order.getCurrency().getCode(), amount, store));
			}

			Refund refund = client(configuration).getPaymentsController()
					.refundCapturedPayment(new RefundCapturedPaymentInput.Builder()
							.captureId(transaction.getTransactionDetails().get("TRANSACTIONID"))
							.contentType(CONTENT_TYPE)
							.body(refundRequest.build())
							.build())
					.getResult();

			String status = refund.getStatus() == null ? null : refund.getStatus().toString();
			if("CANCELLED".equals(status) || "FAILED".equals(status)) {
				LOGGER.error("Wrong status from refund transaction " + status);
				throw new IntegrationException(ServiceException.EXCEPTION_TRANSACTION_DECLINED, "Paypal refund status " + status);
			}

			Transaction newTransaction = new Transaction();
			newTransaction.setAmount(amount);
			newTransaction.setTransactionDate(new Date());
			newTransaction.setTransactionType(TransactionType.REFUND);
			newTransaction.setPaymentType(PaymentType.PAYPAL);
			newTransaction.getTransactionDetails().put("TRANSACTIONID", refund.getId());

			return newTransaction;

		} catch(Exception e) {
			throw toIntegrationException(e);
		}
	}

	private Transaction processTransaction(MerchantStore store,
			Customer customer, List<ShoppingCartItem> items, BigDecimal amount, Payment payment,
			IntegrationConfiguration configuration, IntegrationModule module)
			throws IntegrationException {

		com.salesmanager.core.model.payments.PaypalPayment paypalPayment = (com.salesmanager.core.model.payments.PaypalPayment)payment;
		String orderId = paypalPayment.getPaymentToken();

		try {

			PaypalServerSdkClient client = client(configuration);
			String transactionId;
			String payerId = null;

			if(TransactionType.AUTHORIZE.name().equals(payment.getTransactionType().name())) {
				OrderAuthorizeResponse authorized = client.getOrdersController()
						.authorizeOrder(new AuthorizeOrderInput.Builder().id(orderId).contentType(CONTENT_TYPE).build())
						.getResult();
				transactionId = firstPayment(authorized.getPurchaseUnits(), PaymentCollection::getAuthorizations)
						.map(AuthorizationWithAdditionalData::getId).orElse(null);
				if(authorized.getPayer() != null) {
					payerId = authorized.getPayer().getPayerId();
				}
			} else {
				com.paypal.sdk.models.Order captured = client.getOrdersController()
						.captureOrder(new CaptureOrderInput.Builder().id(orderId).contentType(CONTENT_TYPE).build())
						.getResult();
				transactionId = firstPayment(captured.getPurchaseUnits(), PaymentCollection::getCaptures)
						.map(OrdersCapture::getId).orElse(null);
				if(captured.getPayer() != null) {
					payerId = captured.getPayer().getPayerId();
				}
			}

			if(transactionId == null) {
				throw new IntegrationException("Paypal order " + orderId + " did not return a payment transaction");
			}

			Transaction transaction = new Transaction();
			transaction.setAmount(amount);
			transaction.setTransactionDate(new Date());
			transaction.setTransactionType(payment.getTransactionType());
			transaction.setPaymentType(PaymentType.PAYPAL);
			transaction.getTransactionDetails().put("TOKEN", orderId);
			transaction.getTransactionDetails().put("PAYERID", payerId);
			transaction.getTransactionDetails().put("TRANSACTIONID", transactionId);

			return transaction;

		} catch(Exception e) {
			throw toIntegrationException(e);
		}
	}

	@Override
	public Transaction capture(MerchantStore store, Customer customer,
			Order order, Transaction capturableTransaction,
			IntegrationConfiguration configuration, IntegrationModule module)
			throws IntegrationException {

		try {

			Validate.notNull(capturableTransaction,"Transaction cannot be null");
			Validate.notNull(capturableTransaction.getTransactionDetails().get("TRANSACTIONID"), "Transaction details must contain a TRANSACTIONID");
			Validate.notNull(order,"Order must not be null");
			Validate.notNull(order.getCurrency(),"Order nust contain Currency object");

			String authorizationId = capturableTransaction.getTransactionDetails().get("TRANSACTIONID");

			CapturedPayment captured = client(configuration).getPaymentsController()
					.captureAuthorizedPayment(new CaptureAuthorizedPaymentInput.Builder()
							.authorizationId(authorizationId)
							.contentType(CONTENT_TYPE)
							.body(new CaptureRequest.Builder()
									.amount(money(order.getCurrency().getCode(), order.getTotal(), store))
									.finalCapture(true)
									.build())
							.build())
					.getResult();

			Transaction newTransaction = new Transaction();
			newTransaction.setAmount(order.getTotal());
			newTransaction.setTransactionDate(new Date());
			newTransaction.setTransactionType(TransactionType.CAPTURE);
			newTransaction.setPaymentType(PaymentType.PAYPAL);
			newTransaction.getTransactionDetails().put("AUTHORIZATIONID", authorizationId);
			// capture id, required to refund the payment
			newTransaction.getTransactionDetails().put("TRANSACTIONID", captured.getId());

			return newTransaction;

		} catch(Exception e) {
			throw toIntegrationException(e);
		}
	}

	PaypalServerSdkClient client(IntegrationConfiguration configuration) {
		Map<String,String> keys = configuration.getIntegrationKeys();
		Environment environment = Constants.PRODUCTION_ENVIRONMENT.equals(configuration.getEnvironment())
				? Environment.PRODUCTION : Environment.SANDBOX;
		return new PaypalServerSdkClient.Builder()
				.environment(environment)
				.clientCredentialsAuth(new ClientCredentialsAuthModel.Builder(keys.get(CLIENT_ID), keys.get(CLIENT_SECRET)).build())
				.build();
	}

	private Money money(String currency, BigDecimal value, MerchantStore store) throws ServiceException {
		return new Money.Builder().currencyCode(currency).value(pricingService.getStringAmount(value, store)).build();
	}

	private String returnBaseUrl(MerchantStore store) {
		String baseScheme = store.getDomainName();
		String scheme = coreConfiguration.getProperty("SHOP_SCHEME");
		if(!StringUtils.isBlank(scheme)) {
			baseScheme = coreConfiguration.getProperty("SHOP_SCHEME", "http") + "://" + store.getDomainName();
		}
		StringBuilder url = new StringBuilder(StringUtils.defaultString(baseScheme));
		if(!StringUtils.isBlank(baseScheme) && !baseScheme.endsWith(Constants.SLASH)) {
			url.append(Constants.SLASH);
		}
		url.append(coreConfiguration.getProperty("CONTEXT_PATH", "sm-shop"));
		return url.toString();
	}

	static Optional<String> approvalUrl(List<LinkDescription> links) {
		if(links == null) {
			return Optional.empty();
		}
		return links.stream()
				.filter(l -> APPROVE_LINK.equals(l.getRel()) || PAYER_ACTION_LINK.equals(l.getRel()))
				.map(LinkDescription::getHref)
				.findFirst();
	}

	private static <T> Optional<T> firstPayment(List<PurchaseUnit> units, java.util.function.Function<PaymentCollection, List<T>> extractor) {
		if(units == null) {
			return Optional.empty();
		}
		return units.stream()
				.map(PurchaseUnit::getPayments)
				.filter(java.util.Objects::nonNull)
				.map(extractor)
				.filter(list -> list != null && !list.isEmpty())
				.map(list -> list.get(0))
				.findFirst();
	}

	private static IntegrationException toIntegrationException(Exception e) {
		if(e instanceof IntegrationException) {
			return (IntegrationException)e;
		}
		if(e instanceof ApiException) {
			LOGGER.error("Paypal API error [" + ((ApiException)e).getResponseCode() + "] " + e.getMessage());
		}
		return new IntegrationException(e);
	}

}
