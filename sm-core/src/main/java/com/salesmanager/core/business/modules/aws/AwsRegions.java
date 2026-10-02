package com.salesmanager.core.business.modules.aws;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import software.amazon.awssdk.regions.Region;

/**
 * Resolves AWS regions from configuration values written either as region ids
 * ({@code us-east-1}) or as enum-style names ({@code US_EAST_1}).
 */
public final class AwsRegions {

	private AwsRegions() {
	}

	public static Region of(String name, String defaultName) {
		String value = StringUtils.isBlank(name) ? defaultName : name;
		return Region.of(value.trim().toLowerCase(Locale.ROOT).replace('_', '-'));
	}

}
