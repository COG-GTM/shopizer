package com.salesmanager.test.business.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.salesmanager.core.business.utils.DataUtils;
import com.salesmanager.core.model.merchant.MerchantStore;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DataUtilsParameterizedTest {

	@ParameterizedTest
	@CsvSource({
			"WEIGHT, LB, LB, 100.79",
			"WEIGHT, KG, LB, 221.74",
			"WEIGHT, KG, KG, 100.79",
			"WEIGHT, LB, KG, 45.81",
			"MEASURE, IN, IN, 100.79",
			"MEASURE, CM, IN, 256.00",
			"MEASURE, CM, CM, 100.79",
			"MEASURE, IN, CM, 39.31"
	})
	void convertsWeightAndMeasure(
			String dimension,
			String storeUnit,
			String requestedUnit,
			double expected) {
		MerchantStore store = mock(MerchantStore.class);
		when(store.getWeightunitcode()).thenReturn(storeUnit);
		when(store.getSeizeunitcode()).thenReturn(storeUnit);

		double actual = dimension.equals("WEIGHT")
				? DataUtils.getWeight(100.789, store, requestedUnit)
				: DataUtils.getMeasure(100.789, store, requestedUnit);

		assertEquals(expected, actual, 0.0);
	}
}
