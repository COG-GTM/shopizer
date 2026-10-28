package com.salesmanager.test.customer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import com.salesmanager.core.business.repositories.customer.CustomerRepository;
import com.salesmanager.core.business.services.customer.CustomerServiceImpl;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.common.CredentialsReset;

public class CustomerResetPasswordServiceTest {
    private CustomerRepository repository;
    private CustomerServiceImpl service;
    private Customer entity;

    @Before
    public void setUp() {
        repository = mock(CustomerRepository.class);
        service = new CustomerServiceImpl(repository);
        entity = new Customer();
        entity.setId(42L);
        entity.setPassword("old");
        entity.setCredentialsResetRequest(new CredentialsReset());
    }

    @Test
    public void matchingTokenChangesPasswordAndSaves() throws Exception {
        when(repository.clearResetPasswordToken(42L, "token")).thenReturn(1);
        assertTrue(service.resetPassword(entity, "token", "encoded"));
        assertEquals("encoded", entity.getPassword());
        assertNull(entity.getCredentialsResetRequest());
        verify(repository).saveAndFlush(entity);
    }

    @Test
    public void staleTokenDoesNotChangeOrSaveEntity() throws Exception {
        CredentialsReset reset = entity.getCredentialsResetRequest();
        when(repository.clearResetPasswordToken(42L, "stale")).thenReturn(0);
        assertFalse(service.resetPassword(entity, "stale", "encoded"));
        assertEquals("old", entity.getPassword());
        assertSame(reset, entity.getCredentialsResetRequest());
        verify(repository, never()).saveAndFlush(any(Customer.class));
    }
}
