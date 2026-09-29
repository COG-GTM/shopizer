# sm-shop REST API integration tests

The storefront suite checks the customer-facing REST API end to end over HTTP:
**catalog browse -> cart -> checkout**. Each test boots the full `ShopApplication` on a
random port (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) and sends real requests through
`TestRestTemplate`. It runs against the in-memory H2 database the other `sm-shop` tests use,
so you don't need MySQL, Docker, or any external service.

## What is covered

All classes live in `src/test/java/com/salesmanager/test/shop/integration/storefront/` and
carry the JUnit 5 tag `storefront`.

| Class | Flow |
| --- | --- |
| `CatalogBrowseApiIntegrationTest` | Category list (`GET /api/v1/category`), products by category friendly URL (`GET /api/v2/products/category/{friendlyUrl}`), product search by category id (`GET /api/v2/products?categoryIds=`), product detail by SKU and by friendly URL, and an unknown SKU |
| `CartApiIntegrationTest` | Create a cart (`POST /api/v1/cart`), read it (`GET /api/v1/cart/{code}`), add or update lines (`PUT /api/v1/cart/{code}`), update several lines at once (`POST /api/v1/cart/{code}/multi`), remove a line (`DELETE /api/v1/cart/{code}/product/{sku}`), cart total (`GET /api/v1/cart/{code}/total`), an unknown product, and an unknown cart |
| `CheckoutApiIntegrationTest` | Payment module configuration (`/api/v1/private/modules/payment`), guest checkout (`POST /api/v1/cart/{code}/checkout`) followed by a merchant order lookup (`GET /api/v1/private/orders/{id}`), checkout as a registered customer (`POST /api/v1/auth/cart/{code}/checkout`) followed by order history (`GET /api/v1/auth/orders`), a payment amount that doesn't match the cart, and an unknown cart |

Shared fixtures are in `StorefrontApiTestSupport`, which extends the existing
`ServicesTestSupport`:

- Categories and products are created through the admin API, using the seeded
  `admin@shopizer.com` / `password` account.
- All storefront calls go out without an `Authorization` header, so the tests see exactly what an
  anonymous shopper sees.
- Every code, SKU and email gets a random suffix, so tests don't depend on each other or on run
  order, and they can share the database with the rest of the suite.
- Checkout uses the built-in **money order** payment module (`moneyorder`). It authorizes and
  captures offline, so no payment gateway credentials or network access are needed. Each
  checkout test switches the module on idempotently before it runs.

## Running

Prerequisites: JDK 11+ (CI uses 11, and 17 works too) and Maven 3.6+ or the bundled `./mvnw`.

The first time, build and install the modules `sm-shop` depends on:

```bash
./mvnw clean install -DskipTests
```

Run only the storefront suite (well under a minute on a warm machine; most of it is Spring context start-up):

```bash
./mvnw -pl sm-shop test -Dgroups=storefront
```

Run a single class or a single test:

```bash
./mvnw -pl sm-shop test -Dtest=CheckoutApiIntegrationTest
./mvnw -pl sm-shop test -Dtest='CartApiIntegrationTest#modifyCart'
```

Run everything in `sm-shop`, including this suite:

```bash
./mvnw -pl sm-shop test
```

Reports are written to `sm-shop/target/surefire-reports/`.

## Notes and known issues

- **Email noise.** Checkout and registration try to send confirmation emails through the default
  SMTP settings. With no mail server available, a `MailSendException` stack trace is logged. It
  is harmless: the tests pass and the API responses are unaffected.
- **Category detail by friendly URL is `@Disabled`.** `CategoryApi` maps both
  `GET /api/v1/category/{id}` and `GET /api/v1/category/{friendlyUrl}` to the same path
  pattern, so Spring MVC fails those requests with `IllegalStateException: Ambiguous handler
  methods` (HTTP 500). Once the mapping is fixed, remove the `@Disabled` on
  `getsCategoryByFriendlyUrl`.
- **Status codes follow current behaviour, which isn't always ideal.** An unknown product SKU
  on `/api/v2/product/{sku}` returns `400`, not `404`. A checkout against an unknown cart or
  with a mismatched amount also returns `400`. The assertions pin these codes so that any
  change to them shows up as a failing test.
- **Order currency.** Checkout requests must include `currency`, set to the store currency
  (`GET /api/v1/store/DEFAULT`). If it is missing, the server fails with "Currency not found for
  code null".
- **Payment amount.** The amount must equal the server-calculated cart total
  (`ReadableShoppingCart.total`, or `GET /api/v1/cart/{code}/total`). If it doesn't, checkout
  is rejected with "Payment.amount does not match what the system has calculated".
