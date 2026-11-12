package com.salesmanager.core.business.configuration;

import java.util.Properties;

import org.hibernate.id.enhanced.StandardOptimizerDescriptor;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

/**
 * Hibernate 6 {@link TableGenerator} configured to reproduce the Hibernate 5 legacy
 * {@code MultipleHiLoPerTableGenerator} that this application used through
 * {@code hibernate.id.new_generator_mappings=false}.
 *
 * <p>The JPA {@code @TableGenerator} annotations are left untouched; this class translates
 * their parameters into the legacy algorithm documented in {@code docs/upgrade/id-generation.md}:
 * <ul>
 * <li>allocation size {@code n > 1}: {@code legacy-hilo} optimizer with {@code increment_size = n - 1}
 * (the legacy {@code max_lo}), so IDs are {@code source * n + lo}, the SM_SEQUENCER row advances by one
 * and a missing row starts at {@code 0};</li>
 * <li>allocation size {@code 1}: no optimizer, the row value is the ID and a missing row starts at
 * {@code 1} (legacy skipped id 0).</li>
 * </ul>
 * The annotated {@code initialValue} (2000) was never effective with the legacy generator and is
 * therefore ignored, like before.
 */
public class LegacyHiLoTableGenerator extends TableGenerator {

	@Override
	public void configure(Type type, Properties parameters, ServiceRegistry serviceRegistry) {
		Properties legacyParameters = new Properties();
		legacyParameters.putAll(parameters);
		int allocationSize = Integer.parseInt(parameters.getProperty(INCREMENT_PARAM, String.valueOf(DEFAULT_INCREMENT_SIZE)));
		if (allocationSize > 1) {
			legacyParameters.setProperty(OPT_PARAM, StandardOptimizerDescriptor.LEGACY_HILO.getExternalName());
			legacyParameters.setProperty(INCREMENT_PARAM, String.valueOf(allocationSize - 1));
			legacyParameters.setProperty(INITIAL_PARAM, "0");
		} else {
			legacyParameters.setProperty(OPT_PARAM, StandardOptimizerDescriptor.NONE.getExternalName());
			legacyParameters.setProperty(INCREMENT_PARAM, "1");
			legacyParameters.setProperty(INITIAL_PARAM, "1");
		}
		super.configure(type, legacyParameters, serviceRegistry);
	}
}
