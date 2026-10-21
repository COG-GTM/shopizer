package com.salesmanager.test.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.Test;

import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

/**
 * Pins today's Hibernate 5 generator behavior for the AB-544 cut-over.
 */
public class Hibernate5GeneratorConfigurationTest extends AbstractSalesManagerCoreTestCase {

	@Inject
	private EntityManagerFactory entityManagerFactory;

	@Test
	public void productIdentifierGeneratorUsesLegacyConfiguration() {
		SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
		AbstractEntityPersister persister = (AbstractEntityPersister) sessionFactory.getMetamodel()
				.entityPersister(Product.class);
		assertNotNull(persister.getIdentifierGenerator());
		assertEquals("org.hibernate.id.MultipleHiLoPerTableGenerator",
				persister.getIdentifierGenerator().getClass().getName());
		assertEquals("false", String.valueOf(
				entityManagerFactory.getProperties().get("hibernate.id.new_generator_mappings")));
	}
}
