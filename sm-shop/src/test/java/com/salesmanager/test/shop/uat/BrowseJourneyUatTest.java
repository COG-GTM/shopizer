package com.salesmanager.test.shop.uat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class BrowseJourneyUatTest extends ServicesTestSupport {

    @Test
    public void browseJourney() {
        ReadableProduct product = sampleProduct("BrowseJourneyUatTest");

        ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
                "/api/v1/store/DEFAULT", ReadableMerchantStore.class);
        assertThat(store.getStatusCode(), is(HttpStatus.OK));
        assertThat(store.getBody().getCode(), is("DEFAULT"));

        ResponseEntity<ReadableCategoryList> categories = testRestTemplate.getForEntity(
                "/api/v1/category?store=DEFAULT&lang=en", ReadableCategoryList.class);
        assertThat(categories.getStatusCode(), is(HttpStatus.OK));
        assertThat(categories.getBody().getCategories().isEmpty(), is(false));

        ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
                "/api/v1/products?store=DEFAULT&lang=en", ReadableProductList.class);
        assertThat(products.getStatusCode(), is(HttpStatus.OK));
        assertThat(products.getBody().getRecordsTotal() >= 1, is(true));
        assertThat(products.getBody().getProducts().stream()
                .anyMatch(item -> product.getSku().equals(item.getSku())), is(true));
    }
}
