package com.salesmanager.shop.utils;

import static com.salesmanager.shop.constants.Constants.ADMIN_URI;
import static com.salesmanager.shop.constants.Constants.BLANK;
import static com.salesmanager.shop.constants.Constants.CATEGORY_URI;
import static com.salesmanager.shop.constants.Constants.DEFAULT_DOMAIN_NAME;
import static com.salesmanager.shop.constants.Constants.FILES_URI;
import static com.salesmanager.shop.constants.Constants.HTTP_SCHEME;
import static com.salesmanager.shop.constants.Constants.ORDER_DOWNLOAD_URI;
import static com.salesmanager.shop.constants.Constants.SHOP_URI;
import static com.salesmanager.shop.constants.Constants.SLASH;
import static com.salesmanager.shop.constants.Constants.STATIC_URI;
import static com.salesmanager.shop.constants.Constants.URL_EXTENSION;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.salesmanager.core.business.utils.CoreConfiguration;
import com.salesmanager.core.model.catalog.product.file.DigitalProduct;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.order.ReadableOrderProductDownload;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;

@Component
public class FilePathUtils {

	private static final String DOWNLOADS = "/downloads/";
	private static final String DOUBLE_SLASH = "://";
	private static final String CONTEXT_PATH = "CONTEXT_PATH";
	public static final String X_FORWARDED_HOST = "X-Forwarded-Host";
	public static final String HTTP = "http://";
	public static final String HTTPS = "https://";
	public static final String PASSWORD_RESET_ALLOWED_ORIGINS = "PASSWORD_RESET_ALLOWED_ORIGINS";
	private static final Pattern SAFE_URL_PATH = Pattern.compile("[A-Za-z0-9/._~-]*");

	@Inject
	private CoreConfiguration coreConfiguration;

	@Inject
	@Qualifier("img")
	private ImageFilePath imageUtils;

	@Resource(name = "shopizer-properties")
	public Properties properties = new Properties();

	/**
	 * Builds a static content content file path that can be used by image
	 * servlet utility for getting the physical image Example:
	 * /files/<storeCode>/
	 */
	public String buildStaticFilePath(String storeCode, String fileName) {
		String path = FILES_URI + SLASH + storeCode + SLASH;
		if (StringUtils.isNotBlank(fileName)) {
			return path + fileName;
		}
		return path;
	}

	public String buildStaticFilePath(MerchantStore store) {
		return STATIC_URI + FILES_URI + SLASH + store.getCode() + SLASH;
	}

	/**
	 * Example: /admin/files/downloads/<storeCode>/<product>
	 */
	public String buildAdminDownloadProductFilePath(MerchantStore store, DigitalProduct digitalProduct) {
		return ADMIN_URI + FILES_URI + DOWNLOADS + store.getCode() + SLASH + digitalProduct.getProductFileName();
	}

	/**
	 * Example: /shop/order/download/<orderId>.html
	 */
	public String buildOrderDownloadProductFilePath(MerchantStore store, ReadableOrderProductDownload digitalProduct,
			Long orderId) {
		return SHOP_URI + ORDER_DOWNLOAD_URI + SLASH + orderId + SLASH + digitalProduct.getId() + URL_EXTENSION;
	}

	/**
	 * Example: /<baseImagePath>/files/<storeCode>/STATIC_FILE/<fileName> Or
	 * example: /<shopScheme>://<domainName>/<contextPath>/files/<storeCode>/
	 */
	public String buildStaticFileAbsolutePath(MerchantStore store, String fileName) {
		if (StringUtils.isNotBlank(imageUtils.getBasePath(store))
				&& imageUtils.getBasePath(store).startsWith(HTTP_SCHEME)) {
			return imageUtils.getBasePath(store) + FILES_URI + SLASH + store.getCode() + SLASH
					+ FileContentType.STATIC_FILE + SLASH + fileName;
		} else {
			String scheme = this.getScheme(store);
			return scheme + SLASH + coreConfiguration.getProperty("CONTEXT_PATH")
					+ buildStaticFilePath(store.getCode(), fileName);
		}
	}

