package com.salesmanager.test.business.modules.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.salesmanager.core.business.modules.aws.AwsRegions;

import software.amazon.awssdk.regions.Region;

class AwsRegionsTest {

	@Test
	void acceptsRegionIds() {
		assertEquals(Region.CA_CENTRAL_1, AwsRegions.of("ca-central-1", "us-east-1"));
	}

	@Test
	void acceptsEnumStyleNames() {
		assertEquals(Region.US_EAST_1, AwsRegions.of("US_EAST_1", null));
		assertEquals(Region.US_EAST_1, AwsRegions.of("us_east_1", null));
	}

	@Test
	void fallsBackToDefaultWhenBlank() {
		assertEquals(Region.US_EAST_1, AwsRegions.of(" ", "us-east-1"));
		assertEquals(Region.US_EAST_1, AwsRegions.of(null, "us-east-1"));
	}

}
