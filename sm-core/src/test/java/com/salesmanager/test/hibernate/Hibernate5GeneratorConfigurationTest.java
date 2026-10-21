package com.salesmanager.test.hibernate;

import static org.junit.Assert.assertNotNull;

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.Test;

import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

public class Hibernate5GeneratorConfigurationTest extends AbstractSalesManagerCoreTestCase {

	@Inject
	private EntityManagerFactory entityManagerFactory;

	@Test
	public void reportProductIdentifierGenerator() {
		SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
		AbstractEntityPersister persister = (AbstractEntityPersister) sessionFactory.getMetamodel()
				.entityPersister(Product.class);
		assertNotNull(persister.getIdentifierGenerator());
		System.out.println("sm-core Product identifier generator: "
				+ persister.getIdentifierGenerator().getClass().getName());
		System.out.println("sm-core hibernate.id.new_generator_mappings: "
				+ entityManagerFactory.getProperties().get("hibernate.id.new_generator_mappings"));
	}
}
