package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.core.model.catalog.product.variation.ProductVariation;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.product.facade.ProductVariationFacade;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@Transactional
public class ProductVariationIdentityIntegrationTest extends ServicesTestSupport {

	@PersistenceContext private EntityManager entityManager;
	@Autowired private ProductVariationFacade facade;
	@Autowired private MerchantStoreService merchantService;
	@Autowired private LanguageService languageService;

	private MerchantStore owner;
	private MerchantStore other;
	private Language language;
	private ProductOption option;
	private ProductOptionValue value;
	private ProductVariation original;

	@Before
	public void setUpVariations() throws Exception {
		owner = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		language = languageService.getByCode("en");
		other = new MerchantStore();
		other.setCode("IDENTITY_OTHER");
		other.setStorename("Other variation store");
		other.setStorephone("1234567890");
		other.setStorecity("Montreal");
		other.setStorepostalcode("H1H1H1");
		other.setStoreEmailAddress("identity@example.com");
		other.setCountry(owner.getCountry());
		other.setCurrency(owner.getCurrency());
		other.setDefaultLanguage(language);
		other.getLanguages().add(language);
		entityManager.persist(other);
		ProductOption ownerOption = option(owner);
		ProductOptionValue ownerValue = value(owner);
		original = variation("original", owner, ownerOption, ownerValue);
		option = option(other);
		value = value(other);
		entityManager.flush();
	}

	private ProductOption option(MerchantStore store) {
		ProductOption result = new ProductOption();
		result.setMerchantStore(store);
		result.setCode("identity_option");
		ProductOptionDescription description = new ProductOptionDescription();
		description.setProductOption(result);
		description.setLanguage(language);
		description.setName("Identity option");
		result.getDescriptions().add(description);
		entityManager.persist(result);
		return result;
	}

	private ProductOptionValue value(MerchantStore store) {
		ProductOptionValue result = new ProductOptionValue();
		result.setMerchantStore(store);
		result.setCode("identity_value");
		ProductOptionValueDescription description = new ProductOptionValueDescription();
		description.setProductOptionValue(result);
		description.setLanguage(language);
		description.setName("Identity value");
		result.getDescriptions().add(description);
		entityManager.persist(result);
		return result;
	}

	private ProductVariation variation(String code, MerchantStore store, ProductOption opt, ProductOptionValue val) {
		ProductVariation result = new ProductVariation();
		result.setCode(code);
		result.setMerchantStore(store);
		result.setProductOption(opt);
		result.setProductOptionValue(val);
		entityManager.persist(result);
		return result;
	}

	private PersistableProductVariation body(String code) {
		PersistableProductVariation result = new PersistableProductVariation();
		result.setId(original.getId());
		result.setCode(code);
		result.setOption(option.getId());
		result.setOptionValue(value.getId());
		return result;
	}

	private void assertOriginalUnchanged(Long id, Long optionId, Long valueId) {
		ProductVariation stored = entityManager.find(ProductVariation.class, id);
		assertEquals(owner.getId(), stored.getMerchantStore().getId());
		assertEquals("original", stored.getCode());
		assertEquals(optionId, stored.getProductOption().getId());
		assertEquals(valueId, stored.getProductOptionValue().getId());
	}

	@Test
	public void createInsertsInCallerStoreWithoutOverwritingForeignRow() {
		Long originalId = original.getId();
		Long originalOption = original.getProductOption().getId();
		Long originalValue = original.getProductOptionValue().getId();
		Long optionId = option.getId();
		Long valueId = value.getId();
		long before = entityManager.createQuery("select count(v) from ProductVariation v", Long.class).getSingleResult();

		Long createdId = facade.create(body("created"), other, language);
		entityManager.flush();
		entityManager.clear();

		assertNotEquals(originalId, createdId);
		assertEquals(before + 1, entityManager.createQuery("select count(v) from ProductVariation v", Long.class).getSingleResult().longValue());
		ProductVariation created = entityManager.find(ProductVariation.class, createdId);
		assertEquals(other.getId(), created.getMerchantStore().getId());
		assertEquals("created", created.getCode());
		assertEquals(optionId, created.getProductOption().getId());
		assertEquals(valueId, created.getProductOptionValue().getId());
		assertOriginalUnchanged(originalId, originalOption, originalValue);
	}

	@Test
	public void updateUsesStoreScopedPathIdentityEvenWithForeignBodyId() {
		Long originalId = original.getId();
		Long originalOption = original.getProductOption().getId();
		Long originalValue = original.getProductOptionValue().getId();
		ProductVariation own = variation("own", other, option, value);
		entityManager.flush();
		Long ownId = own.getId();

		facade.update(ownId, body("updated"), other, language);
		entityManager.flush();
		entityManager.clear();

		assertEquals("updated", entityManager.find(ProductVariation.class, ownId).getCode());
		assertOriginalUnchanged(originalId, originalOption, originalValue);
	}

	@Test
	public void foreignPathCannotUpdateAnotherStoresVariation() {
		Long originalId = original.getId();
		Long originalOption = original.getProductOption().getId();
		Long originalValue = original.getProductOptionValue().getId();

		assertThrows(ResourceNotFoundException.class,
				() -> facade.update(originalId, body("blocked"), other, language));
		entityManager.flush();
		entityManager.clear();

		assertOriginalUnchanged(originalId, originalOption, originalValue);
	}
}