	/**
	 * Example: http[s]://<scheme>/<domainName>/<contextPath>
	 */
	public String buildStoreUri(MerchantStore store, HttpServletRequest request) {
		return buildBaseUrl(request, store);
	}

	/**
	 * \/<contextPath>
	 */
	public String buildStoreUri(MerchantStore store, String contextPath) {
		return normalizePath(contextPath);
	}

	public String buildRelativeStoreUri(HttpServletRequest request, MerchantStore store) {
		return "" + normalizePath(request.getContextPath());
	}

	/**
	 * Access to the customer section
	 */
	public String buildCustomerUri(MerchantStore store, String contextPath) {
		return buildStoreUri(store, contextPath);
	}

	public String buildAdminUri(MerchantStore store, HttpServletRequest request) {
		String baseUrl = buildBaseUrl(request, store);
		return baseUrl + ADMIN_URI;
	}

	public String buildCategoryUrl(MerchantStore store, String contextPath, String url) {
		return buildStoreUri(store, contextPath) + SHOP_URI + CATEGORY_URI + SLASH + url + URL_EXTENSION;
	}

	public String buildProductUrl(MerchantStore store, String contextPath, String url) {
		return buildStoreUri(store, contextPath) + SHOP_URI + Constants.PRODUCT_URI + SLASH + url + URL_EXTENSION;
	}

	public String getContextPath() {
		return properties.getProperty(CONTEXT_PATH);
	}

	private String normalizePath(String path) {
		if (SLASH.equals(path)) {
			return BLANK;
		} else {
			return path;
		}
	}

	private String getDomainName(String domainName) {
		if (StringUtils.isBlank(domainName)) {
			return DEFAULT_DOMAIN_NAME;
		} else {
			return domainName;
		}
	}

	private String getScheme(MerchantStore store) {
		String baseScheme = store.getDomainName();
		if (baseScheme != null && baseScheme.length() > 0
				&& baseScheme.charAt(baseScheme.length() - 1) == Constants.SLASH.charAt(0)) {
			baseScheme = baseScheme.substring(0, baseScheme.length() - 1);
		}
		// end no more
		return validUrl(baseScheme);
	}

	public String validUrl(final String url) {
		if (!StringUtils.isBlank(url) && !url.startsWith(HTTP) && !url.startsWith(HTTPS)) {
			return HTTPS + url;
		}
		return url;
	}

	private String buildBaseUrl(HttpServletRequest request, MerchantStore store) {
		String contextPath = normalizePath(request.getContextPath());
		String scheme = getScheme(store);
		return scheme + DOUBLE_SLASH + contextPath;
	}

	public String buildBaseUrl(String contextPath, MerchantStore store) {
		String normalizePath = normalizePath(contextPath);
		String scheme = getScheme(store);
		return scheme + SLASH + normalizePath;
	}

	/**
	 * Builds the base URL (ending with a slash) of a link sent by email, such
	 * as a password reset link, from a client supplied return URL. The return
	 * URL may be blank (store domain), a relative path on the store domain, or
	 * an absolute http(s) URL whose origin is the store domain or one of the
	 * origins listed in PASSWORD_RESET_ALLOWED_ORIGINS, optionally with a hash
	 * route fragment. Anything else, and any query, user info or unusual path
	 * character, is rejected.
	 */
	public String buildTrustedReturnBaseUrl(String returnUrl, MerchantStore store) {
		String storeOrigin = storeOrigin(store);

		if (StringUtils.isBlank(returnUrl)) {
			if (storeOrigin == null) {
				throw invalidReturnUrl();
			}
			return storeOrigin + SLASH;
		}

		URI uri;
		try {
			uri = new URI(returnUrl.trim());
		} catch (URISyntaxException e) {
			throw invalidReturnUrl();
		}

		if (uri.isOpaque() || uri.getRawUserInfo() != null || uri.getRawQuery() != null) {
			throw invalidReturnUrl();
		}

		String origin;
		if (uri.getScheme() == null && uri.getRawAuthority() == null) {
			origin = storeOrigin;
		} else {
			origin = origin(uri);
			if (origin == null || !(origin.equals(storeOrigin) || allowedReturnOrigins().contains(origin))) {
				throw invalidReturnUrl();
			}
		}
		if (origin == null) {
			throw invalidReturnUrl();
		}

		String path = safePath(uri.getRawPath());
		if (uri.getRawFragment() == null) {
			return origin + path;
		}
		return origin + path + "#" + safePath(uri.getRawFragment());
	}

