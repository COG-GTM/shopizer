package com.salesmanager.test.business.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.reference.loader.IntegrationModulesLoader;
import com.salesmanager.core.business.services.system.IntegrationModuleEndpointPolicy;
import com.salesmanager.core.model.system.IntegrationModule;

public class IntegrationModuleEndpointPolicyEdgeCaseTest {

	private static final String PAYPAL_SANDBOX = "https://www.sandbox.paypal.com/cgi-bin/webscr?cmd=_express-checkout&token=";
	private final IntegrationModulesLoader loader = new IntegrationModulesLoader();
	private final ObjectMapper mapper = new ObjectMapper();
	private IntegrationModuleEndpointPolicy policy;

	@Before
	public void setUp() throws Exception {
		policy = new IntegrationModuleEndpointPolicy(loader.loadIntegrationModules("reference/integrationmodules.json"));
	}

	@Test
	public void acceptsCaseInsensitiveReferenceEndpoint() throws Exception {
		policy.validate(beanstream("HTTPS", "WWW.BEANSTREAM.COM", "443", "/scripts/process_transaction.asp"));
	}

	@Test
	public void acceptsModuleWithoutConfiguration() throws Exception {
		policy.validate(module("{\"module\":\"SHIPPING\",\"code\":\"myship\"}"));
	}

	@Test
	public void acceptsMaximumLengthCodeWithoutHost() throws Exception {
		String code = String.join("", Collections.nCopies(100, "a"));
		policy.validate(moduleJson("SHIPPING", code, Collections.<Map<String, String>>emptyList()));
	}

	@Test
	public void acceptsPaypalSandboxReferenceUrl() throws Exception {
		policy.validate(paypal(PAYPAL_SANDBOX, null));
	}

	@Test
	public void acceptsPlainConfigurationValue() throws Exception {
		policy.validate(paypal(null, "Sale"));
	}

	@Test
	public void rejectsCodeLongerThanMaximum() throws Exception {
		assertRejected(moduleJson("SHIPPING", String.join("", Collections.nCopies(101, "a")),
				Collections.<Map<String, String>>emptyList()));
	}

	@Test
	public void rejectsCodeContainingSpaces() throws Exception {
		assertRejected(module("{\"module\":\"SHIPPING\",\"code\":\"bean stream\"}"));
	}

	@Test
	public void rejectsLookalikePaypalHost() throws Exception {
		assertRejected(paypal("https://www.paypal.com.evil.example.com/x", null));
	}

	@Test
	public void rejectsUrlUserInfo() throws Exception {
		assertRejected(paypal("https://user@www.paypal.com/x", null));
	}

	@Test
	public void rejectsBackslashUserInfoAmbiguity() throws Exception {
		assertRejected(paypal("https://www.paypal.com\\@evil.example.com/x", null));
	}

	@Test
	public void rejectsOpaqueHttpsUrl() throws Exception {
		assertRejected(paypal("https:evil.example.com", null));
	}

	@Test
	public void rejectsActiveAndLocalSchemes() throws Exception {
		assertRejected(paypal("JAVASCRIPT:alert(1)", null));
		assertRejected(paypal("data:text/html,x", null));
		assertRejected(paypal("file:///etc/passwd", null));
	}

	@Test
	public void rejectsForeignUrlWithLeadingWhitespace() throws Exception {
		assertRejected(paypal("  https://evil.example.com/x", null));
	}

	@Test
	public void rejectsExplicitDefaultPortWhenReferenceHasNoPort() throws Exception {
		assertRejected(paypal("https://www.paypal.com:443/cgi-bin/webscr", null));
	}

	@Test
	public void rejectsForeignUrlInConfig2() throws Exception {
		assertRejected(paypal(null, "https://evil.example.com/x"));
	}

	@Test
	public void rejectsInvalidBeanstreamUris() throws Exception {
		assertRejected(beanstream("https", "www.beanstream.com", "443", "evil.example.com/x"));
		assertRejected(beanstream("https", "www.beanstream.com", "443", "/\\evil"));
		assertRejected(beanstream("https", "www.beanstream.com", "443", "/has space"));
		assertRejected(beanstream("https", "www.beanstream.com", "443", "/has\nnewline"));
	}

	@Test
	public void rejectsReferenceEndpointWhenReferenceListIsEmpty() throws Exception {
		IntegrationModuleEndpointPolicy emptyPolicy =
				new IntegrationModuleEndpointPolicy(Collections.<IntegrationModule>emptyList());
		assertRejected(emptyPolicy, beanstream("https", "www.beanstream.com", "443",
				"/scripts/process_transaction.asp"));
	}

	private IntegrationModule beanstream(String scheme, String host, String port, String uri) throws Exception {
		Map<String, String> configuration = configuration("TEST", scheme, host, port, uri, null, null);
		return moduleJson("PAYMENT", "beanstream", Arrays.asList(configuration));
	}

	private IntegrationModule paypal(String config1, String config2) throws Exception {
		Map<String, String> configuration = configuration("TEST", "", "", "", "", config1, config2);
		return moduleJson("PAYMENT", "paypal-express-checkout", Arrays.asList(configuration));
	}

	private Map<String, String> configuration(String env, String scheme, String host, String port, String uri,
			String config1, String config2) {
		Map<String, String> configuration = new HashMap<String, String>();
		configuration.put("env", env);
		configuration.put("scheme", scheme);
		configuration.put("host", host);
		configuration.put("port", port);
		configuration.put("uri", uri);
		if (config1 != null) {
			configuration.put("config1", config1);
		}
		if (config2 != null) {
			configuration.put("config2", config2);
		}
		return configuration;
	}

	private IntegrationModule moduleJson(String moduleType, String code, List<Map<String, String>> configurations)
			throws Exception {
		Map<String, Object> module = new HashMap<String, Object>();
		module.put("module", moduleType);
		module.put("code", code);
		if (!configurations.isEmpty()) {
			module.put("configuration", configurations);
		}
		@SuppressWarnings("rawtypes")
		Map parsed = mapper.convertValue(module, Map.class);
		IntegrationModule result = loader.loadModule(parsed);
		assertNotNull(result);
		return result;
	}

	private IntegrationModule module(String json) throws Exception {
		@SuppressWarnings("rawtypes")
		Map parsed = mapper.readValue(json, Map.class);
		IntegrationModule result = loader.loadModule(parsed);
		assertNotNull(result);
		return result;
	}

	private void assertRejected(IntegrationModule module) {
		assertRejected(policy, module);
	}

	private void assertRejected(IntegrationModuleEndpointPolicy targetPolicy, IntegrationModule module) {
		try {
			targetPolicy.validate(module);
			fail("Module should have been rejected");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
	}
}
