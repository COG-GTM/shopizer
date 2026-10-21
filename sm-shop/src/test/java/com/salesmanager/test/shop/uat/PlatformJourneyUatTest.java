package com.salesmanager.test.shop.uat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class PlatformJourneyUatTest extends ServicesTestSupport {

    @Test
    public void platformJourney() {
        ResponseEntity<JsonNode> health = testRestTemplate.getForEntity(
                "/actuator/health", JsonNode.class);
        assertThat(health.getStatusCode(), is(HttpStatus.OK));
        assertThat(health.getBody().path("status").asText(), is("UP"));

        ResponseEntity<JsonNode> apiDocs = testRestTemplate.getForEntity(
                "/v2/api-docs", JsonNode.class);
        assertThat(apiDocs.getStatusCode(), is(HttpStatus.OK));
        assertThat(apiDocs.getBody().path("swagger").asText(), is("2.0"));

        HttpHeaders validCredentials = new HttpHeaders();
        validCredentials.setBasicAuth("admin@shopizer.com", "password");
        validCredentials.setContentType(MediaType.APPLICATION_JSON);
        validCredentials.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        ResponseEntity<String> validOptin = testRestTemplate.postForEntity(
                "/services/private/system/optin", new HttpEntity<>("{}", validCredentials), String.class);
        assertThat(validOptin.getStatusCode(), is(HttpStatus.OK));

        HttpHeaders invalidCredentials = new HttpHeaders();
        invalidCredentials.setBasicAuth("admin@shopizer.com", "wrong");
        invalidCredentials.setContentType(MediaType.APPLICATION_JSON);
        invalidCredentials.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        ResponseEntity<String> invalidOptin = testRestTemplate.postForEntity(
                "/services/private/system/optin", new HttpEntity<>("{}", invalidCredentials), String.class);
        assertThat(invalidOptin.getStatusCode(), is(HttpStatus.UNAUTHORIZED));

        HttpHeaders noCredentials = new HttpHeaders();
        noCredentials.setContentType(MediaType.APPLICATION_JSON);
        noCredentials.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        ResponseEntity<String> unauthenticatedOptin = testRestTemplate.postForEntity(
                "/services/private/system/optin", new HttpEntity<>("{}", noCredentials), String.class);
        assertThat(unauthenticatedOptin.getStatusCode(), is(HttpStatus.UNAUTHORIZED));
    }
}
