package com.salesmanager.core.business.configuration;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.jpa.spi.IdentifierGeneratorStrategyProvider;

/**
 * Routes every JPA {@code GenerationType.TABLE} generator to {@link LegacyHiLoTableGenerator}.
 * Registered through {@code hibernate.identifier_generator_strategy_provider} in {@link DataConfiguration}.
 */
@SuppressWarnings({"deprecation", "removal"})
public class LegacyIdentifierGeneratorStrategyProvider implements IdentifierGeneratorStrategyProvider {

	@Override
	public Map<String, Class<?>> getStrategies() {
		Map<String, Class<?>> strategies = new HashMap<>();
		strategies.put("enhanced-table", LegacyHiLoTableGenerator.class);
		strategies.put(TableGenerator.class.getName(), LegacyHiLoTableGenerator.class);
		return strategies;
	}
}