	private static String safePath(String rawPath) {
		String path = StringUtils.defaultString(rawPath);
		if (!SAFE_URL_PATH.matcher(path).matches()) {
			throw invalidReturnUrl();
		}
		if (!path.startsWith(SLASH)) {
			path = SLASH + path;
		}
		if (!path.endsWith(SLASH)) {
			path = path + SLASH;
		}
		return path;
	}

	private Set<String> allowedReturnOrigins() {
		Set<String> origins = new HashSet<>();
		String configured = properties.getProperty(PASSWORD_RESET_ALLOWED_ORIGINS);
		if (StringUtils.isBlank(configured)) {
			return origins;
		}
		for (String value : configured.split(",")) {
			if (StringUtils.isBlank(value)) {
				continue;
			}
			try {
				String origin = origin(new URI(value.trim()));
				if (origin != null) {
					origins.add(origin);
				}
			} catch (URISyntaxException e) {
				// ignore malformed entries
			}
		}
		return origins;
	}

	private String storeOrigin(MerchantStore store) {
		String domain = StringUtils.removeEnd(getDomainName(store.getDomainName()).trim(), SLASH);
		if (!domain.startsWith(HTTP) && !domain.startsWith(HTTPS)) {
			String scheme = properties.getProperty(Constants.SHOP_SCHEME);
			domain = (StringUtils.isBlank(scheme) ? "https" : scheme.trim()) + DOUBLE_SLASH + domain;
		}
		try {
			return origin(new URI(domain));
		} catch (URISyntaxException e) {
			return null;
		}
	}

	private static String origin(URI uri) {
		String scheme = uri.getScheme();
		String host = uri.getHost();
		if (scheme == null || host == null || uri.getRawUserInfo() != null) {
			return null;
		}
		scheme = scheme.toLowerCase(Locale.ROOT);
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			return null;
		}
		int port = uri.getPort();
		boolean defaultPort = port == -1 || ("http".equals(scheme) && port == 80)
				|| ("https".equals(scheme) && port == 443);
		return scheme + DOUBLE_SLASH + host.toLowerCase(Locale.ROOT) + (defaultPort ? BLANK : ":" + port);
	}

	private static ServiceRuntimeException invalidReturnUrl() {
		String message = "Return url is not allowed for this store";
		return new ServiceRuntimeException("400", message, new IllegalArgumentException(message));
	}

	/**
	 * Requires web server headers to build image URL for social media
	 * sharing.<br/>
	 *
	 * Nginx configuration example:
	 * 
	 * <pre>
	 *     proxy_set_header X-Forwarded-Proto $scheme;
	 *     proxy_set_header X-Forwarded-Host $scheme://$host;
	 *     proxy_set_header X-Forwarded-Server $host;
	 * </pre>
	 * 
	 * @param merchantStore
	 * @param request
	 * @return
	 */
	public String buildStoreForwardedUri(MerchantStore merchantStore, HttpServletRequest request) {
		String uri;
		if (StringUtils.isNotEmpty(request.getHeader(X_FORWARDED_HOST))) {
			uri = request.getHeader(X_FORWARDED_HOST);
		} else {
			uri = buildStoreUri(merchantStore, request);
		}
		return uri;
	}
	
	public boolean isValidURL(String urlString)
	{
	    try
	    {
	        URL url = new URL(urlString);
	        url.toURI();
	        return true;
	    } catch (Exception exception)
	    {
	        return false;
	    }
	}
}
