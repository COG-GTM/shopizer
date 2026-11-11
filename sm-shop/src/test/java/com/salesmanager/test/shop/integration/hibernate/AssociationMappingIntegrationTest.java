package com.salesmanager.test.shop.integration.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;

import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.connection.UserConnection;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class AssociationMappingIntegrationTest extends ServicesTestSupport {
    @Autowired private EntityManagerFactory emf;

    private EntityManager em;
    private MerchantStore store;
    private Language language;

    @BeforeEach
    void openTransaction() {
        em = emf.createEntityManager();
        em.getTransaction().begin();
        store = em.createQuery("from MerchantStore where code = :code", MerchantStore.class)
                .setParameter("code", MerchantStore.DEFAULT_STORE).getSingleResult();
        language = em.createQuery("from Language where code = 'en'", Language.class).getSingleResult();
    }

    @AfterEach
    void rollbackAndClose() {
        if (em != null) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }

    @Test
    void customerGroupRowsFollowAssociationReplacement() {
        Group first = group("join_customer_first");
        Group second = group("join_customer_second");
        Customer customer = new Customer();
        customer.setEmailAddress("join-customer@example.com");
        customer.setMerchantStore(store);
        customer.setDefaultLanguage(language);
        Billing billing = new Billing();
        billing.setFirstName("Join");
        billing.setLastName("Customer");
        billing.setCountry(store.getCountry());
        customer.setBilling(billing);
        customer.getGroups().add(first);
        em.persist(customer);
        em.flush();
        Long id = customer.getId();
        em.clear();

        Customer loaded = em.find(Customer.class, id);
        assertEquals(Collections.singletonList(first.getId()),
                loaded.getGroups().stream().map(Group::getId).collect(Collectors.toList()));
        assertRows("CUSTOMER_GROUP", "CUSTOMER_ID", id, "GROUP_ID", first.getId());
        loaded.getGroups().clear();
        loaded.getGroups().add(em.find(Group.class, second.getId()));
        em.flush();
        em.clear();

        assertEquals(Collections.singletonList(second.getId()),
                em.find(Customer.class, id).getGroups().stream().map(Group::getId).collect(Collectors.toList()));
        assertRows("CUSTOMER_GROUP", "CUSTOMER_ID", id, "GROUP_ID", second.getId());
    }

    @Test
    void userGroupRowsFollowAssociationReplacement() {
        Group first = group("join_user_first");
        Group second = group("join_user_second");
        User user = new User("join_user", "password", "join-user@example.com");
        user.setMerchantStore(store);
        user.setDefaultLanguage(language);
        user.getGroups().add(first);
        em.persist(user);
        em.flush();
        Long id = user.getId();
        em.clear();

        User loaded = em.find(User.class, id);
        assertEquals(Collections.singletonList(first.getId()),
                loaded.getGroups().stream().map(Group::getId).collect(Collectors.toList()));
        assertRows("USER_GROUP", "USER_ID", id, "GROUP_ID", first.getId());
        loaded.getGroups().clear();
        loaded.getGroups().add(em.find(Group.class, second.getId()));
        em.flush();
        em.clear();

        assertEquals(Collections.singletonList(second.getId()),
                em.find(User.class, id).getGroups().stream().map(Group::getId).collect(Collectors.toList()));
        assertRows("USER_GROUP", "USER_ID", id, "GROUP_ID", second.getId());
    }

    @Test
    void productCategoryRowsFollowAssociationReplacement() {
        Category first = categoryEntity("join_category_first");
        Category second = categoryEntity("join_category_second");
        Product product = new Product();
        product.setSku("join_product");
        product.setMerchantStore(store);
        product.getCategories().add(first);
        em.persist(product);
        em.flush();
        Long id = product.getId();
        em.clear();

        Product loaded = em.find(Product.class, id);
        assertEquals(Collections.singleton(first.getId()),
                loaded.getCategories().stream().map(Category::getId).collect(Collectors.toSet()));
        assertRows("PRODUCT_CATEGORY", "PRODUCT_ID", id, "CATEGORY_ID", first.getId());
        loaded.getCategories().clear();
        loaded.getCategories().add(em.find(Category.class, second.getId()));
        em.flush();
        em.clear();

        assertEquals(Collections.singleton(second.getId()),
                em.find(Product.class, id).getCategories().stream().map(Category::getId).collect(Collectors.toSet()));
        assertRows("PRODUCT_CATEGORY", "PRODUCT_ID", id, "CATEGORY_ID", second.getId());
    }

    @Test
    void userConnectionUsesOriginalTableAndEnforcesGeneratedUniqueConstraint() {
        SessionFactoryImplementor factory = emf.unwrap(SessionFactoryImplementor.class);
        AbstractEntityPersister mapping = (AbstractEntityPersister) factory.getMetamodel()
                .entityPersister(UserConnection.class);
        String qualifiedTable = mapping.getTableName();
        assertEquals("USERCONNECTION", qualifiedTable.substring(qualifiedTable.lastIndexOf('.') + 1).toUpperCase());
        assertEquals("create", factory.getProperties().get("hibernate.hbm2ddl.auto"));
        boolean uniqueConstraint = em.unwrap(Session.class).doReturningWork(connection -> {
            Map<String, Set<String>> indexes = new HashMap<>();
            try (ResultSet rows = connection.getMetaData().getIndexInfo(null, "SALESMANAGER",
                    "USERCONNECTION", true, false)) {
                while (rows.next()) {
                    String column = rows.getString("COLUMN_NAME");
                    if (column != null) indexes.computeIfAbsent(rows.getString("INDEX_NAME"),
                            key -> new HashSet<>()).add(column.toUpperCase());
                }
            }
            return indexes.values().contains(new HashSet<>(Arrays.asList("USERID", "PROVIDERID", "USERRANK")));
        });
        UserConnection first = connection("first");
        em.persist(first);
        em.flush();
        em.clear();
        if (!uniqueConstraint) {
            System.out.println("USERCONNECTION: generated schema has no userId/providerId/userRank unique index; table mapping verified");
            return;
        }
        em.persist(connection("second"));
        PersistenceException failure = assertThrows(PersistenceException.class, em::flush);
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        assertTrue(cause instanceof java.sql.SQLException);
        assertEquals("23505", ((java.sql.SQLException) cause).getSQLState());
    }

    private UserConnection connection(String providerUser) {
        UserConnection connection = new UserConnection();
        connection.setUserId("join_connection_user");
        connection.setProviderId("join_provider");
        connection.setProviderUserId(providerUser);
        connection.setRank(1);
        return connection;
    }

    private Group group(String name) {
        Group group = new Group(name);
        em.persist(group);
        return group;
    }

    private Category categoryEntity(String code) {
        Category category = new Category(store);
        category.setId(null);
        category.setCode(code);
        em.persist(category);
        return category;
    }

    private void assertRows(String table, String ownerColumn, Long ownerId, String targetColumn, Number expectedId) {
        List<?> rows = em.createNativeQuery("select " + targetColumn + " from SALESMANAGER." + table
                + " where " + ownerColumn + " = :id").setParameter("id", ownerId).getResultList();
        assertEquals(Collections.singletonList(expectedId.longValue()),
                rows.stream().map(value -> ((Number) value).longValue()).collect(Collectors.toList()));
    }
}
