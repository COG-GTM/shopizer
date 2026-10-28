package com.salesmanager.test.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.product.ProductFileManager;
import com.salesmanager.core.business.repositories.catalog.product.image.ProductImageRepository;
import com.salesmanager.core.business.services.catalog.product.image.ProductImageServiceImpl;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.ImageContentFile;

@RunWith(MockitoJUnitRunner.class)
public class ProductImageServiceImplValidationTest {

    @Mock
    private ProductImageRepository repository;

    @Mock
    private ProductFileManager productFileManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProductImageServiceImpl service;

    @Before
    public void setUp() {
        service = new ProductImageServiceImpl(repository);
        ReflectionTestUtils.setField(service, "productFileManager", productFileManager);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        when(repository.save(any(ProductImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void rejectsUnsafeProductImageNameAndClosesInput() throws Exception {
        CloseTrackingInputStream stream = new CloseTrackingInputStream();
        ProductImage productImage = productImage("../../../ACME/SKU1/LARGE/main.jpg", 0);
        ImageContentFile inputImage = inputImage("main.jpg", stream);

        ServiceException exception = assertValidationFailure(
                () -> service.addProductImage(new Product(), productImage, inputImage));

        assertEquals("message.image.invalid.name", exception.getMessageCode());
        assertEquals(ServiceException.EXCEPTION_VALIDATION, exception.getExceptionType());
        assertEquals(true, stream.closed);
        verify(productFileManager, never()).addProductImage(any(), any());
        verify(repository, never()).save(any(ProductImage.class));
    }

    @Test
    public void rejectsUnsafeInputFileName() throws Exception {
        ProductImage productImage = productImage("main.jpg", 0);
        ImageContentFile inputImage = inputImage("../main.jpg", new CloseTrackingInputStream());

        ServiceException exception = assertValidationFailure(
                () -> service.addProductImage(new Product(), productImage, inputImage));

        assertEquals("message.image.invalid.name", exception.getMessageCode());
        assertEquals(ServiceException.EXCEPTION_VALIDATION, exception.getExceptionType());
        verify(productFileManager, never()).addProductImage(any(), any());
        verify(repository, never()).save(any(ProductImage.class));
    }

    @Test
    public void addsSafeImageAndPublishesEvent() throws Exception {
        ProductImage productImage = productImage("main.jpg", 0);
        CloseTrackingInputStream stream = new CloseTrackingInputStream();
        ImageContentFile inputImage = inputImage("main.jpg", stream);

        service.addProductImage(new Product(), productImage, inputImage);

        verify(productFileManager).addProductImage(productImage, inputImage);
        verify(repository).save(productImage);
        verify(eventPublisher).publishEvent(any());
        assertEquals(true, stream.closed);
    }

    @Test
    public void wrapsValidationFailureFromAddProductImages() throws Exception {
        ProductImage productImage = productImage("../../unsafe.jpg", 0);
        CloseTrackingInputStream stream = new CloseTrackingInputStream();
        productImage.setImage(stream);

        try {
            service.addProductImages(new Product(), Collections.singletonList(productImage));
            fail("Expected ServiceException");
        } catch (ServiceException wrapped) {
            Throwable cause = wrapped.getCause();
            assertNotNull(cause);
            assertEquals(ServiceException.class, cause.getClass());
            assertEquals("message.image.invalid.name", ((ServiceException) cause).getMessageCode());
        }

        assertEquals(true, stream.closed);
        verify(productFileManager, never()).addProductImage(any(), any());
        verify(repository, never()).save(any(ProductImage.class));
    }

    @Test
    public void doesNotValidateExternalUrlImages() throws Exception {
        ProductImage productImage = productImage("../unsafe.jpg", 1);
        CloseTrackingInputStream stream = new CloseTrackingInputStream();
        ImageContentFile inputImage = inputImage("../../unsafe.jpg", stream);

        service.addProductImage(new Product(), productImage, inputImage);

        verify(productFileManager, never()).addProductImage(any(), any());
        verify(repository).save(productImage);
        verify(eventPublisher).publishEvent(any());
        assertEquals(true, stream.closed);
    }

    private static ProductImage productImage(String name, int imageType) {
        ProductImage image = new ProductImage();
        image.setProductImage(name);
        image.setImageType(imageType);
        return image;
    }

    private static ImageContentFile inputImage(String name, CloseTrackingInputStream stream) {
        ImageContentFile inputImage = new ImageContentFile();
        inputImage.setFileName(name);
        inputImage.setFile(stream);
        return inputImage;
    }

    private static ServiceException assertValidationFailure(CheckedOperation operation) throws Exception {
        try {
            operation.run();
            fail("Expected validation ServiceException");
        } catch (ServiceException exception) {
            return exception;
        }
        throw new AssertionError("Expected validation ServiceException");
    }

    private interface CheckedOperation {
        void run() throws Exception;
    }

    private static class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        CloseTrackingInputStream() {
            super(new byte[] {1});
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
