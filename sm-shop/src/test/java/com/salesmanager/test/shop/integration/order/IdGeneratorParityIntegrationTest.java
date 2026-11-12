package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.generator.Generator;
import org.hibernate.id.enhanced.LegacyHiLoAlgorithmOptimizer;
import org.hibernate.id.enhanced.NoopOptimizer;
import org.hibernate.id.enhanced.Optimizer;
import org.hibernate.id.enhanced.TableGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.LegacyHiLoTableGenerator;
import com.salesmanager.core.model.catalog.catalog.Catalog;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.catalog.product.manufacturer.ManufacturerDescription;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Hibernate 6 parity with the Hibernate 5 legacy MultipleHiLoPerTableGenerator captured in
 * docs/upgrade/id-generation.md (AB-541): same SM_SEQUENCER rows, same ID arithmetic.
 */
public class IdGeneratorParityIntegrationTest extends ServicesTestSupport {
    @Autowired private EntityManagerFactory emf;

    private Map<String, Long> sequencers(Session session) {
        Map<String, Long> rows = new TreeMap<>();
        for (Object[] row : session.createNativeQuery("select SEQ_NAME, SEQ_COUNT from SALESMANAGER.SM_SEQUENCER order by SEQ_NAME", Object[].class).getResultList()) {
            rows.put((String) row[0], ((Number) row[1]).longValue());
        }
        return rows;
    }

    private static int maxLo(TableGenerator generator) {
        Optimizer optimizer = generator.getOptimizer();
        return optimizer instanceof LegacyHiLoAlgorithmOptimizer ? optimizer.getIncrementSize() : 0;
    }

    @Test
    void everyTableGeneratorReproducesLegacyHiLoParameters() {
        SessionFactoryImplementor factory = emf.unwrap(SessionFactoryImplementor.class);
        assertEquals("false", String.valueOf(factory.getProperties().get("hibernate.id.generator.stored_last_used")));
        assertEquals("legacy-hilo", String.valueOf(factory.getProperties().get("hibernate.id.optimizer.pooled.preferred")));
        List<String> segments = new ArrayList<>();
        factory.getMappingMetamodel().forEachEntityDescriptor(descriptor -> {
            Generator generator = descriptor.getGenerator();
            if (!(generator instanceof TableGenerator)) return;
            String entity = descriptor.getEntityName();
            assertTrue(generator instanceof LegacyHiLoTableGenerator, entity);
            TableGenerator tableGenerator = (TableGenerator) generator;
            assertEquals(Boolean.FALSE, ReflectionTestUtils.getField(tableGenerator, "storeLastUsedValue"), entity);
            assertEquals("SM_SEQUENCER", tableGenerator.getTableName(), entity);
            Optimizer optimizer = tableGenerator.getOptimizer();
            if (optimizer instanceof LegacyHiLoAlgorithmOptimizer) {
                // JPA default allocationSize 50 -> legacy max_lo 49, blocks of 50 ids per SM_SEQUENCER increment
                assertEquals(49, optimizer.getIncrementSize(), entity);
                assertEquals(0, tableGenerator.getInitialValue(), entity);
            } else {
                // description generators: allocationSize 1, the row value is the id
                assertTrue(optimizer instanceof NoopOptimizer, entity);
                assertEquals(1, optimizer.getIncrementSize(), entity);
                assertEquals(1, tableGenerator.getInitialValue(), entity);
            }
            segments.add(tableGenerator.getSegmentValue());
        });
        assertTrue(segments.size() >= 60, "table generators found: " + segments.size());
        assertTrue(segments.contains("PRODUCT_SEQ_NEXT_VAL"));
        assertTrue(segments.contains("manufacturer_description_seq"));
    }

    @Test
    void recordsEffectiveLegacyGeneratorsAcrossNewSessions() {
        SessionFactoryImplementor factory = emf.unwrap(SessionFactoryImplementor.class);
        Map<String, Long> before;
        try (Session session = factory.openSession()) {
            before = sequencers(session);
        }
        for (Class<?> type : new Class<?>[] {Product.class, Catalog.class, Manufacturer.class, ManufacturerDescription.class}) {
            Generator rawGenerator = factory.getMappingMetamodel().getEntityDescriptor(type).getGenerator();
            assertTrue(rawGenerator instanceof LegacyHiLoTableGenerator, type.getSimpleName());
            TableGenerator generator = (TableGenerator) rawGenerator;
            int maxLo = maxLo(generator);
            int expectedMaxLo = type == Product.class || type == Manufacturer.class ? 49 : 0;
            assertEquals(expectedMaxLo, maxLo);
            String segment = generator.getSegmentValue();
            Optimizer optimizer = generator.getOptimizer();
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
