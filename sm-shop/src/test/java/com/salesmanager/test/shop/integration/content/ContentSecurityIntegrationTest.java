package com.salesmanager.test.shop.integration.content;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class ContentSecurityIntegrationTest extends ServicesTestSupport {
    @Test
    public void rejectsTraversalUploads() throws Exception {
        for (String name : new String[] { "../../evil.png", "..\\evil.png", "/etc/x.png" }) {
            assertJsonClientError(upload("/api/v1/private/file", "file", getHeader(), name));
        }
    }

    @Test
    public void acceptsValidUpload() {
        ResponseEntity<String> response = upload("/api/v1/private/file", "file", getHeader(), "ok-test.png");
        assertTrue(response.toString(), response.getStatusCode().is2xxSuccessful());
        ResponseEntity<String> deleted = testRestTemplate.exchange(
                "/api/v1/private/content/?name=ok-test.png&contentType=IMAGE", HttpMethod.DELETE,
                new HttpEntity<>(getHeader()), String.class);
        assertTrue(deleted.toString(), deleted.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void rejectsMixedBatch() throws Exception {
        assertJsonClientError(upload("/api/v1/private/files", "file[]", getHeader(), "good.png", "../../evil.png"));
    }

    @Test
    public void rejectsUnsafeDeleteAndUnknownContentType() throws Exception {
        for (String query : new String[] { "name=../../x.png&contentType=IMAGE", "name=ok.png&contentType=UNKNOWN" }) {
            assertJsonClientError(testRestTemplate.exchange("/api/v1/private/content/?" + query,
                    HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class));
        }
    }

    @Test
    public void uploadRequiresAuthentication() {
        ResponseEntity<String> response = upload("/api/v1/private/file", "file", new HttpHeaders(), "ok-test.png");
        assertTrue(response.toString(), response.getStatusCodeValue() == 401 || response.getStatusCodeValue() == 403);
    }

    private ResponseEntity<String> upload(String url, String field, HttpHeaders headers, String... names) {
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (String name : names) {
            HttpHeaders part = new HttpHeaders();
            part.setContentType(MediaType.IMAGE_PNG);
            body.add(field, new HttpEntity<>(new ByteArrayResource(new byte[] { 1, 2, 3 }) {
                @Override public String getFilename() { return name; }
            }, part));
        }
        return testRestTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    private void assertJsonClientError(ResponseEntity<String> response) throws Exception {
        assertTrue(response.toString(), response.getStatusCode().is4xxClientError());
        assertNotNull(response.getHeaders().getContentType());
        assertTrue(response.toString(), MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType()));
        assertTrue(response.toString(), new ObjectMapper().readTree(response.getBody()).isObject());
    }
}
