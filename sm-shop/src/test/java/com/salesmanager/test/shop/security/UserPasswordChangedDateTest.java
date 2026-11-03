package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Date;

import org.junit.Test;

import com.salesmanager.core.model.user.User;

public class UserPasswordChangedDateTest {

  @Test
  public void nullAndSamePasswordDoNotChangeDateAndNewPasswordAdvancesSecondPrecisionDate() {
    User user = new User();
    user.setAdminPassword("initial-hash");
    Date initialDate = user.getPasswordChangedDate();
    assertNotNull(initialDate);

    user.setAdminPassword(null);
    assertEquals(initialDate, user.getPasswordChangedDate());

    user.setAdminPassword("initial-hash");
    Date sameValueDate = user.getPasswordChangedDate();
    user.setAdminPassword("initial-hash");
    assertEquals(sameValueDate, user.getPasswordChangedDate());

    Date futureDate = new Date((System.currentTimeMillis() / 1000L) * 1000L + 10000L);
    user.setPasswordChangedDate(futureDate);
    user.setAdminPassword("new-hash");

    assertEquals(futureDate.getTime() + 1000L, user.getPasswordChangedDate().getTime());
    assertEquals(0L, user.getPasswordChangedDate().getTime() % 1000L);
    assertEquals(0L, initialDate.getTime() % 1000L);
  }
}
