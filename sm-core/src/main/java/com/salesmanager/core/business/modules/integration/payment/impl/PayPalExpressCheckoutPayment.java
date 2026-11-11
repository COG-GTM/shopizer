package com.salesmanager.core.business.modules.integration.payment.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import com.paypal.sdk.models.AmountBreakdown;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.ItemRequest;
import com.paypal.sdk.models.Money;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.PaymentSource;
import com.paypal.sdk.models.PaypalWallet;
import com.paypal.sdk.models.PaypalWalletExperienceContext;
import com.paypal.sdk.models.PurchaseUnitRequest;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.utils.CoreConfiguration;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.PaypalPayment;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;

public class PayPalExpressCheckoutPayment implements PaymentModule {
    @Inject
    private PricingService pricingService;
    @Inject
    private CoreConfiguration coreConfiguration;

    private PayPalGateway gateway;

    void setGateway(PayPalGateway gateway) {
        this.gateway = gateway;
    }

    private PayPalGateway gateway(IntegrationConfiguration configuration) {
        return gateway == null ? new PayPalGateway(configuration) : gateway;
    }

    @Override
    public void validateModuleConfiguration(IntegrationConfiguration configuration, MerchantStore store)
            throws IntegrationException {
        Map<String, String> keys = configuration.getIntegrationKeys();
        List<String> errors = new ArrayList<>();
        for (String key : new String[] {"clientId", "clientSecret"}) {
            if (keys == null || StringUtils.isBlank(keys.get(key))) {
                errors.add(key);
            }
        }
        if (!errors.isEmpty()) {
            IntegrationException exception = new IntegrationException(IntegrationException.ERROR_VALIDATION_SAVE);
            exception.setErrorFields(errors);
            throw exception;
        }
    }

    @Override
    public Transaction initTransaction(MerchantStore store, Customer customer, BigDecimal amount,
            Payment payment, IntegrationConfiguration configuration, IntegrationModule module)
            throws IntegrationException {
        try {
            PurchaseUnitRequest unit = new PurchaseUnitRequest.Builder(new AmountWithBreakdown.Builder(
                store.getCurrency().getCode(), pricingService.getStringAmount(amount, store)).build()).build();
            return create(store, amount, unit, configuration);
        } catch (Exception e) {
            throw new IntegrationException(e);
        }
    }

    public Transaction initPaypalTransaction(MerchantStore store, List<ShoppingCartItem> items,
            OrderTotalSummary summary, Payment payment, IntegrationConfiguration configuration,
            IntegrationModule module) throws IntegrationException {
        try {
            Validate.notNull(configuration, "Configuration must not be null");
            Validate.notNull(payment, "Payment must not be null");
            Validate.notNull(summary, "OrderTotalSummary must not be null");
            List<ItemRequest> lineItems = new ArrayList<>();
            for (ShoppingCartItem item : items) {
                lineItems.add(new ItemRequest.Builder(item.getProduct().getProductDescription().getName(),
                    money(payment.getCurrency().getCode(), item.getFinalPrice().getFinalPrice(), store),
                    String.valueOf(item.getQuantity())).build());
            }
            AmountBreakdown.Builder breakdown = new AmountBreakdown.Builder()
                .itemTotal(money(store.getCurrency().getCode(), summary.getSubTotal(), store));
            BigDecimal tax = null;
            for (OrderTotal total : summary.getTotals()) {
                if (Constants.OT_SHIPPING_MODULE_CODE.equals(total.getModule())) {
                    breakdown.shipping(money(store.getCurrency().getCode(), total.getValue(), store));
                }
                if (Constants.OT_HANDLING_MODULE_CODE.equals(total.getModule())) {
                    breakdown.handling(money(store.getCurrency().getCode(), total.getValue(), store));
                }
                if (Constants.OT_TAX_MODULE_CODE.equals(total.getModule())) {
                    tax = (tax == null ? BigDecimal.ZERO : tax).add(total.getValue());
                }
            }
            if (tax != null) {
                breakdown.taxTotal(money(store.getCurrency().getCode(), tax, store));
            }
            PurchaseUnitRequest unit = new PurchaseUnitRequest.Builder(new AmountWithBreakdown.Builder(
                store.getCurrency().getCode(), pricingService.getStringAmount(summary.getTotal(), store))
                .breakdown(breakdown.build()).build()).items(lineItems).build();
            return create(store, summary.getTotal(), unit, configuration);
        } catch (Exception e) {
            throw new IntegrationException(e);
        }
    }

    private Transaction create(MerchantStore store, BigDecimal amount, PurchaseUnitRequest unit,
            IntegrationConfiguration configuration) throws Exception {
        String base = store.getDomainName();
        if (StringUtils.isNotBlank(coreConfiguration.getProperty("SHOP_SCHEME"))) {
            base = coreConfiguration.getProperty("SHOP_SCHEME", "http") + "://" + base;
        }
        String url = base + (StringUtils.isNotBlank(base) && !base.endsWith(Constants.SLASH) ? Constants.SLASH : "")
            + coreConfiguration.getProperty("CONTEXT_PATH", "sm-shop") + Constants.SHOP_URI
            + "/paypal/checkout" + coreConfiguration.getProperty("URL_EXTENSION", ".html");
        CheckoutPaymentIntent intent = TransactionType.AUTHORIZECAPTURE.name().equalsIgnoreCase(
            configuration.getIntegrationKeys().get("transaction")) ? CheckoutPaymentIntent.CAPTURE
            : CheckoutPaymentIntent.AUTHORIZE;
        OrderRequest request = new OrderRequest.Builder(intent, Collections.singletonList(unit))
            .paymentSource(new PaymentSource.Builder().paypal(new PaypalWallet.Builder()
                .experienceContext(new PaypalWalletExperienceContext.Builder()
                    .returnUrl(url + "/success").cancelUrl(url + "/cancel").build()).build()).build()).build();
        PayPalGateway.Result result = gateway(configuration).create(request);
        Transaction transaction = transaction(amount, TransactionType.INIT);
        transaction.getTransactionDetails().put("TOKEN", result.id);
        transaction.getTransactionDetails().put("CORRELATION", result.correlation);
        transaction.getTransactionDetails().put("APPROVALURL", result.approvalUrl);
        return transaction;
    }

