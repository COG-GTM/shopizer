package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Pins today's Hibernate 5 generator behavior for the AB-544 cut-over.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class Hibernate5GeneratorConfigurationTest extends ServicesTestSupport {

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
