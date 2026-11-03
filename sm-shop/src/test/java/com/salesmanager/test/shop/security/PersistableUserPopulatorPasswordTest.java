package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.populator.user.PersistableUserPopulator;

public class PersistableUserPopulatorPasswordTest {

  private PasswordEncoder passwordEncoder;
  private PersistableUserPopulator populator;
  private Language language;
  private MerchantStore store;

  @Before
  public void setUp() throws Exception {
    passwordEncoder = new BCryptPasswordEncoder();
    populator = new PersistableUserPopulator();
    language = mock(Language.class);
    store = new MerchantStore();

    LanguageService languageService = mock(LanguageService.class);
    when(languageService.getByCode("en")).thenReturn(language);
    GroupService groupService = mock(GroupService.class);
    when(groupService.listGroupByNames(Collections.<String>emptyList())).thenReturn(Collections.emptyList());

    ReflectionTestUtils.setField(populator, "languageService", languageService);
    ReflectionTestUtils.setField(populator, "groupService", groupService);
    ReflectionTestUtils.setField(populator, "merchantStoreService", mock(MerchantStoreService.class));
    ReflectionTestUtils.setField(populator, "passwordEncoder", passwordEncoder);
  }

  @Test
  public void newTargetGetsEncodedPasswordAndChangeDate() throws Exception {
    User target = populate("Password1", null);

    assertTrue(passwordEncoder.matches("Password1", target.getAdminPassword()));
    assertNotNull(target.getPasswordChangedDate());
  }

  @Test
  public void matchingPasswordPreservesHashAndChangeDate() throws Exception {
    String existingHash = passwordEncoder.encode("Password1");
    Date oldDate = new Date(1600000000000L);
    User target = new User();
    target.setAdminPassword(existingHash);
    target.setPasswordChangedDate(oldDate);

    populate("Password1", target);

    assertEquals(existingHash, target.getAdminPassword());
    assertEquals(oldDate, target.getPasswordChangedDate());
  }

  @Test
  public void changedPasswordGetsNewHashAndLaterChangeDate() throws Exception {
    String existingHash = passwordEncoder.encode("Password1");
    Date oldDate = new Date(1600000000000L);
    User target = new User();
    target.setAdminPassword(existingHash);
    target.setPasswordChangedDate(oldDate);

    populate("Password2", target);

    assertNotEquals(existingHash, target.getAdminPassword());
    assertTrue(passwordEncoder.matches("Password2", target.getAdminPassword()));
    assertTrue(target.getPasswordChangedDate().after(oldDate));
  }

  @Test
  public void nullAndBlankPasswordsPreserveHashAndChangeDate() throws Exception {
    String existingHash = passwordEncoder.encode("Password1");
    Date oldDate = new Date(1600000000000L);
    User target = new User();
    target.setAdminPassword(existingHash);
    target.setPasswordChangedDate(oldDate);

    populate(null, target);
    populate("", target);

    assertEquals(existingHash, target.getAdminPassword());
    assertEquals(oldDate, target.getPasswordChangedDate());
  }

  private User populate(String password, User target) throws Exception {
    PersistableUser source = new PersistableUser();
    source.setDefaultLanguage("en");
    source.setPassword(password);
    source.setActive(true);
    return populator.populate(source, target, store, language);
  }
}
