# Identifier generation compatibility

## New-entity id semantics

`SalesManagerEntityServiceImpl` normalizes a numeric identifier of zero to `null`
before `save`, `saveAll`, and `saveAndFlush` delegate to Spring Data. The entity
model and API mapper defaults remain unchanged, so incoming DTOs may continue to
use `0` to represent a new entity. Hibernate 6.6 treats a non-null identifier as
an existing/detached entity; clearing the sentinel before the repository save
allows the configured generator to assign the identifier.

## Current generator configuration

`DataConfiguration` sets `hibernate.id.new_generator_mappings=false`. The shop
application properties also contain
`spring.jpa.hibernate.use-new-id-generator-mappings=true`. Tests inspecting the
Product persister in both the sm-core and sm-shop contexts reported
`org.hibernate.id.MultipleHiLoPerTableGenerator`, and both entity-manager
factories exposed `hibernate.id.new_generator_mappings=false`. The Boot property
therefore does not determine the effective Product generator in either context.

## `SM_SEQUENCER` measurements

The compiled `@TableGenerator` annotations resolve to **80 distinct `pkColumnValue`s**. The copied seed database contains **29 `SM_SEQUENCER` rows**; every seed row is represented by an annotation (no seed-only keys), while 51 mapped keys have no seed row. The annotation table includes resolved `SchemaConstant` values, including the description generators' `allocationSize=1` and `initialValue=2000`.

