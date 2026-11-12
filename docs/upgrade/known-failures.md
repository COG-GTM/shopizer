# Behaviour defects observed on the Boot 2.5 baseline

AB-533 changes no production Java or application configuration. These observations
are recorded separately for follow-up; no Jira issue was created because this run
is explicitly read-only in Jira.

## KF-1: private read of a deleted product returns HTTP 500

Sev 2: an API consumer receives a server error for a missing product, degrading
error handling; listing/checking product existence is a reasonable workaround.

Reproduction on the unchanged Boot 2.5.12 production sources:

1. Log in as the seeded administrator at `POST /api/v1/private/login`.
2. Create a product with `POST /api/v1/private/product` and record its ID.
3. Delete it with `DELETE /api/v1/private/product/{id}` (HTTP 200).
4. Read `GET /api/v2/private/product/{id}` with the same administrator JWT.

Expected: HTTP 404. Observed: HTTP 500. The log shows
`NullPointerException: Product cannot be null` at
`ReadableProductDefinitionMapper.merge:68`, called by
`ProductDefinitionFacadeImpl.getProduct:89` and `ProductApiV2.getV2:155`.

`BehaviourSmokeTest.deletedPrivateProductShouldReturnNotFound` retains the desired
assertion with `@Disabled("KF-1: ...")`. Remove the annotation when the defect is
fixed in a separate ticket. The required CRUD flow remains enabled: it verifies
private reads and updates, deletes successfully, and confirms the SKU no longer
exists using the private uniqueness endpoint.

The initial checkout probe failed because the fresh H2 store had no payment
configuration. This is a fixture prerequisite, not a recorded product defect:
the suite now configures the built-in `moneyorder` module with its required
payee address through the private API before checking out. JSON clients must
also send `Accept: application/json` to get a 401 Basic-auth challenge rather
than the legacy form-login redirect for browser requests.

## Resolved in AB-544: inventory decrement looked up the product by order-line id

`OrderServiceImpl.process` decremented inventory with
`productService.getById(orderProduct.getId())`, i.e. it used the `ORDER_PRODUCT_ID` as a
`PRODUCT_ID`. On Hibernate 5 the smoke checkout only passed because the two sequences happened
to point at an existing product; with Hibernate 6 the reference proxy fails fast with
`EntityNotFoundException: Unable to find Product with id N` once that coincidence breaks
(`BehaviourSmokeTest.checkoutCreatesOrderThatAdminCanRead` in the full `sm-shop` run). The
lookup now uses the order line's SKU (`productService.getBySku`), which is the product the line
was created from.
