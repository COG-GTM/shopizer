package com.salesmanager.core.business.services.system;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.model.system.ModuleConfig;

/**
 * Integration modules are shared by every store, and payment / shipping modules post card data and
 * merchant credentials to the endpoint stored in their {@link ModuleConfig}. This policy pins those
 * endpoints to the ones shipped in the bundled reference module definitions.
 */
public final class IntegrationModuleEndpointPolicy {

	private static final Pattern MODULE_CODE = Pattern.compile("[A-Za-z0-9_-]{1,100}");
	private static final Pattern SAFE_PATH = Pattern.compile("/(?!/)[A-Za-z0-9._~!$&'()*+,;=:%/?-]*");

	private final Map<String, Set<String>> endpoints = new HashMap<String, Set<String>>();
	private final Map<String, Set<String>> uris = new HashMap<String, Set<String>>();
	private final Map<String, Set<String>> urlOrigins = new HashMap<String, Set<String>>();

	public IntegrationModuleEndpointPolicy(List<IntegrationModule> referenceModules) {
		for (IntegrationModule module : referenceModules) {
			if (module.getCode() == null || module.getModuleConfigs() == null) {
				continue;
			}
			for (ModuleConfig config : module.getModuleConfigs().values()) {
				if (StringUtils.isNotBlank(config.getHost())) {
					add(endpoints, module.getCode(), endpoint(config));
				}
				if (StringUtils.isNotBlank(config.getUri())) {
					add(uris, module.getCode(), config.getUri());
				}
				for (String value : new String[] { config.getConfig1(), config.getConfig2() }) {
					String origin = origin(value);
					if (origin != null) {
						add(urlOrigins, module.getCode(), origin);
					}
				}
			}
		}
	}

	public void validate(IntegrationModule module) throws ServiceException {
		String code = module.getCode();
		if (code == null || !MODULE_CODE.matcher(code).matches()) {
			throw invalid("Integration module code is missing or invalid");
		}
		Map<String, ModuleConfig> configs = module.getModuleConfigs();
		if (configs == null) {
			return;
		}
		for (ModuleConfig config : configs.values()) {
			validateEndpoint(code, config);
			validateUri(code, config.getUri());
			validateUrl(code, config.getConfig1());
			validateUrl(code, config.getConfig2());
		}
	}

	private void validateEndpoint(String code, ModuleConfig config) throws ServiceException {
		if (StringUtils.isBlank(config.getHost())) {
			if (StringUtils.isNotBlank(config.getScheme()) || StringUtils.isNotBlank(config.getPort())) {
				throw invalid("Integration module " + code + " declares a scheme or port without a host");
			}
			return;
		}
		if (!allowed(endpoints, code).contains(endpoint(config))) {
			throw invalid("Endpoint " + endpoint(config) + " is not an allowed endpoint for integration module " + code);
		}
	}

	private void validateUri(String code, String uri) throws ServiceException {
		if (StringUtils.isEmpty(uri) || allowed(uris, code).contains(uri)) {
			return;
		}
		if (!SAFE_PATH.matcher(uri).matches()) {
			throw invalid("Integration module " + code + " uri must be a relative path");
		}
	}

	private void validateUrl(String code, String value) throws ServiceException {
		if (value == null || !value.contains(":")) {
			return;
		}
		String origin = origin(value);
		if (origin == null) {
			if (looksLikeUrl(value)) {
				throw invalid("Integration module " + code + " contains a malformed URL");
			}
			return;
		}
		if (!allowed(urlOrigins, code).contains(origin)) {
			throw invalid("URL origin " + origin + " is not allowed for integration module " + code);
		}
	}

	private static boolean looksLikeUrl(String value) {
		String v = value.trim().toLowerCase(Locale.ROOT);
		return v.contains("//") || v.startsWith("http:") || v.startsWith("https:") || v.startsWith("javascript:")
				|| v.startsWith("data:") || v.startsWith("vbscript:") || v.startsWith("file:");
	}

	private static String endpoint(ModuleConfig config) {
		return normalize(config.getScheme()) + "://" + normalize(config.getHost()) + ":"
				+ StringUtils.trimToEmpty(config.getPort());
	}

	private static String origin(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		try {
			URI uri = new URI(value.trim());
			if (!uri.isAbsolute() || uri.getHost() == null || uri.getRawUserInfo() != null) {
				return null;
			}
			return normalize(uri.getScheme()) + "://" + normalize(uri.getHost()) + ":" + uri.getPort();
		} catch (URISyntaxException e) {
			return null;
		}
	}

	private static String normalize(String value) {
		return StringUtils.trimToEmpty(value).toLowerCase(Locale.ROOT);
	}

	private static void add(Map<String, Set<String>> map, String code, String value) {
		map.computeIfAbsent(code, k -> new HashSet<String>()).add(value);
	}

	private static Collection<String> allowed(Map<String, Set<String>> map, String code) {
		Set<String> values = map.get(code);
		return values == null ? Collections.<String>emptySet() : values;
	}

	private static ServiceException invalid(String message) {
		return new ServiceException(ServiceException.EXCEPTION_VALIDATION, message);
	}

}
