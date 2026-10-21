package com.salesmanager.test.junit5;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoreTestCaseLifecycleTest extends AbstractSalesManagerCoreTestCase {

	private boolean superclassSetupCompleted;

	@BeforeEach
	void recordSuperclassSetup() throws Exception {
		superclassSetupCompleted = merchantService.getByCode(MerchantStore.DEFAULT_STORE) != null
				&& languageService.getByCode(ENGLISH_LANGUAGE_CODE) != null;
	}

	@Test
	void superclassSetupRunsBeforeSubclassSetupAndInjectsServices() {
		assertAll(
				() -> assertTrue(superclassSetupCompleted,
						"Superclass setup must populate the default merchant and English language"),
				() -> assertNotNull(merchantService),
				() -> assertNotNull(languageService));
	}
}
