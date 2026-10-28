package com.salesmanager.test.business.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.reference.loader.IntegrationModulesLoader;
import com.salesmanager.core.business.services.system.IntegrationModuleEndpointPolicy;
import com.salesmanager.core.model.system.IntegrationModule;

public class IntegrationModuleEndpointPolicyTest {

	private static final String BEANSTREAM = "{\"module\":\"PAYMENT\",\"code\":\"beanstream\",\"type\":\"creditcard\","
			+ "\"regions\":[\"US\",\"CA\"],\"configuration\":["
			+ "{\"env\":\"TEST\",\"scheme\":\"%s\",\"host\":\"%s\",\"port\":\"%s\",\"uri\":\"%s\"},"
			+ "{\"env\":\"PROD\",\"scheme\":\"https\",\"host\":\"www.beanstream.com\",\"port\":\"443\",\"uri\":\"/scripts/process_transaction.asp\"}]}";

	private final IntegrationModulesLoader loader = new IntegrationModulesLoader();
	private IntegrationModuleEndpointPolicy policy;

	@Before
	public void setUp() throws Exception {
		policy = new IntegrationModuleEndpointPolicy(loader.loadIntegrationModules("reference/integrationmodules.json"));
	}

	@Test
	public void acceptsEveryBundledReferenceModule() throws Exception {
		for (IntegrationModule module : loader.loadIntegrationModules("reference/integrationmodules.json")) {
			policy.validate(module);
		}
	}

	@Test
	public void acceptsReferenceEndpointWithDifferentPath() throws Exception {
		policy.validate(beanstream("https", "www.beanstream.com", "443", "/api/v1/payments"));
	}

	@Test
	public void rejectsAttackerHost() throws Exception {
		assertRejected(beanstream("https", "evil.example.com", "443", "/scripts/process_transaction.asp"));
	}

	@Test
	public void rejectsInternalHost() throws Exception {
		assertRejected(beanstream("http", "169.254.169.254", "80", "/latest/meta-data/"));
	}

	@Test
	public void rejectsSchemeDowngrade() throws Exception {
		assertRejected(beanstream("http", "www.beanstream.com", "443", "/scripts/process_transaction.asp"));
	}

	@Test
	public void rejectsPortChange() throws Exception {
		assertRejected(beanstream("https", "www.beanstream.com", "8443", "/scripts/process_transaction.asp"));
	}

	@Test
	public void rejectsHostSmuggledThroughUri() throws Exception {
		assertRejected(beanstream("https", "www.beanstream.com", "443", "@evil.example.com/x"));
		assertRejected(beanstream("https", "www.beanstream.com", "443", "//evil.example.com/x"));
	}

	@Test
	public void rejectsSchemeOrPortWithoutHost() throws Exception {
		assertRejected(beanstream("https", "", "443", ""));
	}

	@Test
	public void rejectsHostBorrowedFromAnotherModule() throws Exception {
		assertRejected(beanstream("https", "onlinetools.ups.com", "443", "/xml/Rate"));
	}

	@Test
	public void rejectsUnknownModuleWithEndpoint() throws Exception {
		assertRejected(module("{\"module\":\"SHIPPING\",\"code\":\"myship\",\"configuration\":["
				+ "{\"env\":\"PROD\",\"scheme\":\"https\",\"host\":\"evil.example.com\",\"port\":\"443\",\"uri\":\"/\"}]}"));
	}

	@Test
	public void acceptsUnknownModuleWithoutEndpoint() throws Exception {
		policy.validate(module("{\"module\":\"SHIPPING\",\"code\":\"myship\",\"configuration\":["
				+ "{\"env\":\"PROD\",\"scheme\":\"\",\"host\":\"\",\"port\":\"\",\"uri\":\"\"}]}"));
	}

	@Test
	public void rejectsRedirectUrlToForeignOrigin() throws Exception {
		assertRejected(paypal("https://evil.example.com/cgi-bin/webscr?token="));
		assertRejected(paypal("javascript:alert(1)"));
	}

	@Test
	public void acceptsRedirectUrlOnReferenceOrigin() throws Exception {
		policy.validate(paypal("https://www.paypal.com/checkoutnow?token="));
	}

	@Test
	public void rejectsInvalidCode() throws Exception {
		assertRejected(module("{\"module\":\"PAYMENT\",\"code\":\"../x\"}"));
		assertRejected(module("{\"module\":\"PAYMENT\"}"));
	}

	private IntegrationModule beanstream(String scheme, String host, String port, String uri) throws Exception {
		return module(String.format(BEANSTREAM, scheme, host, port, uri));
	}

	private IntegrationModule paypal(String config1) throws Exception {
		return module("{\"module\":\"PAYMENT\",\"code\":\"paypal-express-checkout\",\"configuration\":["
				+ "{\"env\":\"PROD\",\"scheme\":\"\",\"host\":\"\",\"port\":\"\",\"uri\":\"\",\"config1\":\"" + config1 + "\"}]}");
	}

	@SuppressWarnings("rawtypes")
	private IntegrationModule module(String json) throws Exception {
		IntegrationModule module = loader.loadModule(new ObjectMapper().readValue(json, Map.class));
		assertNotNull(module);
		return module;
	}

	private void assertRejected(IntegrationModule module) {
		try {
			policy.validate(module);
			fail("Module should have been rejected");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
	}

}
