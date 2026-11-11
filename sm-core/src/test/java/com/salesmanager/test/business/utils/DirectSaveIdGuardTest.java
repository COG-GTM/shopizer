package com.salesmanager.test.business.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.repository.CrudRepository;

import com.salesmanager.core.model.generic.SalesManagerEntity;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.business.repositories.catalog.catalog.CatalogRepository;
import com.salesmanager.core.business.services.catalog.catalog.CatalogServiceImpl;
import com.salesmanager.core.model.catalog.catalog.Catalog;
import com.salesmanager.core.business.repositories.catalog.catalog.CatalogEntryRepository;
import com.salesmanager.core.business.services.catalog.catalog.CatalogEntryServiceImpl;
import com.salesmanager.core.model.catalog.catalog.CatalogCategoryEntry;
import com.salesmanager.core.business.repositories.catalog.product.attribute.ProductAttributeRepository;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeServiceImpl;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.business.repositories.catalog.product.image.ProductImageRepository;
import com.salesmanager.core.business.services.catalog.product.image.ProductImageServiceImpl;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.business.repositories.catalog.product.price.ProductPriceRepository;
import com.salesmanager.core.business.services.catalog.product.price.ProductPriceServiceImpl;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.business.repositories.catalog.product.type.ProductTypeRepository;
import com.salesmanager.core.business.services.catalog.product.type.ProductTypeServiceImpl;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.business.repositories.catalog.product.variant.ProductVariantRepository;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantServiceImpl;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.core.business.repositories.catalog.product.variant.ProductVariantGroupRepository;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantGroupServiceImpl;
import com.salesmanager.core.model.catalog.product.variant.ProductVariantGroup;
import com.salesmanager.core.business.repositories.customer.attribute.CustomerAttributeRepository;
import com.salesmanager.core.business.services.customer.attribute.CustomerAttributeServiceImpl;
import com.salesmanager.core.model.customer.attribute.CustomerAttribute;
import com.salesmanager.core.business.repositories.customer.optin.CustomerOptinRepository;
import com.salesmanager.core.business.services.customer.optin.CustomerOptinServiceImpl;
import com.salesmanager.core.model.system.optin.CustomerOptin;
import com.salesmanager.core.business.repositories.user.UserRepository;
import com.salesmanager.core.business.services.user.UserServiceImpl;
import com.salesmanager.core.model.user.User;
import com.salesmanager.core.model.catalog.product.Product;

class DirectSaveIdGuardTest {

    static Stream<SaveCase> services() {
        return Stream.of(
                saveCase("Catalog.saveOrUpdate", CatalogRepository.class, new Catalog(),
                        (r, e) -> new CatalogServiceImpl(r).saveOrUpdate(e, new MerchantStore())),
                saveCase("CatalogEntry.add", CatalogEntryRepository.class, new CatalogCategoryEntry(),
                        (r, e) -> new CatalogEntryServiceImpl(r).add(e, new Catalog())),
                saveCase("ProductAttribute.saveOrUpdate", ProductAttributeRepository.class, new ProductAttribute(),
                        (r, e) -> new ProductAttributeServiceImpl(r).saveOrUpdate(e)),
                saveCase("ProductImage.saveOrUpdate", ProductImageRepository.class, new ProductImage(),
                        (r, e) -> new ProductImageServiceImpl(r).saveOrUpdate(e)),
                saveCase("ProductPrice.saveOrUpdate", ProductPriceRepository.class, new ProductPrice(),
                        (r, e) -> new ProductPriceServiceImpl(r).saveOrUpdate(e)),
                saveCase("ProductType.update", ProductTypeRepository.class, new ProductType(),
                        (r, e) -> new ProductTypeServiceImpl(r).update("type", new MerchantStore(), e)),
                saveCase("ProductVariant.saveProductVariant", ProductVariantRepository.class, new ProductVariant(),
                        (r, e) -> new ProductVariantServiceImpl(r).saveProductVariant(e)),
                saveCase("ProductVariantGroup.saveOrUpdate", ProductVariantGroupRepository.class, new ProductVariantGroup(),
                        (r, e) -> new ProductVariantGroupServiceImpl(r).saveOrUpdate(e)),
                saveCase("CustomerAttribute.saveOrUpdate", CustomerAttributeRepository.class, new CustomerAttribute(),
                        (r, e) -> new CustomerAttributeServiceImpl(r).saveOrUpdate(e)),
                saveCase("CustomerOptin.optinCumtomer", CustomerOptinRepository.class, new CustomerOptin(),
                        (r, e) -> new CustomerOptinServiceImpl(r).optinCumtomer(e)),
                saveCase("User.saveOrUpdate", UserRepository.class, new User(),
                        (r, e) -> new UserServiceImpl(r).saveOrUpdate(e)),
                saveCase("ProductImage.updateProductImage", ProductImageRepository.class, new ProductImage(),
                        (r, e) -> new ProductImageServiceImpl(r).updateProductImage(new Product(), e)));
    }

    static Stream<Arguments> serviceIds() {
        return Stream.of(null, 0L, 37L)
                .flatMap(id -> services().map(service -> Arguments.of(service, id)));
    }

    @ParameterizedTest(name = "{0}, id={1}")
    @MethodSource("serviceIds")
    void normalizesZeroAtRepositoryBoundaryAndPreservesOtherIds(SaveCase service, Long id) throws Exception {
        service.entity.setId(id);
        Long expected = Long.valueOf(0L).equals(id) ? null : id;
        service.assertIdOnSave(expected);
        service.save.run();
        service.verifySave.run();
        assertEquals(expected, service.entity.getId());
    }

    @ParameterizedTest(name = "{0} propagates repository failure")
    @MethodSource("services")
    void propagatesRepositoryFailures(SaveCase service) {
        service.entity.setId(37L);
        IllegalStateException failure = new IllegalStateException("repository unavailable");
        service.failOnSave(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, service.save::run));
        assertEquals(Long.valueOf(37L), service.entity.getId());
    }

    private static <E extends SalesManagerEntity<Long, ?>, R extends CrudRepository<E, Long>>
            SaveCase saveCase(String name, Class<R> repositoryType, E entity, SaveOperation<R, E> operation) {
        R repository = mock(repositoryType);
        return new SaveCase(name, entity, () -> operation.save(repository, entity),
                () -> verify(repository).save(same(entity)),
                expected -> doAnswer(invocation -> {
                    E saved = invocation.getArgument(0);
                    assertSame(entity, saved);
                    assertEquals(expected, saved.getId());
                    return saved;
                }).when(repository).save(same(entity)),
                failure -> doThrow(failure).when(repository).save(same(entity)));
    }

    @FunctionalInterface
    private interface SaveOperation<R, E> {
        void save(R repository, E entity) throws Exception;
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static class SaveCase {
        private final String name;
        private final SalesManagerEntity<Long, ?> entity;
        private final Action save;
        private final Action verifySave;
        private final Consumer<Long> assertion;
        private final Consumer<RuntimeException> failure;

        SaveCase(String name, SalesManagerEntity<Long, ?> entity, Action save, Action verifySave,
                Consumer<Long> assertion,
                Consumer<RuntimeException> failure) {
            this.name = name;
            this.entity = entity;
            this.save = save;
            this.verifySave = verifySave;
            this.assertion = assertion;
            this.failure = failure;
        }

        void assertIdOnSave(Long expected) {
            assertion.accept(expected);
        }

        void failOnSave(RuntimeException exception) {
            failure.accept(exception);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
