package com.salesmanager.core.business.repositories.catalog.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.persistence.Query;

import org.junit.Test;

import com.salesmanager.core.model.catalog.product.attribute.AttributeCriteria;

public class ProductRepositoryImplAttributeCriteriaTest {

	@Test
	public void appendsOneCriterionWithIndexedParameterNames() {
		StringBuilder query = new StringBuilder();

		ProductRepositoryImpl.appendAttributeCriteria(query,
				Arrays.asList(attribute("COLOR", "Red")));

		assertEquals(" and po.code = :attrCode0 and povd.description like :attrVal0", query.toString());
	}

	@Test
	public void appendsTwoCriteriaInIndexOrder() {
		StringBuilder query = new StringBuilder();

		ProductRepositoryImpl.appendAttributeCriteria(query,
				Arrays.asList(attribute("COLOR", "Red"), attribute("SIZE", "Small")));

		assertEquals(" and po.code = :attrCode0 and povd.description like :attrVal0"
				+ " and po.code = :attrCode1 and povd.description like :attrVal1", query.toString());
	}

	@Test
	public void callerValuesNeverAppearInAppendedQuery() {
		List<String> hostileValues = Arrays.asList("SIZE or 1=1", "SIZE') or ('1'='1", "a:b", "x--");

		for (String hostileValue : hostileValues) {
			StringBuilder query = new StringBuilder();
			ProductRepositoryImpl.appendAttributeCriteria(query,
					Arrays.asList(attribute(hostileValue, hostileValue)));

			for (String value : hostileValues) {
				assertFalse(query.toString().contains(value));
			}
		}
	}

	@Test
	public void bindsBothParameterValuesForEachCriterion() {
		Query query = mock(Query.class);
		List<AttributeCriteria> criteria = Arrays.asList(attribute("COLOR", "Red"), attribute("SIZE", "Small"));

		ProductRepositoryImpl.bindAttributeCriteria(query, criteria);

		verify(query).setParameter("attrCode0", "COLOR");
		verify(query).setParameter("attrVal0", "%Red%");
		verify(query).setParameter("attrCode1", "SIZE");
		verify(query).setParameter("attrVal1", "%Small%");
		verifyNoMoreInteractions(query);
	}

	@Test
	public void bindsCodeAndWildcardValueForOneCriterion() {
		Query query = mock(Query.class);

		ProductRepositoryImpl.bindAttributeCriteria(query, Arrays.asList(attribute("COLOR", "Red")));

		verify(query).setParameter("attrCode0", "COLOR");
		verify(query).setParameter("attrVal0", "%Red%");
		verifyNoMoreInteractions(query);
	}

	@Test
	public void emptyCriteriaDoesNotAppendOrBindAnything() {
		StringBuilder queryText = new StringBuilder();
		Query query = mock(Query.class);

		ProductRepositoryImpl.appendAttributeCriteria(queryText, Collections.emptyList());
		ProductRepositoryImpl.bindAttributeCriteria(query, Collections.emptyList());

		assertEquals("", queryText.toString());
		verifyNoMoreInteractions(query);
	}

	private static AttributeCriteria attribute(String code, String value) {
		AttributeCriteria criteria = new AttributeCriteria();
		criteria.setAttributeCode(code);
		criteria.setAttributeValue(value);
		return criteria;
	}
}
