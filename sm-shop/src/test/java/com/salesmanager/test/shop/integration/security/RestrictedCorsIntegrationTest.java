package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.*;

import com.salesmanager.test.shop.common.SecurityHttpTestSupport;
import com.salesmanager.core.business.modules.cms.impl.CacheManager;
import com.salesmanager.core.business.modules.cms.content.infinispan.CmsStaticContentFileManagerImpl;
import com.salesmanager.core.business.modules.cms.product.infinispan.CmsImageFileManagerImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "shopizer.cors.allowed-origin-patterns=https://shop.example.com",
        "db.jdbcUrl=jdbc:h2:mem:RESTRICTED-CORS;AUTOCOMMIT=OFF;NON_KEYWORDS=VALUE;INIT=CREATE SCHEMA IF NOT EXISTS SALESMANAGER" })
class RestrictedCorsIntegrationTest extends SecurityHttpTestSupport {
    private static CacheManager imageCache;
    private static CacheManager contentCache;

    @BeforeAll
    static void preserveCmsSingletons() {
        imageCache = CmsImageFileManagerImpl.getInstance().getCacheManager();
        contentCache = CmsStaticContentFileManagerImpl.getInstance().getCacheManager();
    }

    @AfterAll
    static void restoreCmsSingletons() {
        // CMS factories share process-wide instances across Spring application contexts.
        if (imageCache != null) {
            CmsImageFileManagerImpl.getInstance().setCacheManager(imageCache);
        }
        if (contentCache != null) {
            CmsStaticContentFileManagerImpl.getInstance().setCacheManager(contentCache);
        }
    }

    @Test
    void onlyConfiguredOriginCanPreflightProtectedPaths() {
        for (String path : new String[] { "/api/v1/private/user/profile", "/api/v1/auth/customer/profile" }) {
            ResponseEntity<String> allowed = preflight(path, "https://shop.example.com");
            assertEquals(200, allowed.getStatusCode().value(), allowed.toString());
            assertEquals("https://shop.example.com", allowed.getHeaders().getAccessControlAllowOrigin());
            ResponseEntity<String> denied = preflight(path, "http://evil.example");
            assertEquals(403, denied.getStatusCode().value(), denied.toString());
            assertNull(denied.getHeaders().getAccessControlAllowOrigin());
        }
    }
}
