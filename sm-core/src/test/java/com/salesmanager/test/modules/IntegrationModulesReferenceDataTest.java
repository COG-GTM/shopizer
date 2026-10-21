package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class IntegrationModulesReferenceDataTest {

  @Test
  public void keepsPaypalCheckoutUrlsAndStripeAndBraintreeModules() throws Exception {
    InputStream source = getClass().getResourceAsStream("/reference/integrationmodules.json");
    try {
      JsonNode modules = new ObjectMapper().readTree(source);
      Map<String, JsonNode> byCode = new HashMap<String, JsonNode>();
      for (JsonNode module : modules) {
        byCode.put(module.path("code").asText(), module);
      }

      JsonNode paypal = byCode.get("paypal-express-checkout");
      assertEquals("https://www.sandbox.paypal.com/checkoutnow?token=",
          environment(paypal, "TEST").path("config1").asText());
      assertEquals("https://www.paypal.com/checkoutnow?token=",
          environment(paypal, "PROD").path("config1").asText());
      assertTrue(byCode.containsKey("stripe"));
      assertTrue(byCode.containsKey("stripe3"));
      assertTrue(byCode.containsKey("braintree"));
    } finally {
      if (source != null) {
        source.close();
      }
    }
  }

  private JsonNode environment(JsonNode module, String name) {
    for (JsonNode configuration : module.path("configuration")) {
      if (name.equals(configuration.path("env").asText())) {
        return configuration;
      }
    }
    throw new AssertionError("Missing configuration environment " + name);
  }
}
