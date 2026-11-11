package com.salesmanager.core.business.modules.integration.payment.impl;

import java.util.List;
import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;
import com.paypal.sdk.http.response.ApiResponse;
import com.paypal.sdk.models.AuthorizeOrderInput;
import com.paypal.sdk.models.CaptureAuthorizedPaymentInput;
import com.paypal.sdk.models.CaptureOrderInput;
import com.paypal.sdk.models.CaptureRequest;
import com.paypal.sdk.models.CapturedPayment;
import com.paypal.sdk.models.CreateOrderInput;
import com.paypal.sdk.models.Money;
import com.paypal.sdk.models.Order;
import com.paypal.sdk.models.OrderAuthorizeResponse;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.PurchaseUnit;
import com.paypal.sdk.models.Refund;
import com.paypal.sdk.models.RefundCapturedPaymentInput;
import com.paypal.sdk.models.RefundRequest;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;

class PayPalGateway {
    private final PaypalServerSdkClient client;

    PayPalGateway(IntegrationConfiguration configuration) {
        client = new PaypalServerSdkClient.Builder()
            .environment(Constants.PRODUCTION_ENVIRONMENT.equals(configuration.getEnvironment())
                ? Environment.PRODUCTION : Environment.SANDBOX)
            .clientCredentialsAuth(new ClientCredentialsAuthModel.Builder(
                configuration.getIntegrationKeys().get("clientId"),
                configuration.getIntegrationKeys().get("clientSecret")).build()).build();
    }

    PayPalGateway(PaypalServerSdkClient client) {
        this.client = client;
    }

    Result create(OrderRequest request) throws Exception {
        ApiResponse<Order> response = client.getOrdersController().createOrder(
            new CreateOrderInput.Builder("application/json", request).prefer("return=representation").build());
        Order order = response.getResult();
        Result result = result(order.getId(), response);
        if (order.getLinks() != null) {
            order.getLinks().stream().filter(link -> "approve".equals(link.getRel())
                || "payer-action".equals(link.getRel())).findFirst()
                .ifPresent(link -> result.approvalUrl = link.getHref());
        }
        return result;
    }

    Result authorize(String orderId) throws Exception {
        ApiResponse<OrderAuthorizeResponse> response = client.getOrdersController().authorizeOrder(
            new AuthorizeOrderInput.Builder(orderId, "application/json").prefer("return=representation").build());
        List<PurchaseUnit> units = response.getResult().getPurchaseUnits();
        if (units == null || units.isEmpty() || units.get(0).getPayments() == null
            || units.get(0).getPayments().getAuthorizations() == null
            || units.get(0).getPayments().getAuthorizations().isEmpty()) {
            throw new IntegrationException("PayPal returned no authorization");
        }
        com.paypal.sdk.models.AuthorizationWithAdditionalData authorization =
            units.get(0).getPayments().getAuthorizations().get(0);
        checkStatus(String.valueOf(authorization.getStatus()), "CREATED", "PENDING");
        Result result = result(authorization.getId(), response);
        if (response.getResult().getPayer() != null) {
            result.payerId = response.getResult().getPayer().getPayerId();
        }
        return result;
    }

    Result captureOrder(String orderId) throws Exception {
        ApiResponse<Order> response = client.getOrdersController().captureOrder(
            new CaptureOrderInput.Builder(orderId, "application/json").prefer("return=representation").build());
        List<PurchaseUnit> units = response.getResult().getPurchaseUnits();
        if (units == null || units.isEmpty() || units.get(0).getPayments() == null
            || units.get(0).getPayments().getCaptures() == null
            || units.get(0).getPayments().getCaptures().isEmpty()) {
            throw new IntegrationException("PayPal returned no capture");
        }
        com.paypal.sdk.models.OrdersCapture capture = units.get(0).getPayments().getCaptures().get(0);
        checkStatus(String.valueOf(capture.getStatus()), "COMPLETED", "PENDING");
        Result result = result(capture.getId(), response);
        if (response.getResult().getPayer() != null) {
            result.payerId = response.getResult().getPayer().getPayerId();
        }
        return result;
    }

    Result capture(String authorizationId, Money amount) throws Exception {
        ApiResponse<CapturedPayment> response = client.getPaymentsController().captureAuthorizedPayment(
            new CaptureAuthorizedPaymentInput.Builder(authorizationId, "application/json")
                .body(new CaptureRequest.Builder().amount(amount).finalCapture(false).build())
                .prefer("return=representation").build());
        checkStatus(String.valueOf(response.getResult().getStatus()), "COMPLETED", "PENDING");
        return result(response.getResult().getId(), response);
    }

    Result refund(String captureId, Money amount) throws Exception {
        ApiResponse<Refund> response = client.getPaymentsController().refundCapturedPayment(
            new RefundCapturedPaymentInput.Builder(captureId, "application/json")
                .body(new RefundRequest.Builder().amount(amount).build()).prefer("return=representation").build());
        checkStatus(String.valueOf(response.getResult().getStatus()), "COMPLETED", "PENDING");
        return result(response.getResult().getId(), response);
    }

    private void checkStatus(String status, String first, String second) throws IntegrationException {
        if (!first.equals(status) && !second.equals(status)) {
            throw new IntegrationException("PayPal transaction status " + status);
        }
    }

    private Result result(String id, ApiResponse<?> response) throws IntegrationException {
        if (id == null || id.isEmpty()) {
            throw new IntegrationException("PayPal returned no transaction id");
        }
        return new Result(id, response.getHeaders() == null ? null : response.getHeaders().value("paypal-debug-id"));
    }

    static class Result {
        final String id;
        final String correlation;
        String payerId;
        String approvalUrl;

        Result(String id, String correlation) {
            this.id = id;
            this.correlation = correlation;
        }
    }
}
