package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Environment;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.modules.integration.payment.impl.BraintreePayment;
import com.salesmanager.core.model.system.IntegrationConfiguration;

public class BraintreeGatewayEnvironmentTest {

  @Test
  public void mapsTestToSandboxAndProductionToProductionWithConfiguredKeys() {
    ExposedPayment payment = new ExposedPayment();
    IntegrationConfiguration configuration = new IntegrationConfiguration();
    Map<String, String> keys = new HashMap<String, String>();
    keys.put("merchant_id", "merchant-value");
    keys.put("public_key", "public-value");
    keys.put("private_key", "private-value");
    configuration.setIntegrationKeys(keys);

    configuration.setEnvironment(Constants.TEST_ENVIRONMENT);
    assertGateway(payment.gateway(configuration), Environment.SANDBOX);
    configuration.setEnvironment(Constants.PRODUCTION_ENVIRONMENT);
    assertGateway(payment.gateway(configuration), Environment.PRODUCTION);
  }

  private void assertGateway(BraintreeGateway gateway, Environment environment) {
    assertEquals(environment, gateway.getConfiguration().getEnvironment());
    assertEquals("/merchants/merchant-value", gateway.getConfiguration().getMerchantPath());
    assertEquals("public-value", gateway.getConfiguration().getPublicKey());
    assertEquals("private-value", gateway.getConfiguration().getPrivateKey());
  }

  private static class ExposedPayment extends BraintreePayment {
    protected BraintreeGateway gateway(IntegrationConfiguration configuration) {
      return super.gateway(configuration);
    }
  }
}
