package com.salesmanager.core.business.modules.integration.payment.impl;

import java.util.Map;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;

class StripeGateway {
    Charge createCharge(Map<String, Object> params) throws StripeException {
        return Charge.create(params);
    }

    Charge retrieveCharge(String id) throws StripeException {
        return Charge.retrieve(id);
    }

    void captureCharge(Charge charge) throws StripeException {
        charge.capture();
    }

    Refund refund(Map<String, Object> params) throws StripeException {
        return Refund.create(params);
    }

    PaymentIntent createIntent(PaymentIntentCreateParams params) throws StripeException {
        return PaymentIntent.create(params);
    }

    PaymentIntent retrieveIntent(String id) throws StripeException {
        return PaymentIntent.retrieve(id);
    }

    PaymentIntent captureIntent(PaymentIntent intent, PaymentIntentCaptureParams params) throws StripeException {
        return intent.capture(params);
    }
}
