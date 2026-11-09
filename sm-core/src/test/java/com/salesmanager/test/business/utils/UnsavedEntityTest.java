package com.salesmanager.test.business.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import com.salesmanager.core.business.services.common.generic.SalesManagerEntityServiceImpl;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;

class UnsavedEntityTest {
    private static class Service extends SalesManagerEntityServiceImpl<Long, Manufacturer> {
        Service(JpaRepository<Manufacturer, Long> repository) { super(repository); }
        Manufacturer flush(Manufacturer entity) { return saveAndFlush(entity); }
    }

    @Test void saveClearsZero() throws Exception { check(0L, null, false); }
    @Test void savePreservesExistingId() throws Exception { check(5L, 5L, false); }
    @Test void savePreservesNull() throws Exception { check(null, null, false); }
    @Test void saveAndFlushClearsZero() throws Exception { check(0L, null, true); }
    @Test void saveAndFlushPreservesExistingId() throws Exception { check(5L, 5L, true); }
    @Test void saveAndFlushPreservesNull() throws Exception { check(null, null, true); }

    @SuppressWarnings("unchecked")
    private void check(Long initial, Long expected, boolean flush) throws Exception {
        JpaRepository<Manufacturer, Long> repository = mock(JpaRepository.class);
        Manufacturer entity = new Manufacturer();
        entity.setId(initial);
        when(repository.saveAndFlush(same(entity))).thenAnswer(invocation -> {
            assertEquals(expected, ((Manufacturer) invocation.getArgument(0)).getId());
            return entity;
        });
        Service service = new Service(repository);
        if (flush) assertSame(entity, service.flush(entity));
        else service.save(entity);
        verify(repository).saveAndFlush(same(entity));
        verifyNoMoreInteractions(repository);
    }
}
