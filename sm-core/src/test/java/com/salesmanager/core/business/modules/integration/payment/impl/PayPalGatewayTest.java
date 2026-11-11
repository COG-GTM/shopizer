package com.salesmanager.core.business.modules.integration.payment.impl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.Money;
import com.paypal.sdk.models.OAuthToken;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.PurchaseUnitRequest;
import com.salesmanager.core.modules.integration.IntegrationException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Protocol;
import okio.Buffer;

class PayPalGatewayTest {
    private final AtomicReference<Request> request = new AtomicReference<>();
    private String requestBody;

    private PayPalGateway gateway(String json) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            request.set(chain.request());
            Buffer buffer = new Buffer();
            if (chain.request().body() != null) {
                chain.request().body().writeTo(buffer);
            }
            requestBody = buffer.readUtf8();
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(201).message("Created").header("Content-Type", "application/json")
                .header("paypal-debug-id", "debug-id")
                .body(ResponseBody.create(MediaType.parse("application/json"), json)).build();
        }).build();
        PaypalServerSdkClient sdk = new PaypalServerSdkClient.Builder().environment(Environment.SANDBOX)
            .clientCredentialsAuth(new ClientCredentialsAuthModel.Builder("mock-client", "mock-secret")
                .oAuthToken(new OAuthToken.Builder("mock-token", "Bearer")
                    .expiry(System.currentTimeMillis() / 1000 + 3600).expiresIn(3600L).build()).build())
            .httpClientConfig(builder -> builder.httpClientInstance(http).numberOfRetries(0)).build();
        return new PayPalGateway(sdk);
    }

    @Test
    void createUsesOrdersV2AndExtractsApproval() throws Exception {
        PayPalGateway gateway = gateway("{\"id\":\"order-id\",\"status\":\"CREATED\",\"links\":["
            + "{\"href\":\"https://example.com/approve\",\"rel\":\"payer-action\",\"method\":\"GET\"}]}");
        PayPalGateway.Result result = gateway.create(new OrderRequest.Builder(CheckoutPaymentIntent.AUTHORIZE,
            Collections.singletonList(new PurchaseUnitRequest.Builder(
                new AmountWithBreakdown.Builder("USD", "12.34").build()).build())).build());
        assertEquals("/v2/checkout/orders", request.get().url().encodedPath());
        assertTrue(requestBody.contains("AUTHORIZE"));
        assertTrue(requestBody.contains("12.34"));
        assertEquals("order-id", result.id);
        assertEquals("debug-id", result.correlation);
        assertEquals("https://example.com/approve", result.approvalUrl);
    }

    @Test
    void authorizeExtractsAuthorizationAndPayer() throws Exception {
        PayPalGateway gateway = gateway("{\"id\":\"order-id\",\"status\":\"COMPLETED\","
            + "\"payer\":{\"payer_id\":\"payer\"},\"purchase_units\":[{\"payments\":{"
            + "\"authorizations\":[{\"id\":\"auth-id\",\"status\":\"CREATED\"}]}}]}");
        PayPalGateway.Result result = gateway.authorize("order-id");
        assertEquals("/v2/checkout/orders/order-id/authorize", request.get().url().encodedPath());
        assertEquals("auth-id", result.id);
        assertEquals("payer", result.payerId);
    }

    @Test
    void orderCaptureExtractsCapture() throws Exception {
        PayPalGateway gateway = gateway("{\"id\":\"order-id\",\"status\":\"COMPLETED\","
            + "\"purchase_units\":[{\"payments\":{\"captures\":[{\"id\":\"capture-id\",\"status\":\"COMPLETED\"}]}}]}");
        assertEquals("capture-id", gateway.captureOrder("order-id").id);
        assertEquals("/v2/checkout/orders/order-id/capture", request.get().url().encodedPath());
    }

    @Test
    void captureAuthorizationKeepsPartialCaptureOption() throws Exception {
        PayPalGateway gateway = gateway("{\"id\":\"capture-id\",\"status\":\"COMPLETED\"}");
        assertEquals("capture-id", gateway.capture("auth-id", new Money.Builder("USD", "5.00").build()).id);
        assertEquals("/v2/payments/authorizations/auth-id/capture", request.get().url().encodedPath());
        assertTrue(requestBody.contains("\"final_capture\":false"));
        assertTrue(requestBody.contains("5.00"));
    }

    @Test
    void refundsCaptureWithOptionalAmount() throws Exception {
        PayPalGateway gateway = gateway("{\"id\":\"refund-id\",\"status\":\"COMPLETED\"}");
        assertEquals("refund-id", gateway.refund("capture-id", new Money.Builder("USD", "5.00").build()).id);
        assertEquals("/v2/payments/captures/capture-id/refund", request.get().url().encodedPath());
        assertTrue(requestBody.contains("5.00"));
        gateway.refund("capture-id", null);
        assertFalse(requestBody.contains("amount"));
    }

    @Test
    void missingAuthorizationDoesNotBecomeSuccessfulTransaction() {
        PayPalGateway gateway = gateway("{\"id\":\"order-id\",\"purchase_units\":[]}");
        assertThrows(IntegrationException.class, () -> gateway.authorize("order-id"));
    }

    @Test
    void declinedCaptureDoesNotBecomeSuccessfulTransaction() {
        PayPalGateway gateway = gateway("{\"id\":\"capture-id\",\"status\":\"DECLINED\"}");
        assertThrows(IntegrationException.class,
            () -> gateway.capture("auth-id", new Money.Builder("USD", "5.00").build()));
    }
}
