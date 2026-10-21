package com.salesmanager.test.shop.integration.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.aop.framework.Advised;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.repositories.catalog.category.CategoryRepository;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepository;
import com.salesmanager.core.business.repositories.customer.CustomerRepository;
import com.salesmanager.core.business.repositories.merchant.MerchantRepository;
import com.salesmanager.core.business.repositories.order.OrderRepository;
import com.salesmanager.core.business.repositories.support.NewEntityIdNormalizingRepository;
import com.salesmanager.core.model.customer.connection.UserConnection;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RepositoryBaseClassWiringIntegrationTest extends ServicesTestSupport {

	@Inject
	private ProductRepository productRepository;

	@Inject
	private CategoryRepository categoryRepository;

	@Inject
	private CustomerRepository customerRepository;

	@Inject
	private MerchantRepository merchantRepository;

	@Inject
	private OrderRepository orderRepository;

	@Inject
	private EntityManagerFactory entityManagerFactory;

	@Inject
	private DataSource dataSource;

	@Test
	public void repositoriesUseNormalizingBaseAndUserConnectionHasItsOwnTable() throws Exception {
		assertNormalizingBase(productRepository);
		assertNormalizingBase(categoryRepository);
		assertNormalizingBase(customerRepository);
		assertNormalizingBase(merchantRepository);
		assertNormalizingBase(orderRepository);

		SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
		AbstractEntityPersister persister = (AbstractEntityPersister) sessionFactory.getMetamodel()
				.entityPersister(UserConnection.class);
		assertTrue(persister.getTableName().endsWith("USERCONNECTION"));

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Integer tableCount = jdbcTemplate.queryForObject(
				"select count(*) from INFORMATION_SCHEMA.TABLES where TABLE_NAME='USERCONNECTION'", Integer.class);
		assertEquals(Integer.valueOf(1), tableCount);
	}

	private void assertNormalizingBase(Object repository) throws Exception {
		Object target = ((Advised) repository).getTargetSource().getTarget();
		assertTrue(target instanceof NewEntityIdNormalizingRepository);
	}
}
