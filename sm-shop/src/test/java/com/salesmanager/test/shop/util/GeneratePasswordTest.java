package com.salesmanager.test.shop.util;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;


/**
 * This utility is for password encryption
 * @author carlsamson
 *
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class GeneratePasswordTest extends ServicesTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratePasswordTest.class);

  @Inject
  @Named("passwordEncoder")
  private PasswordEncoder passwordEncoder;
  
  @Test
  public void createPassword() throws Exception {
 

      String password ="password";
      String encoded = passwordEncoder.encode(password);
      LOGGER.info(encoded);
      System.out.println(encoded);
      //To comply with sonarlint rule java:S2699
      Assertions.assertNotNull(encoded);
  }



}
