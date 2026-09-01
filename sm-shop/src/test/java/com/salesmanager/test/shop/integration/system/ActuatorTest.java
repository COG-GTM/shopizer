package com.salesmanager.test.shop.integration.system;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;



@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ActuatorTest {
	
	  @Inject
	  private TestRestTemplate testRestTemplate;
	
	
	  @Test
	  public void testPing() throws Exception {
		  HttpHeaders headers = new HttpHeaders();
		  headers.setContentType(MediaType.APPLICATION_JSON);
		  
		  HttpEntity<Object> entity = new HttpEntity<Object>(headers);

	      final ResponseEntity<Void> response = testRestTemplate.
	    		  exchange("/actuator/health/ping/", HttpMethod.GET, entity, Void.class);
	      if (response.getStatusCode() != HttpStatus.OK) {
	          throw new Exception(response.toString());
	      }
	  }

	  @Test
	  public void testSensitiveEndpointsNotReachableAnonymously() throws Exception {
		  for (String endpoint : new String[] {"/actuator/heapdump", "/actuator/env", "/actuator/threaddump",
				  "/actuator/configprops", "/actuator/beans", "/actuator/mappings", "/actuator/loggers"}) {
			  final ResponseEntity<String> response = testRestTemplate.getForEntity(endpoint, String.class);
			  if (response.getStatusCode() != HttpStatus.UNAUTHORIZED
					  && response.getStatusCode() != HttpStatus.NOT_FOUND) {
				  throw new Exception(endpoint + " " + response.toString());
			  }
		  }
	  }

	  @Test
	  public void testHealthDetailsHiddenAnonymously() throws Exception {
		  final ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health", String.class);
		  if (response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.SERVICE_UNAVAILABLE) {
			  throw new Exception(response.toString());
		  }
		  if (response.getBody() != null && response.getBody().contains("\"components\"")) {
			  throw new Exception("health components exposed anonymously " + response.toString());
		  }
	  }

}