On Hibernate 5.4.33, each generator produced `allocationSize + 2` IDs. The observed IDs are consecutive. If a sequencer row exists with starting count `c`, the first ID is `c × allocationSize`; if the row is absent, the first ID is `1` (the legacy generator does not use the annotation's `initialValue` to seed the missing row). After `n` calls, an existing row stores `c + ceil(n / allocationSize)`. For a missing row and the measured allocation sizes, the final count is `4` for allocation size `1` and `2` for allocation size `50`. This formula matches the first IDs, the allocation boundary, and final `SEQ_COUNT` for all 80 rows.

Each H5 cell is `first three IDs; three IDs spanning the first allocation boundary; final SEQ_COUNT`. The seed count is the value in the unmodified seed copy before measurement.

| `SEQ_NAME` | Entity class | Allocation | Initial value | Seed `SEQ_COUNT` | Empty H5 | Seed-copy H5 |
| --- | --- | ---: | ---: | ---: | --- | --- |
| `CATALOG_ENT_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.catalog.CatalogCategoryEntry` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `CATALOG_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.catalog.Catalog` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `CATEGORY_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.category.Category` | 50 | 0 | `2` | first `1,2,3`; boundary `50,51,52`; final `2` | first `100,101,102`; boundary `149,150,151`; final `4` |
| `CONTENT_SEQ_NEXT_VAL` | `com.salesmanager.core.model.content.Content` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `COUNTRY_SEQ_NEXT_VAL` | `com.salesmanager.core.model.reference.country.Country` | 50 | 0 | `5` | first `1,2,3`; boundary `50,51,52`; final `2` | first `250,251,252`; boundary `299,300,301`; final `7` |
| `CURRENCY_SEQ_NEXT_VAL` | `com.salesmanager.core.model.reference.currency.Currency` | 50 | 0 | `4` | first `1,2,3`; boundary `50,51,52`; final `2` | first `200,201,202`; boundary `249,250,251`; final `6` |
| `CUSTOMER_OPTION_SEQ_NEXT_VAL` | `com.salesmanager.core.model.customer.attribute.CustomerOption` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `CUSTOMER_OPT_VAL_SEQ_NEXT_VAL` | `com.salesmanager.core.model.customer.attribute.CustomerOptionValue` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `CUSTOMER_REVIEW_SEQ_NEXT_VAL` | `com.salesmanager.core.model.customer.review.CustomerReview` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `CUSTOMER_SEQ_NEXT_VAL` | `com.salesmanager.core.model.customer.Customer` | 50 | 0 | `4` | first `1,2,3`; boundary `50,51,52`; final `2` | first `200,201,202`; boundary `249,250,251`; final `6` |
| `CUST_ATTR_SEQ_NEXT_VAL` | `com.salesmanager.core.model.customer.attribute.CustomerAttribute` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `CUST_OPTSET_SEQ_NEXT_VAL` | `com.salesmanager.core.model.customer.attribute.CustomerOptionSet` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `CUST_OPT_SEQ_NEXT_VAL` | `com.salesmanager.core.model.system.optin.CustomerOptin` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `FILE_HISTORY_ID_NEXT_VALUE` | `com.salesmanager.core.model.order.filehistory.FileHistory` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `GEOZONE_SEQ_NEXT_VAL` | `com.salesmanager.core.model.reference.geozone.GeoZone` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `GROUP_SEQ_NEXT_VAL` | `com.salesmanager.core.model.user.Group` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `LANG_SEQ_NEXT_VAL` | `com.salesmanager.core.model.reference.language.Language` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `MANUFACT_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `MERCH_CONF_SEQ_NEXT_VAL` | `com.salesmanager.core.model.system.MerchantConfiguration` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `MOD_CONF_SEQ_NEXT_VAL` | `com.salesmanager.core.model.system.IntegrationModule` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `MR_LOG_SEQ_NEXT_VAL` | `com.salesmanager.core.model.system.MerchantLog` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `OPTIN_SEQ_NEXT_VAL` | `com.salesmanager.core.model.system.optin.Optin` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `ORDERACCOUNTPRODUCT_SEQ_NEXT_VAL` | `com.salesmanager.core.model.order.orderaccount.OrderAccountProduct` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `ORDER_ACCOUNT_ID_NEXT_VALUE` | `com.salesmanager.core.model.order.orderaccount.OrderAccount` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `ORDER_ATTR_ID_NEXT_VALUE` | `com.salesmanager.core.model.order.attributes.OrderAttribute` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `ORDER_ID_SEQ_NEXT_VAL` | `com.salesmanager.core.model.order.Order` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `ORDER_PRD_PRICE_ID_NEXT_VAL` | `com.salesmanager.core.model.order.orderproduct.OrderProductPrice` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `ORDER_PRODUCT_ATTR_ID_NEXT_VAL` | `com.salesmanager.core.model.order.orderproduct.OrderProductAttribute` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `ORDER_PRODUCT_DL_ID_NEXT_VALUE` | `com.salesmanager.core.model.order.orderproduct.OrderProductDownload` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `ORDER_PRODUCT_ID_NEXT_VALUE` | `com.salesmanager.core.model.order.orderproduct.OrderProduct` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `ORDER_TOTAL_ID_NEXT_VALUE` | `com.salesmanager.core.model.order.OrderTotal` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `PERMISSION_SEQ_NEXT_VAL` | `com.salesmanager.core.model.user.Permission` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `PRD_TYPE_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.type.ProductType` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `PRD_VAR_IMG_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.variant.ProductVariantImage` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `PRODUCT_ATTR_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.attribute.ProductAttribute` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `PRODUCT_AVAIL_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.availability.ProductAvailability` | 50 | 0 | `2` | first `1,2,3`; boundary `50,51,52`; final `2` | first `100,101,102`; boundary `149,150,151`; final `4` |
| `PRODUCT_DGT_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.file.DigitalProduct` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `PRODUCT_IMG_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.image.ProductImage` | 50 | 0 | `2` | first `1,2,3`; boundary `50,51,52`; final `2` | first `100,101,102`; boundary `149,150,151`; final `4` |
| `PRODUCT_OPTION_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.attribute.ProductOption` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `PRODUCT_OPT_SET_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.attribute.ProductOptionSet` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `PRODUCT_OPT_VAL_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `PRODUCT_PRICE_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.price.ProductPrice` | 50 | 0 | `2` | first `1,2,3`; boundary `50,51,52`; final `2` | first `100,101,102`; boundary `149,150,151`; final `4` |
| `PRODUCT_RELATION_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.relationship.ProductRelationship` | 50 | 0 | `2` | first `1,2,3`; boundary `50,51,52`; final `2` | first `100,101,102`; boundary `149,150,151`; final `4` |
| `PRODUCT_REVIEW_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.review.ProductReview` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `PRODUCT_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.Product` | 50 | 0 | `2` | first `1,2,3`; boundary `50,51,52`; final `2` | first `100,101,102`; boundary `149,150,151`; final `4` |
| `PRODUCT_VARIN_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.variation.ProductVariation` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `PRODUCT_VAR_GROUP_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.variant.ProductVariantGroup` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `PRODUCT_VAR_SEQ_NEXT_VAL` | `com.salesmanager.core.model.catalog.product.variant.ProductVariant` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `SHIP_QUOTE_ID_NEXT_VALUE` | `com.salesmanager.core.model.shipping.Quote` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `SHP_CRT_ATTR_ITM_SEQ_NEXT_VAL` | `com.salesmanager.core.model.shoppingcart.ShoppingCartAttributeItem` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `SHP_CRT_ITM_SEQ_NEXT_VAL` | `com.salesmanager.core.model.shoppingcart.ShoppingCartItem` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `SHP_CRT_SEQ_NEXT_VAL` | `com.salesmanager.core.model.shoppingcart.ShoppingCart` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `SHP_ORIG_SEQ_NEXT_VAL` | `com.salesmanager.core.model.shipping.ShippingOrigin` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `STATUS_HIST_ID_NEXT_VALUE` | `com.salesmanager.core.model.order.orderstatus.OrderStatusHistory` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `STORE_SEQ_NEXT_VAL` | `com.salesmanager.core.model.merchant.MerchantStore` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `SYST_CONF_SEQ_NEXT_VAL` | `com.salesmanager.core.model.system.SystemConfiguration` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `SYST_NOTIF_SEQ_NEXT_VAL` | `com.salesmanager.core.model.system.SystemNotification` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `TAX_RATE_ID_NEXT_VALUE` | `com.salesmanager.core.model.tax.taxrate.TaxRate` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `TRANSACT_SEQ_NEXT_VAL` | `com.salesmanager.core.model.payments.Transaction` | 50 | 0 | — | first `1,2,3`; boundary `50,51,52`; final `2` | first `1,2,3`; boundary `50,51,52`; final `2` |
| `TX_CLASS_SEQ_NEXT_VAL` | `com.salesmanager.core.model.tax.taxclass.TaxClass` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `USER_SEQ_NEXT_VAL` | `com.salesmanager.core.model.user.User` | 50 | 0 | `1` | first `1,2,3`; boundary `50,51,52`; final `2` | first `50,51,52`; boundary `99,100,101`; final `3` |
| `ZONE_SEQ_NEXT_VAL` | `com.salesmanager.core.model.reference.zone.Zone` | 50 | 0 | `5` | first `1,2,3`; boundary `50,51,52`; final `2` | first `250,251,252`; boundary `299,300,301`; final `7` |
| `category_description_seq` | `com.salesmanager.core.model.catalog.category.CategoryDescription` | 1 | 2000 | `4` | first `1,2,3`; boundary `1,2,3`; final `4` | first `4,5,6`; boundary `4,5,6`; final `7` |
| `content_description_seq` | `com.salesmanager.core.model.content.ContentDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `country_description_seq` | `com.salesmanager.core.model.reference.country.CountryDescription` | 1 | 2000 | `712` | first `1,2,3`; boundary `1,2,3`; final `4` | first `712,713,714`; boundary `712,713,714`; final `715` |
| `custome_review_description_seq` | `com.salesmanager.core.model.customer.review.CustomerReviewDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `customer_option_description_seq` | `com.salesmanager.core.model.customer.attribute.CustomerOptionDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `customer_option_value_description_seq` | `com.salesmanager.core.model.customer.attribute.CustomerOptionValueDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `geozone_description_seq` | `com.salesmanager.core.model.reference.geozone.GeoZoneDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `manufacturer_description_seq` | `com.salesmanager.core.model.catalog.product.manufacturer.ManufacturerDescription` | 1 | 2000 | `2` | first `1,2,3`; boundary `1,2,3`; final `4` | first `2,3,4`; boundary `2,3,4`; final `5` |
| `product_description_seq` | `com.salesmanager.core.model.catalog.product.description.ProductDescription` | 1 | 2000 | `5` | first `1,2,3`; boundary `1,2,3`; final `4` | first `5,6,7`; boundary `5,6,7`; final `8` |
| `product_image_description_seq` | `com.salesmanager.core.model.catalog.product.image.ProductImageDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `product_option_description_seq` | `com.salesmanager.core.model.catalog.product.attribute.ProductOptionDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `product_option_value_description_seq` | `com.salesmanager.core.model.catalog.product.attribute.ProductOptionValueDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `product_price_description_seq` | `com.salesmanager.core.model.catalog.product.price.ProductPriceDescription` | 1 | 2000 | `5` | first `1,2,3`; boundary `1,2,3`; final `4` | first `5,6,7`; boundary `5,6,7`; final `8` |
| `product_review_description_seq` | `com.salesmanager.core.model.catalog.product.review.ProductReviewDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `product_type_description_seq` | `com.salesmanager.core.model.catalog.product.type.ProductTypeDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `product_var_image_desc_seq` | `com.salesmanager.core.model.catalog.product.variant.ProductVariantImageDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `taxrate_description_seq` | `com.salesmanager.core.model.tax.taxrate.TaxRateDescription` | 1 | 2000 | — | first `1,2,3`; boundary `1,2,3`; final `4` | first `1,2,3`; boundary `1,2,3`; final `4` |
| `zone_description_seq` | `com.salesmanager.core.model.reference.zone.ZoneDescription` | 1 | 2000 | `652` | first `1,2,3`; boundary `1,2,3`; final `4` | first `652,653,654`; boundary `652,653,654`; final `655` |

The H5 seed-copy predictions match the runtime IDs recorded during the API check: `CATEGORY_SEQ_NEXT_VAL` → **100**, `PRODUCT_SEQ_NEXT_VAL` → **100**, and `CUSTOMER_SEQ_NEXT_VAL` → **200**.

## Hibernate 6 generator settings

Mismatch counts include any difference in generated IDs or final stored `SEQ_COUNT`. ID offsets below are H6 values relative to H5.

| Optimizer | `stored_last_used` | State | Mismatches / 80 | Mismatch patterns | Example (`SEQ_NAME`; H5 IDs / count → H6 IDs / count) |
| --- | --- | --- | ---: | --- | --- |
| `legacy-hilo` | `true` | Empty | 80 | 60 keys: IDs +50, count unchanged; 20 missing-row description keys: H6 starts at `initialValue=2000` (+2000 IDs), final count +1999. | `CATEGORY_SEQ_NEXT_VAL`; `1,2,3` / 2 → `51,52,53` / 2 |
| `legacy-hilo` | `false` | Empty | 80 | 60 keys: IDs +50, count +1; 20 missing-row description keys: H6 starts at `initialValue=2000` (+2000 IDs), final count +2000. | `CATEGORY_SEQ_NEXT_VAL`; `1,2,3` / 2 → `51,52,53` / 3 |
| `hilo` | `true` | Empty | 20 | 20 missing-row description keys: H6 starts at `initialValue=2000` (+2000 IDs), final count +1999. | `CATALOG_ENT_SEQ_NEXT_VAL`; `1,2,3` / 4 → `2001,2002,2003` / 2003 |
| `hilo` | `false` | Empty | 80 | 60 allocation-50 keys: IDs match, count +1; 20 missing-row description keys: H6 starts at `initialValue=2000` (+2000 IDs), final count +2000. | `CATEGORY_SEQ_NEXT_VAL`; `1,2,3` / 2 → `1,2,3` / 3 |
| `legacy-hilo` | `true` | Seed copy | 80 | 37 keys: IDs +50; 13: +52; 6: +53; 6: +1; 2: +55; 2: +56 (counts unchanged); 14 missing-row description keys: IDs +2000, final count +1999. | `CONTENT_SEQ_NEXT_VAL`; `1,2,3` / 2 → `51,52,53` / 2 |
| `legacy-hilo` | `false` | Seed copy | 74 | 37 keys: IDs +50 and count +1; 13: IDs +1; 6: +2; 2: +4; 2: +5 (counts otherwise match); 14 missing-row description keys: IDs +2000, final count +2000. | `CONTENT_SEQ_NEXT_VAL`; `1,2,3` / 2 → `51,52,53` / 3 |
| `hilo` | `true` | Seed copy | 43 | 29 keys: IDs +1; 14 missing-row description keys: H6 starts at `initialValue=2000` (+2000 IDs), final count +1999; counts otherwise match. | `CATEGORY_SEQ_NEXT_VAL`; `100,101,102` / 4 → `101,102,103` / 4 |
| `hilo` | `false` | Seed copy | 74 | 37 keys: IDs match, count +1; 23: IDs −49, count unchanged; 14 missing-row description keys: IDs +2000, final count +2000. | `CONTENT_SEQ_NEXT_VAL`; `1,2,3` / 2 → `1,2,3` / 3 |

## Implication for AB-544

None of the `legacy-hilo` or `hilo` settings, with either `stored_last_used` value, reproduces H5 IDs and stored counts across both starting states. AB-544 owns the choice between:

- A custom H6 `IdentifierGenerator` that reproduces the legacy formula: first ID is `SEQ_COUNT × allocationSize` (a missing row starts at 1), and `SEQ_COUNT` increases by 1 per allocation.
- The closest tested setting, `hibernate.id.optimizer.pooled.preferred=hilo` with `hibernate.id.generator.stored_last_used=true` (63 mismatches across the 160 row/state comparisons), plus a one-time `SM_SEQUENCER` rebase at cut-over so new IDs remain above every existing ID. This avoids collisions but produces different ID values.

## How this was measured

Measurements used Hibernate 5.4.33 and Hibernate 6.6.5.Final with H2 1.4.200, starting from an empty database and a copy of the seed database. For each persister, `generate()` was called `allocationSize + 2` times; generated IDs and final `SM_SEQUENCER.SEQ_COUNT` values were compared.
