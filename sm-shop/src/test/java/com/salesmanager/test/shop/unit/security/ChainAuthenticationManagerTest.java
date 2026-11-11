package com.salesmanager.test.shop.unit.security;

import com.salesmanager.shop.application.config.MultipleEntryPointsSecurityConfig;
import com.salesmanager.shop.admin.security.WebUserServices;
import com.salesmanager.shop.store.security.admin.JWTAdminServicesImpl;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChainAuthenticationManagerTest {
    @Test
    void managersUseOnlyTheirOwnStoreAndRejectInvalidCredentials() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        UserDetailsService customer = mock(UserDetailsService.class);
        WebUserServices services = mock(WebUserServices.class);
        JWTAdminServicesImpl admin = mock(JWTAdminServicesImpl.class);
        UserDetailsService jwtCustomer = mock(UserDetailsService.class);
        UserDetailsService[] stores = {customer, services, admin, jwtCustomer};
        MultipleEntryPointsSecurityConfig.CustomerConfiguration shop = new MultipleEntryPointsSecurityConfig.CustomerConfiguration();
        MultipleEntryPointsSecurityConfig.ServicesApiConfiguration legacy = new MultipleEntryPointsSecurityConfig.ServicesApiConfiguration();
        MultipleEntryPointsSecurityConfig.UserApiConfiguration users = new MultipleEntryPointsSecurityConfig.UserApiConfiguration();
        MultipleEntryPointsSecurityConfig.CustomeApiConfiguration customers = new MultipleEntryPointsSecurityConfig.CustomeApiConfiguration();
        ReflectionTestUtils.setField(shop, "customerDetailsService", customer);
        ReflectionTestUtils.setField(legacy, "userDetailsService", services);
        ReflectionTestUtils.setField(users, "jwtUserDetailsService", admin);
        ReflectionTestUtils.setField(customers, "jwtCustomerDetailsService", jwtCustomer);
        AuthenticationManager[] managers = {shop.customerAuthenticationManager(encoder), legacy.servicesAuthenticationManager(encoder),
                users.jwtAdminAuthenticationManager(encoder), customers.jwtCustomerAuthenticationManager(encoder)};
        for (int i = 0; i < managers.length; i++) {
            assertInstanceOf(ProviderManager.class, managers[i]);
            ProviderManager manager = (ProviderManager) managers[i];
            assertNull(ReflectionTestUtils.getField(manager, "parent"));
            assertEquals(i == 2 ? 2 : 1, manager.getProviders().size());
            assertEquals(DaoAuthenticationProvider.class, manager.getProviders().get(i == 2 ? 1 : 0).getClass());
            if (i == 2) {
                assertEquals(JWTAdminAuthenticationProvider.class, manager.getProviders().get(0).getClass());
                ReflectionTestUtils.setField(manager.getProviders().get(0), "jwtAdminDetailsService", admin);
                ReflectionTestUtils.setField(manager.getProviders().get(0), "passwordEncoder", encoder);
            }
            when(stores[i].loadUserByUsername("known")).thenReturn(User.withUsername("known")
                    .password(encoder.encode("secret")).roles("AUTH").build());
            when(stores[i].loadUserByUsername("missing")).thenThrow(new UsernameNotFoundException("missing"));
            clearInvocations(stores);
            assertTrue(manager.authenticate(new UsernamePasswordAuthenticationToken("known", "secret")).isAuthenticated());
            assertThrows(BadCredentialsException.class, () -> manager.authenticate(new UsernamePasswordAuthenticationToken("known", "wrong")));
            assertThrows(BadCredentialsException.class, () -> manager.authenticate(new UsernamePasswordAuthenticationToken("missing", "secret")));
            for (int j = 0; j < stores.length; j++) if (i != j) verifyNoInteractions(stores[j]);
        }
        assertInstanceOf(JWTAdminAuthenticationProvider.class, users.adminAuthenticationProvider());
        assertInstanceOf(JWTCustomerAuthenticationProvider.class, customers.customerAuthenticationProvider());
    }

    @Test
    void globalParentCannotFallThroughToAnotherChain() {
        AuthenticationManagerBuilder builder = mock(AuthenticationManagerBuilder.class);
        new MultipleEntryPointsSecurityConfig().configureGlobalAuthentication(builder);
        ArgumentCaptor<AuthenticationManager> parent = ArgumentCaptor.forClass(AuthenticationManager.class);
        verify(builder).parentAuthenticationManager(parent.capture());
        assertThrows(ProviderNotFoundException.class, () -> parent.getValue().authenticate(
                new UsernamePasswordAuthenticationToken("any", "password")));
    }
}
