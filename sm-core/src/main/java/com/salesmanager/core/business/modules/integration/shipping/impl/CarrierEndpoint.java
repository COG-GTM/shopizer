package com.salesmanager.core.business.modules.integration.shipping.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.salesmanager.core.model.system.ModuleConfig;
import com.salesmanager.core.modules.integration.IntegrationException;

/**
 * Builds outbound carrier URLs from a module configuration while pinning the
 * scheme, host and port to the carrier's known endpoints.
 */
public final class CarrierEndpoint {

	private CarrierEndpoint() {
	}

	public static String buildUrl(String carrier, ModuleConfig config, Set<String> allowedSchemes,
			Set<String> allowedHosts) throws IntegrationException {

		if (config == null) {
			throw new IntegrationException("No " + carrier + " module configuration for the current environment");
		}

		String scheme = StringUtils.trimToEmpty(config.getScheme()).toLowerCase(Locale.ROOT);
		String host = StringUtils.trimToEmpty(config.getHost()).toLowerCase(Locale.ROOT);
		String uri = StringUtils.trimToEmpty(config.getUri());

		if (!allowedSchemes.contains(scheme) || !allowedHosts.contains(host)) {
			throw new IntegrationException(carrier + " endpoint " + scheme + "://" + host + " is not an allowed carrier endpoint");
		}

		int port = parsePort(carrier, config.getPort());

		if (!uri.startsWith("/") || uri.startsWith("//") || uri.contains("\\") || uri.contains("?")
				|| uri.contains("#")) {
			throw new IntegrationException(carrier + " endpoint path is invalid");
		}

		try {
			URI built = new URI(scheme, null, host, port, uri, null, null);
			if (!host.equals(built.getHost())) {
				throw new IntegrationException(carrier + " endpoint is invalid");
			}
			return built.toASCIIString();
		} catch (URISyntaxException e) {
			throw new IntegrationException(carrier + " endpoint is invalid", e);
		}
	}

	private static int parsePort(String carrier, String port) throws IntegrationException {
		String value = StringUtils.trimToEmpty(port);
		if (value.isEmpty()) {
			return -1;
		}
		if (!"80".equals(value) && !"443".equals(value)) {
			throw new IntegrationException(carrier + " endpoint port " + value + " is not allowed");
		}
		return Integer.parseInt(value);
	}

}
