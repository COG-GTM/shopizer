package com.salesmanager.test.shop.integration.security;

import com.salesmanager.shop.application.ShopApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityContextStructureTest {
    @Autowired private ApplicationContext context;

    @Test
    void fourOrderedChainsHaveIsolatedManagersAndTwoIgnoringCustomizers() {
        assertEquals(4, context.getBeansOfType(SecurityFilterChain.class).size());
        assertEquals(2, context.getBeansOfType(WebSecurityCustomizer.class).size());
        assertTrue(context.getBeansOfType(WebSecurityConfigurerAdapter.class).isEmpty());
        Object shop = context.getBean("customerAuthenticationManager");
        Object admin = context.getBean("jwtAdminAuthenticationManager");
        Object customer = context.getBean("jwtCustomerAuthenticationManager");
        assertNotSame(shop, admin);
        assertNotSame(shop, customer);
        assertNotSame(admin, customer);
        FilterChainProxy proxy = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
        String[] patterns = {"/shop/**", "/services/**", "/api/v*/private/**", "/api/v*/auth/**"};
        int offset = proxy.getFilterChains().size() - patterns.length;
        assertTrue(offset > 0);
        for (int i = 0; i < offset; i++) assertTrue(proxy.getFilterChains().get(i).getFilters().isEmpty());
        for (int i = 0; i < patterns.length; i++) {
            DefaultSecurityFilterChain chain = (DefaultSecurityFilterChain) proxy.getFilterChains().get(offset + i);
            assertEquals(patterns[i], ((AntPathRequestMatcher) chain.getRequestMatcher()).getPattern());
            assertFalse(chain.getFilters().isEmpty());
        }
    }
}
