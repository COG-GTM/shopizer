package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.shop.model.catalog.product.attribute.api.PersistableProductOptionEntity;
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductOptionEntity;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.ReadableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.type.PersistableProductType;
import com.salesmanager.shop.model.catalog.product.type.ProductTypeDescription;
import com.salesmanager.shop.model.catalog.product.type.ReadableProductType;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ClientSuppliedIdIntegrationTest extends ServicesTestSupport {
    private static final String SETS = "/api/v1/private/product/property/set";
    private static final String TYPES = "/api/v1/private/product/type";

    @Test
    public void productTypeCreateDoesNotOverwriteBodyId() {
        String codeA = uniqueCode();
        Long idA = createType(codeA, null);
        String codeB = uniqueCode();
        Long idB = createType(codeB, idA);
        assertNotEquals(idA, idB);
        assertEquals(codeA, getType(idA).getCode());
        assertEquals(codeB, getType(idB).getCode());
    }

    @Test
    public void optionSetUpdateUsesPathIdAndLeavesBodyIdRowUntouched() {
        Long option = createOption();
        String victimCode = uniqueCode();
        String targetCode = uniqueCode();
        ReadableProductOptionSet victim = createSet(victimCode, option);
        ReadableProductOptionSet target = createSet(targetCode, option);
        PersistableProductOptionSet body = new PersistableProductOptionSet();
        body.setId(victim.getId());
        body.setCode(uniqueCode());
        body.setOption(option);
        body.setReadOnly(true);
        ResponseEntity<Void> response = testRestTemplate.exchange(SETS + "/" + target.getId(),
                HttpMethod.PUT, new HttpEntity<>(body, getHeader()), Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ReadableProductOptionSet victimAfter = getSet(victim.getId());
        assertEquals(victimCode, victimAfter.getCode());
        assertFalse(victimAfter.isReadOnly());
        ReadableProductOptionSet targetAfter = getSet(target.getId());
        assertEquals(targetCode, targetAfter.getCode());
        assertTrue(targetAfter.isReadOnly());
    }

    private String uniqueCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Long createType(String code, Long id) {
        PersistableProductType type = new PersistableProductType();
        type.setId(id);
        type.setCode(code);
        ProductTypeDescription description = new ProductTypeDescription();
        description.setLanguage("en");
        description.setName(code);
        type.setDescriptions(Collections.singletonList(description));
        ResponseEntity<Entity> response = testRestTemplate.postForEntity(TYPES,
                new HttpEntity<>(type, getHeader()), Entity.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        return response.getBody().getId();
    }

    private ReadableProductType getType(Long id) {
        ResponseEntity<ReadableProductType> response = testRestTemplate.exchange(TYPES + "/" + id,
                HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableProductType.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private Long createOption() {
        String code = uniqueCode();
        PersistableProductOptionEntity option = new PersistableProductOptionEntity();
        option.setCode(code);
        option.setType("select");
        ProductOptionDescription description = new ProductOptionDescription();
        description.setLanguage("en");
        description.setName(code);
        option.setDescriptions(Collections.singletonList(description));
        ResponseEntity<ReadableProductOptionEntity> response = testRestTemplate.postForEntity(
                "/api/v1/private/product/option", new HttpEntity<>(option, getHeader()),
                ReadableProductOptionEntity.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        return response.getBody().getId();
    }

    private ReadableProductOptionSet createSet(String code, Long option) {
        PersistableProductOptionSet body = new PersistableProductOptionSet();
        body.setCode(code);
        body.setOption(option);
        ResponseEntity<Void> response = testRestTemplate.postForEntity(SETS,
                new HttpEntity<>(body, getHeader()), Void.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<ReadableProductOptionSet[]> list = testRestTemplate.exchange(SETS, HttpMethod.GET,
                new HttpEntity<>(getHeader()), ReadableProductOptionSet[].class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertNotNull(list.getBody());
        return Arrays.stream(list.getBody()).filter(set -> code.equals(set.getCode())).findFirst()
                .orElseThrow(() -> new AssertionError("Created option set missing: " + code));
    }

    private ReadableProductOptionSet getSet(Long id) {
        ResponseEntity<ReadableProductOptionSet> response = testRestTemplate.exchange(SETS + "/" + id,
                HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableProductOptionSet.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }
}
