package com.salesmanager.test.user;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import com.salesmanager.core.business.repositories.user.UserRepository;
import com.salesmanager.core.business.services.user.UserServiceImpl;
import com.salesmanager.core.model.user.User;
import com.salesmanager.core.model.common.CredentialsReset;

public class UserResetPasswordServiceTest {
    private UserRepository repository;
    private UserServiceImpl service;
    private User entity;

    @Before
    public void setUp() {
        repository = mock(UserRepository.class);
        service = new UserServiceImpl(repository);
        entity = new User();
        entity.setId(42L);
        entity.setAdminPassword("old");
        entity.setCredentialsResetRequest(new CredentialsReset());
    }

    @Test
    public void matchingTokenChangesPasswordAndSaves() throws Exception {
        when(repository.clearResetPasswordToken(42L, "token")).thenReturn(1);
        assertTrue(service.resetPassword(entity, "token", "encoded"));
        assertEquals("encoded", entity.getAdminPassword());
        assertNull(entity.getCredentialsResetRequest());
        verify(repository).saveAndFlush(entity);
    }

    @Test
    public void staleTokenDoesNotChangeOrSaveEntity() throws Exception {
        CredentialsReset reset = entity.getCredentialsResetRequest();
        when(repository.clearResetPasswordToken(42L, "stale")).thenReturn(0);
        assertFalse(service.resetPassword(entity, "stale", "encoded"));
        assertEquals("old", entity.getAdminPassword());
        assertSame(reset, entity.getCredentialsResetRequest());
        verify(repository, never()).saveAndFlush(any(User.class));
    }
}
