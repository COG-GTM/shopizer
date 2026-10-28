package com.salesmanager.core.business.modules.integration.shipping.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.model.system.ModuleConfig;
import com.salesmanager.core.modules.integration.IntegrationException;

/**
 * Fixed carrier endpoints for shipping quote modules that send merchant
 * credentials to a third party. These endpoints are deliberately not read
 * from the integration module record, which can be modified at runtime.
 */
public final class CarrierEndpoints {

	public static final String UPS = "ups";
	public static final String USPS = "usps";

	private static final Map<String, Map<String, Endpoint>> ENDPOINTS;

	static {
		Map<String, Map<String, Endpoint>> endpoints = new HashMap<String, Map<String, Endpoint>>();

		Map<String, Endpoint> ups = new HashMap<String, Endpoint>();
		ups.put(Constants.TEST_ENVIRONMENT, new Endpoint("https", "wwwcie.ups.com", "443", "/ups.app/xml/Rate"));
		ups.put(Constants.PRODUCTION_ENVIRONMENT, new Endpoint("https", "onlinetools.ups.com", "443", "/xml/Rate"));
		endpoints.put(UPS, Collections.unmodifiableMap(ups));

		Map<String, Endpoint> usps = new HashMap<String, Endpoint>();
		usps.put(Constants.TEST_ENVIRONMENT, new Endpoint("http", "testing.shippingapis.com", "80", "/ShippingAPI.dll"));
		usps.put(Constants.PRODUCTION_ENVIRONMENT, new Endpoint("http", "production.shippingapis.com", "80", "/ShippingAPI.dll"));
		endpoints.put(USPS, Collections.unmodifiableMap(usps));

		ENDPOINTS = Collections.unmodifiableMap(endpoints);
	}

	private CarrierEndpoints() {
	}

	public static boolean isCarrier(String moduleCode) {
		return moduleCode != null && ENDPOINTS.containsKey(moduleCode);
	}

	public static Endpoint resolve(String moduleCode, String environment) throws IntegrationException {
		Map<String, Endpoint> byEnvironment = moduleCode == null ? null : ENDPOINTS.get(moduleCode);
		Endpoint endpoint = byEnvironment == null || environment == null ? null : byEnvironment.get(environment);
		if (endpoint == null) {
			throw new IntegrationException("No carrier endpoint for module " + moduleCode + " in environment " + environment);
		}
		return endpoint;
	}

	/**
	 * Rejects a carrier module definition whose endpoints differ from the fixed
	 * carrier endpoints.
	 */
	public static void validate(IntegrationModule module) {
		if (module == null || !isCarrier(module.getCode())) {
			return;
		}
		Map<String, Endpoint> allowed = ENDPOINTS.get(module.getCode());
		Map<String, ModuleConfig> configs = module.getModuleConfigs();
		if (configs == null) {
			return;
		}
		for (ModuleConfig config : configs.values()) {
			Endpoint expected = config == null ? null : allowed.get(config.getEnv());
			if (expected == null || !expected.matches(config)) {
				throw new IllegalArgumentException("Endpoint for carrier module " + module.getCode()
						+ " must be one of the fixed carrier endpoints");
			}
		}
	}

	public static final class Endpoint {

		private final String scheme;
		private final String host;
		private final String port;
		private final String uri;

		Endpoint(String scheme, String host, String port, String uri) {
			this.scheme = scheme;
			this.host = host;
			this.port = port;
			this.uri = uri;
		}

		public String getScheme() {
			return scheme;
		}

		public String getHost() {
			return host;
		}

		public String getPort() {
			return port;
		}

		public String getUri() {
			return uri;
		}

		public String toUrl() {
			return scheme + "://" + host + ":" + port + uri;
		}

		boolean matches(ModuleConfig config) {
			return scheme.equalsIgnoreCase(Objects.toString(config.getScheme(), ""))
					&& host.equalsIgnoreCase(Objects.toString(config.getHost(), ""))
					&& port.equals(config.getPort())
					&& uri.equals(config.getUri());
		}
	}
}
