package com.salesmanager.test.shop.integration.search;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.springframework.http.HttpStatus.CREATED;

import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.SearchProductList;
import com.salesmanager.shop.model.catalog.SearchProductRequest;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@Disabled("Requires an Elasticsearch server")
public class SearchApiIntegrationTest extends ServicesTestSupport {

    @Autowired
    private TestRestTemplate testRestTemplate;



    /**
     * Add a product then search for it
     * This tests is disabled since it requires Elastic search server started
     *
     * @throws Exception
     */
    //@Test
    @Disabled("Requires an Elasticsearch server")
    public void searchItem() throws Exception {
    	
    	PersistableProduct product = super.product("TESTPRODUCT");
    	
        final HttpEntity<PersistableProduct> entity = new HttpEntity<>(product, getHeader());

        final ResponseEntity<PersistableProduct> response = testRestTemplate.postForEntity("/api/v1/private/product?store=" + Constants.DEFAULT_STORE, entity, PersistableProduct.class);
        assertThat(response.getStatusCode(), is(CREATED));
        
        SearchProductRequest searchRequest = new SearchProductRequest();
        searchRequest.setQuery("TEST");
        final HttpEntity<SearchProductRequest> searchEntity = new HttpEntity<>(searchRequest, getHeader());
        
        
        final ResponseEntity<SearchProductList> searchResponse = testRestTemplate.postForEntity("/api/v1/search?store=" + Constants.DEFAULT_STORE, searchEntity, SearchProductList.class);
        assertThat(searchResponse.getStatusCode(), is(CREATED));

    }


}