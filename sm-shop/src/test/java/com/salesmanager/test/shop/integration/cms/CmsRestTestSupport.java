package com.salesmanager.test.shop.integration.cms;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.modules.cms.impl.CacheManager;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.Pr350IntegrationSupport;

abstract class CmsRestTestSupport extends Pr350IntegrationSupport {
    @Autowired @Qualifier("infinispanProductAssetsManager") CacheManager cache;

    protected ResponseEntity<String> upload(String endpoint, String name, byte[] bytes, String type) {
        HttpHeaders headers = getHeader();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(type));
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return name; }
        };
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new HttpEntity<>(resource, partHeaders));
        return testRestTemplate.postForEntity(endpoint, new HttpEntity<>(parts, headers), String.class);
    }

}
