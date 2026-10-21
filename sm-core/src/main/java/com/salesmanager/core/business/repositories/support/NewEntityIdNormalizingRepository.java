package com.salesmanager.core.business.repositories.support;

import java.io.Serializable;

import javax.persistence.EntityManager;

import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import com.salesmanager.core.model.generic.SalesManagerEntity;

/**
 * Hibernate 6.6 treats a non-null id as detached; API DTOs default id to 0.
 */
@NoRepositoryBean
public class NewEntityIdNormalizingRepository<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> {

	public NewEntityIdNormalizingRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
		super(entityInformation, entityManager);
	}

	@Override
	public <S extends T> S save(S entity) {
		if (entity instanceof SalesManagerEntity<?, ?>) {
			SalesManagerEntity<?, ?> salesManagerEntity = (SalesManagerEntity<?, ?>) entity;
			Object id = salesManagerEntity.getId();
			if (id instanceof Number && ((Number) id).longValue() == 0L) {
				salesManagerEntity.setId(null);
			}
		}
		return super.save(entity);
	}
}
