package com.salesmanager.core.business.modules.cms.content.aws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.InputContentFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

class S3StaticContentAssetsManagerImplTest {
    private S3Client client;
    private S3StaticContentAssetsManagerImpl manager;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        manager = new S3StaticContentAssetsManagerImpl();
        manager.setClient(client);
        CMSManager cms = mock(CMSManager.class);
        when(cms.getRootName()).thenReturn("content-bucket");
        manager.setCmsManager(cms);
    }

    @Test
    void uploadKeepsKeyAndPublicAcl() throws Exception {
        InputContentFile file = new InputContentFile();
        file.setFileName("logo.png");
        file.setMimeType("image/png");
        file.setFileContentType(FileContentType.IMAGE);
        file.setFile(new ByteArrayInputStream(new byte[] {1, 2}));
        manager.addFile("DEFAULT", Optional.of("ignored"), file);
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertEquals("content-bucket", request.getValue().bucket());
        assertEquals("files/DEFAULT/logo.png", request.getValue().key());
        assertEquals("public-read", request.getValue().aclAsString());
        assertEquals("image/png", request.getValue().contentType());
        assertArrayEquals(new byte[] {1, 2}, body.getValue().contentStreamProvider().newStream().readAllBytes());
    }

    @Test
    void deleteKeepsKeys() throws Exception {
        manager.removeFile("DEFAULT", FileContentType.IMAGE, "logo.png", Optional.of("ignored"));
        manager.removeFiles("DEFAULT", Optional.empty());
        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client, times(2)).deleteObject(request.capture());
        assertEquals("files/DEFAULT/logo.png", request.getAllValues().get(0).key());
        assertEquals("files/DEFAULT/", request.getAllValues().get(1).key());
    }

    @Test
    void listKeepsPrefixAndFiltersSubfolders() throws Exception {
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
            .contents(S3Object.builder().key("files/DEFAULT/logo.png").build(),
                S3Object.builder().key("files/DEFAULT/sub/hidden.png").build()).build());
        assertEquals(java.util.Collections.singletonList("logo.png"),
            manager.getFileNames("DEFAULT", Optional.empty(), FileContentType.IMAGE));
        ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(client).listObjectsV2(request.capture());
        assertEquals("content-bucket", request.getValue().bucket());
        assertEquals("files/DEFAULT/", request.getValue().prefix());
    }

    @Test
    void emptyListRetainsNullResult() throws Exception {
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder().build());
        assertNull(manager.getFileNames("DEFAULT", Optional.empty(), FileContentType.IMAGE));
        assertNull(manager.getFiles("DEFAULT", Optional.empty(), FileContentType.IMAGE));
    }

    @Test
    void wrapsSdkFailure() {
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(new IllegalStateException("offline"));
        assertThrows(ServiceException.class,
            () -> manager.removeFile("DEFAULT", FileContentType.IMAGE, "logo.png", Optional.empty()));
    }
}
