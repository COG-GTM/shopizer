# AB-539 integration SDK migration

## PayPal Express Checkout configuration

The Express Checkout module continues to use the existing `transaction` setting
and environment setting. Replace the legacy NVP API credentials with REST app
credentials from the PayPal Developer Dashboard:

| Previous key | New key |
| --- | --- |
| `username` | `clientId` |
| `api` | — |
| `signature` | — |
| — | `clientSecret` |

Use the REST application's client ID and secret for `clientId` and
`clientSecret`; the old NVP username, API password, and signature are not
interchangeable with these values.

Checkout now creates a PayPal Orders v2 order. The payment token returned by
initialization is the Orders v2 order ID, and that same order ID is supplied
when the payer approves the checkout. After an authorization is captured,
`TRANSACTIONID` identifies the capture (rather than the authorization), so the
subsequent refund flow can address the captured payment.

The `paypal-express-checkout` module's configuration labels in
`sm-shop/src/main/resources/bundles/payment.properties` and
`payment_fr.properties` remain unchanged for this migration. Updating labels
for `clientId` and `clientSecret` is tracked as follow-up AB-549.

## SDK versions

| Integration | Previous SDK | Replacement |
| --- | --- | --- |
| AWS S3 | `com.amazonaws:aws-java-sdk-s3:1.11.640` | `software.amazon.awssdk:s3:2.57.0` |
| AWS SES | `com.amazonaws:aws-java-sdk-ses:1.11.640` | `software.amazon.awssdk:sesv2:2.57.0` |
| PayPal | `com.paypal.sdk:merchantsdk:2.6.109` | `com.paypal.sdk:paypal-server-sdk:2.4.0` |
| Stripe | `com.stripe:stripe-java:19.5.0` | `com.stripe:stripe-java:34.0.0` |
| Braintree | `com.braintreepayments.gateway:braintree-java:2.73.0` | `com.braintreepayments.gateway:braintree-java:3.55.1` |

## AWS configuration and credentials

The existing AWS region and bucket configuration keys are unchanged. AWS
clients now use the AWS SDK v2 default credentials provider chain rather than
the old SDK's implicit provider. Credentials are resolved from supported
providers such as environment variables, Java system properties, the shared
`~/.aws` credentials/config files, and container or instance roles. Continue
to grant the runtime identity the required S3 or SES permissions.

## SES region

The existing `${config.emailSender.region}` setting is unchanged. SES accepts
either underscore-separated or hyphen-separated region names; for example,
`US_EAST_1` and `us-east-1` both resolve to `us-east-1`.