    @Override
    public Transaction authorize(MerchantStore store, Customer customer, List<ShoppingCartItem> items,
            BigDecimal amount, Payment payment, IntegrationConfiguration configuration, IntegrationModule module)
            throws IntegrationException {
        return process(store, amount, payment, configuration, false);
    }

    @Override
    public Transaction authorizeAndCapture(MerchantStore store, Customer customer, List<ShoppingCartItem> items,
            BigDecimal amount, Payment payment, IntegrationConfiguration configuration, IntegrationModule module)
            throws IntegrationException {
        return process(store, amount, payment, configuration, true);
    }

    private Transaction process(MerchantStore store, BigDecimal amount, Payment payment, IntegrationConfiguration configuration,
            boolean capture) throws IntegrationException {
        try {
            PaypalPayment paypal = (PaypalPayment) payment;
            Validate.notNull(paypal.getPaymentToken(), "A paypal payment token is required to process this transaction");
            com.paypal.sdk.models.Order approvedOrder = gateway(configuration).getOrder(paypal.getPaymentToken());
            if (approvedOrder == null || approvedOrder.getPurchaseUnits() == null
                || approvedOrder.getPurchaseUnits().size() != 1
                || approvedOrder.getPurchaseUnits().get(0) == null
                || approvedOrder.getPurchaseUnits().get(0).getAmount() == null
                || !pricingService.getStringAmount(amount, store).equals(
                    approvedOrder.getPurchaseUnits().get(0).getAmount().getValue())
                || !store.getCurrency().getCode().equals(
                    approvedOrder.getPurchaseUnits().get(0).getAmount().getCurrencyCode())) {
                throw new IntegrationException("PayPal order amount or currency does not match the cart");
            }
            PayPalGateway.Result result = capture ? gateway(configuration).captureOrder(paypal.getPaymentToken())
                : gateway(configuration).authorize(paypal.getPaymentToken());
            Transaction transaction = transaction(amount, payment.getTransactionType());
            transaction.getTransactionDetails().put("TOKEN", paypal.getPaymentToken());
            transaction.getTransactionDetails().put("PAYERID", result.payerId);
            transaction.getTransactionDetails().put("TRANSACTIONID", result.id);
            transaction.getTransactionDetails().put("CORRELATION", result.correlation);
            return transaction;
        } catch (Exception e) {
            throw new IntegrationException(e);
        }
    }

    @Override
    public Transaction capture(MerchantStore store, Customer customer, Order order, Transaction authorization,
            IntegrationConfiguration configuration, IntegrationModule module) throws IntegrationException {
        try {
            String authorizationId = authorization.getTransactionDetails().get("TRANSACTIONID");
            Validate.notNull(authorizationId, "Transaction details must contain a TRANSACTIONID");
            PayPalGateway.Result result = gateway(configuration).capture(authorizationId,
                money(order.getCurrency().getCode(), order.getTotal(), store));
            Transaction transaction = transaction(order.getTotal(), TransactionType.CAPTURE);
            transaction.getTransactionDetails().put("AUTHORIZATIONID", authorizationId);
            transaction.getTransactionDetails().put("TRANSACTIONID", authorizationId);
            transaction.getTransactionDetails().put("CAPTUREID", result.id);
            return transaction;
        } catch (Exception e) {
            throw new IntegrationException(e);
        }
    }

    @Override
    public Transaction refund(boolean partial, MerchantStore store, Transaction original, Order order,
            BigDecimal amount, IntegrationConfiguration configuration, IntegrationModule module)
            throws IntegrationException {
        try {
            String captureId = original.getTransactionDetails().getOrDefault("CAPTUREID",
                original.getTransactionDetails().get("TRANSACTIONID"));
            Validate.notNull(captureId, "Transaction details must contain a TRANSACTIONID");
            PayPalGateway.Result result = gateway(configuration).refund(captureId,
                partial ? money(order.getCurrency().getCode(), amount, store) : null);
            Transaction transaction = transaction(amount, TransactionType.REFUND);
            transaction.getTransactionDetails().put("TRANSACTIONID", result.id);
            original.getTransactionDetails().put("CORRELATION", result.correlation);
            return transaction;
        } catch (Exception e) {
            if (e instanceof IntegrationException) {
                throw (IntegrationException) e;
            }
            throw new IntegrationException(e);
        }
    }

    private Money money(String currency, BigDecimal amount, MerchantStore store) throws Exception {
        return new Money.Builder(currency, pricingService.getStringAmount(amount, store)).build();
    }

    private Transaction transaction(BigDecimal amount, TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setTransactionDate(new Date());
        transaction.setTransactionType(type);
        transaction.setPaymentType(PaymentType.PAYPAL);
        return transaction;
    }
}
