package com.salesmanager.test.configuration;

import com.salesmanager.core.business.configuration.LegacyHiLoTableGenerator;
import com.salesmanager.core.business.configuration.LegacyIdentifierGeneratorStrategyProvider;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.id.IdentifierGeneratorHelper;
import org.hibernate.id.enhanced.AccessCallback;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyGeneratorUpgradeTest {
    @Test
    void tableStrategyAliasesUseCompatibilityGenerator() {
        assertEquals(java.util.Map.of("enhanced-table", LegacyHiLoTableGenerator.class,
                TableGenerator.class.getName(), LegacyHiLoTableGenerator.class),
                new LegacyIdentifierGeneratorStrategyProvider().getStrategies());
    }

    @Test
    void legacyBlocksAdvanceSourceOncePerFiftyIdsAndSkipZero() {
        check(50, 0, 1, 120);
        check(50, 7, 350, 120);
    }

    @Test
    void allocationOneUsesSourceAsIdAndIgnoresAnnotatedInitialValue() {
        check(1, 1, 1, 5);
    }

    private void check(int allocation, long sourceStart, long firstId, int count) {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", H2Dialect.class.getName())
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false).build();
        try {
            Properties parameters = new Properties();
            parameters.setProperty(TableGenerator.INCREMENT_PARAM, Integer.toString(allocation));
            parameters.setProperty(TableGenerator.INITIAL_PARAM, "2000");
            LegacyHiLoTableGenerator generator = new LegacyHiLoTableGenerator();
            generator.configure(new TypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.LONG),
                    parameters, registry);
            assertEquals(allocation == 1 ? 1 : 0, generator.getInitialValue());
            AtomicLong source = new AtomicLong(sourceStart);
            AccessCallback callback = new AccessCallback() {
                public org.hibernate.id.IntegralDataTypeHolder getNextValue() {
                    return IdentifierGeneratorHelper.getIntegralDataTypeHolder(Long.class)
                            .initialize(source.getAndIncrement());
                }
                public String getTenantIdentifier() { return null; }
            };
            for (int i = 0; i < count; i++) {
                long id = ((Number) generator.getOptimizer().generate(callback)).longValue();
                assertEquals(firstId + i, id);
                assertEquals(allocation == 1 ? id + 1 : id / allocation + 1, source.get());
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
