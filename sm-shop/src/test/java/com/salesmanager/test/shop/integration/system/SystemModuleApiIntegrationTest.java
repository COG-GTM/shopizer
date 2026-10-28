package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

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

import com.salesmanager.core.business.services.system.ModuleConfigurationService;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class SystemModuleApiIntegrationTest extends ServicesTestSupport {

	private static final String MODULE_URL = "/services/private/system/module";
	private static final String CONTENT_USER = "content-editor@test.com";
	private static final String CONTENT_PASSWORD = "Password1";
	private static final String BEANSTREAM = "{\"module\":\"PAYMENT\",\"code\":\"beanstream\",\"type\":\"creditcard\","
			+ "\"version\":\"\",\"regions\":[\"US\",\"CA\",\"GB\"],\"image\":\"beanstream.gif\",\"configuration\":["
			+ "{\"env\":\"TEST\",\"scheme\":\"https\",\"host\":\"%1$s\",\"port\":\"443\",\"uri\":\"/scripts/process_transaction.asp\"},"
			+ "{\"env\":\"PROD\",\"scheme\":\"https\",\"host\":\"%1$s\",\"port\":\"443\",\"uri\":\"/scripts/process_transaction.asp\"}]}";

	@Inject
	private TestRestTemplate testRestTemplate;

	@Inject
	private ModuleConfigurationService moduleConfigurationService;

	@Test
	public void storeLevelAdminCannotRewriteGlobalModule() throws Exception {
		createContentEditor();
		String before = moduleConfigurationService.getByCode("beanstream").getConfiguration();

		ResponseEntity<String> response = post(CONTENT_USER, CONTENT_PASSWORD, beanstream("evil.example.com"));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertEquals(before, moduleConfigurationService.getByCode("beanstream").getConfiguration());
	}

	@Test
	public void superadminCannotPointModuleAtUnapprovedHost() throws Exception {
		String before = moduleConfigurationService.getByCode("beanstream").getConfiguration();

		ResponseEntity<String> response = post("admin@shopizer.com", "password", beanstream("evil.example.com"));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(before, moduleConfigurationService.getByCode("beanstream").getConfiguration());
	}

	@Test
	public void superadminCanRedefineModuleWithReferenceEndpoint() throws Exception {
		ResponseEntity<String> response = post("admin@shopizer.com", "password", beanstream("www.beanstream.com"));

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private ResponseEntity<String> post(String user, String password, String json) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
		return testRestTemplate.withBasicAuth(user, password).exchange(MODULE_URL, HttpMethod.POST,
				new HttpEntity<>(json, headers), String.class);
	}

	private String beanstream(String host) {
		return String.format(BEANSTREAM, host);
	}

	private void createContentEditor() throws Exception {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(CONTENT_USER);
		user.setFirstName("Content");
		user.setLastName("Editor");
		user.setUserName(CONTENT_USER);
		user.setPassword(CONTENT_PASSWORD);
		user.setRepeatPassword(CONTENT_PASSWORD);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN_CONTENT");
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		if (response.getStatusCode() != HttpStatus.OK) {
			throw new Exception(response.toString());
		}
	}

}
