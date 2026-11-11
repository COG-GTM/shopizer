# AB-539 integration SDK migration

This change retains Spring Boot 2.5, javax APIs and the Java 11 source/bytecode
target. Build verification uses JDK 17 and JDK 21. No provider credentials are
included in the repository; the integration tests mock provider boundaries.

## PayPal: migrate each merchant's IntegrationModule configuration

Both `paypal-express-checkout` (Spring bean `paypal-ec`) and the REST payment
implementation now use PayPal Server SDK **2.4.0**, Orders v2 and Payments v2.
The NVP/SOAP Merchant SDK has been removed. The REST implementation previously
contained only stubs; it now implements the same Orders v2 workflow as EC.

The `integrationKeys` map must be migrated:

| Previous key | Replacement |
| --- | --- |
| EC `username`, `api`, `signature` | `clientId`, `clientSecret` from a REST app; these are **not** converted NVP credentials |
| REST `client`, `secret` | `clientId`, `clientSecret` |
| `transaction` | Unchanged: `AUTHORIZECAPTURE` creates a CAPTURE-intent order; other values create an AUTHORIZE-intent order |

1. Sign in to <https://developer.paypal.com/dashboard/applications> with the
   merchant's PayPal business account. Select **Sandbox** or **Live**, then
   create/select a REST app under **Apps & Credentials**.
2. Copy that app's Client ID and Secret into the merchant's payment integration
   configuration using the existing admin IntegrationModule configuration flow.
   Store them as `integrationKeys.clientId` and `integrationKeys.clientSecret`.
   Validation errors use precisely those field names. Admin/front-end field
   labels for the previous credentials are outside this ticket's owned area;
   if that UI cannot edit arbitrary integration keys, update the configuration
   through the existing integration configuration API instead: authenticated
   admin `GET /api/v1/private/modules/payment/paypal-express-checkout?store=DEFAULT`,
   then `POST /api/v1/private/modules/payment?store=DEFAULT` with the returned
   configuration's `code`, `active`, `defaultSelected`, `integrationOptions`,
   and updated `integrationKeys`. Replace `DEFAULT` with the merchant store code.
   Keep all unrelated integration keys/options; POST replaces the maps rather
   than merging them. Do not paste credentials into tickets, logs or examples.
3. Preserve `transaction`, environment, and other merchant settings. `TEST`
   selects sandbox; `PROD` selects live. Sandbox credentials do not work in live
   mode and vice versa. Remove the obsolete credential keys after verification.
4. Complete an approved sandbox order, authorization, capture and partial refund
   before enabling live traffic. Back up configuration and settle/refund existing
   legacy authorizations before switching. Orders v2 does not create NVP EC
   tokens; old, in-flight EC tokens cannot be resumed by this implementation.

### Caller and stored-transaction compatibility

`initTransaction` and `initPaypalTransaction` return an INIT transaction whose
`TOKEN` is the Orders v2 order ID. Use the additive `APPROVALURL` detail returned
by PayPal to send the payer for approval; do **not** construct an old
`cgi-bin/webscr?cmd=_express-checkout` URL. The reference module configuration
and legacy shop properties still contain that old URL and are outside the owned
area. Consumers must adopt the provider approval URL as part of their rollout.

After approval, supply the order ID as `PaypalPayment.paymentToken`.
`authorize` authorizes the order; `authorizeAndCapture` captures the order.
`TOKEN`, `PAYERID`, `TRANSACTIONID`, and `CORRELATION` remain the detail keys.
`CORRELATION` now contains PayPal's debug ID rather than an NVP correlation ID.
The transaction type remains the supplied payment transaction type, as before.

Capturing an authorization uses `order.total` (including partial amounts) and
leaves it open to further captures, matching the old NOTCOMPLETE flag.
`AUTHORIZATIONID` and `TRANSACTIONID` retain the authorization ID. The additive
`CAPTUREID` stores the new capture ID required by Payments v2 refunds. Refund
uses `CAPTUREID`, falling back to `TRANSACTIONID` for direct order captures.
A partial refund supplies the requested amount; a full refund omits the amount
to refund the remaining capture. Refund transactions retain type REFUND and
store the refund ID in `TRANSACTIONID`; the original transaction receives the
refund correlation, matching the previous implementation.

Historical NVP transactions remain readable, but the new SDK cannot guarantee
operations against their IDs. Reconcile these with PayPal before migration;
do not rewrite stored IDs. There is no NVP-to-Orders ID translation.

New SDK imports are confined to payment implementation files and use
`com.paypal.sdk`, `com.paypal.sdk.authentication`,
`com.paypal.sdk.http.response`, and `com.paypal.sdk.models`. No legacy
`urn.ebay`, `com.paypal.core`, `com.paypal.exception`,
`com.paypal.sdk.exceptions`, `com.paypal.api`, or `com.paypal.base` imports remain.

## AWS SDK v2

Root dependency management imports `software.amazon.awssdk:bom:2.51.0`
(published August 2026, approximately three months before this upgrade), not the
latest AWS release. S3 uses `S3Client`; email uses `SesClient`. Bucket names,
CMS region/location settings, `config.emailSender.region`, S3 object keys,
public-read ACLs, and SES subject/HTML/text bodies are unchanged. SES accepts
the existing enum-style region (`US_EAST_1`) as well as `us-east-1`.

Clients use `DefaultCredentialsProvider`: system properties, environment
variables, web identity, shared profiles, container credentials and instance
profiles are supported. AWS v2 checks system properties before environment
variables (v1 did the reverse). Avoid conflicting credential sources. There are
no new application credential configuration keys. Existing IAM permissions
continue to be required. No provider calls are made by unit tests.

S3 listing still returns null for empty results and retains the existing single
page behavior. Directory deletion remains deletion of the exact prefix object,
not recursive deletion. Product single-image deletion retains its previous key
without SMALL/LARGE; these behaviors were intentionally not repaired here.

## Stripe and Braintree

* `com.stripe:stripe-java` **34.1.0**: current stable major/release on Central at
  upgrade time; no alpha/beta builds. Existing Charge and PaymentIntent flows
  remain unchanged behind an injectable adapter, without Mockito static mocking
  or an additional mocking dependency. Amount conversion and transaction detail
  keys are retained. In particular, both Stripe implementations' existing refund
  result type CAPTURE and null `TRANSACTIONID` are deliberately preserved.
  This SDK targets a newer Stripe API version; validate account-specific
  behavior in sandbox before rollout.
* `com.braintreepayments.gateway:braintree-java` **3.55.1**: current stable
  major/release on Central at upgrade time. The gateway can be injected for
  tests. Nonce, merchant credentials, environment selection, sale, settlement,
  refund amounts and transaction mappings are unchanged. This ticket does not
  alter the existing auth-and-capture sale request options.

These SDKs are compatible with the Java 11 application target. Unit tests are
provider-isolated contract tests, not proof of live merchant/account behavior.
Gson is aligned to 2.10.1 (Stripe's required runtime baseline), OkHttp to
4.12.0 (required by PayPal's APIMatic adapter), Okio to 3.6.0, and Kotlin to
1.9.10 (the Okio JVM baseline). Without those version entries, Boot's Gson
2.8.9/OkHttp 3.14.9 and a legacy transitive Okio 1.0.0 cause runtime linkage
errors. These are shared transitive libraries; provider-isolated tests do not
verify other vendors' live behavior with the aligned versions.
AB-549 should include the credential and approval-URL rollout in its integration
runbook and verify all provider flows with sandbox accounts.
