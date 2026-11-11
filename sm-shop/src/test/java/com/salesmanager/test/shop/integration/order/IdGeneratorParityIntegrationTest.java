package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.id.MultipleHiLoPerTableGenerator;
import org.hibernate.id.enhanced.LegacyHiLoAlgorithmOptimizer;
import org.hibernate.id.enhanced.NoopOptimizer;
import org.hibernate.id.enhanced.PooledOptimizer;
import org.hibernate.id.enhanced.PooledLoOptimizer;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.type.LongType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.model.catalog.catalog.Catalog;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.catalog.product.manufacturer.ManufacturerDescription;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class IdGeneratorParityIntegrationTest extends ServicesTestSupport {
    @Autowired private EntityManagerFactory emf;
    @Autowired private Environment environment;

    private Map<String, Long> sequencers(Session session) {
        Map<String, Long> rows = new TreeMap<>();
        for (Object value : session.createNativeQuery("select SEQ_NAME, SEQ_COUNT from SALESMANAGER.SM_SEQUENCER order by SEQ_NAME").getResultList()) {
            Object[] row = (Object[]) value;
            rows.put((String) row[0], ((Number) row[1]).longValue());
        }
        return rows;
    }

    @Test
    void enhancedTableGeneratorUsesPooledUnlessExplicitlyOverridden() {
        SessionFactoryImplementor factory = emf.unwrap(SessionFactoryImplementor.class);
        Properties parameters = new Properties();
        parameters.setProperty("increment_size", "50");
        TableGenerator generator = new TableGenerator();
        generator.configure(LongType.INSTANCE, parameters, factory.getServiceRegistry());
        assertTrue(generator.getOptimizer() instanceof PooledOptimizer);
        assertEquals(Boolean.TRUE, ReflectionTestUtils.getField(generator, "storeLastUsedValue"));
        parameters.setProperty("hibernate.id.optimizer.pooled.preferred", "pooled-lo");
        generator = new TableGenerator();
        generator.configure(LongType.INSTANCE, parameters, factory.getServiceRegistry());
        assertTrue(generator.getOptimizer() instanceof PooledLoOptimizer);
        parameters.setProperty("increment_size", "1");
        generator = new TableGenerator();
        generator.configure(LongType.INSTANCE, parameters, factory.getServiceRegistry());
        assertTrue(generator.getOptimizer() instanceof NoopOptimizer);
    }

    @Test
    void recordsEffectiveLegacyGeneratorsAcrossNewSessions() {
        assertEquals("true", environment.getProperty("spring.jpa.hibernate.use-new-id-generator-mappings"));
        SessionFactoryImplementor factory = emf.unwrap(SessionFactoryImplementor.class);
        assertEquals("false", String.valueOf(factory.getProperties().get("hibernate.id.new_generator_mappings")));
        Map<String, Long> before;
        try (Session session = factory.openSession()) {
            before = sequencers(session);
        }
        for (Class<?> type : new Class<?>[] {Product.class, Catalog.class, Manufacturer.class, ManufacturerDescription.class}) {
            Object generator = factory.getMetamodel().entityPersister(type).getIdentifierGenerator();
            assertTrue(generator instanceof MultipleHiLoPerTableGenerator, type.getSimpleName());
            int maxLo = (Integer) ReflectionTestUtils.getField(generator, "maxLo");
            int expectedMaxLo = type == Product.class || type == Manufacturer.class ? 49 : 0;
            assertEquals(expectedMaxLo, maxLo);
            String segment = (String) ReflectionTestUtils.getField(generator, "segmentName");
            LegacyHiLoAlgorithmOptimizer optimizer = (LegacyHiLoAlgorithmOptimizer) ReflectionTestUtils.getField(generator, "hiloOptimizer");
            assertEquals(maxLo > 0, optimizer != null);
            Long previousId = null;
            for (int batch = 0; batch < 2; batch++) {
                try (Session session = factory.openSession()) {
                    Transaction transaction = session.beginTransaction();
                    try {
                        MerchantStore store = session.createQuery("from MerchantStore where code='DEFAULT'", MerchantStore.class).getSingleResult();
                        Language language = session.createQuery("from Language where code='en'", Language.class).getSingleResult();
                        List<Long> ids = new ArrayList<>();
                        List<Long> counters = new ArrayList<>();
                        Long initialCounter = sequencers(session).get(segment);
                        for (int index = 0; index < 3; index++) {
                            Object entity;
                            String code = "id_parity_" + batch + "_" + index;
                            if (type == Product.class) {
                                Product product = new Product();
                                product.setSku(code);
                                product.setMerchantStore(store);
                                entity = product;
                            } else if (type == Catalog.class) {
                                Catalog catalog = new Catalog();
                                catalog.setCode(code);
                                catalog.setMerchantStore(store);
                                entity = catalog;
                            } else if (type == Manufacturer.class) {
                                Manufacturer manufacturer = new Manufacturer();
                                manufacturer.setCode(code);
                                manufacturer.setMerchantStore(store);
                                entity = manufacturer;
                            } else {
                                Manufacturer manufacturer = new Manufacturer();
                                manufacturer.setCode(code);
                                manufacturer.setMerchantStore(store);
                                session.persist(manufacturer);
                                ManufacturerDescription description = new ManufacturerDescription();
                                description.setName(code);
                                description.setManufacturer(manufacturer);
                                description.setLanguage(language);
                                entity = description;
                            }
                            session.persist(entity);
                            session.flush();
                            long id = ((Number) session.getIdentifier(entity)).longValue();
                            long counter = sequencers(session).get(segment);
                            if (previousId != null) assertEquals(previousId + 1, id);
                            if (maxLo == 0) assertEquals(id + 1, counter);
                            else {
                                assertEquals(optimizer.getLastSourceValue().makeValue().longValue() + 1, counter);
                                assertTrue(id >= (counter - 1) * (maxLo + 1));
                                assertTrue(id <= (counter - 1) * (maxLo + 1) + maxLo);
                            }
                            previousId = id;
                            ids.add(id);
                            counters.add(counter);
                        }
                        System.out.println("ID-PARITY " + type.getSimpleName() + " session=" + (batch + 1) + " segment=" + segment
                                + " maxLo=" + maxLo + " before=" + initialCounter + " ids=" + ids + " afterEach=" + counters);
                    } finally {
                        transaction.rollback();
                    }
                }
            }
        }
        try (Session session = factory.openSession()) {
            Map<String, Long> after = sequencers(session);
            java.util.Set<String> keys = new java.util.TreeSet<>(before.keySet());
            keys.addAll(after.keySet());
            for (String key : keys) System.out.println("ID-ROW " + key + " " + before.get(key) + " -> " + after.get(key));
        }
    }
}
