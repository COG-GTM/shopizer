package com.salesmanager.test.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.init.InitializationDatabase;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.test.configuration.ConfigurationTest;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigurationTest.class)
public class UserFindByStoreIntegrationTest {

	private static final String SECOND_STORE_CODE = "authz-core-other";
	private static final String USER_NAME = "authz-core-user@test.com";

	@Inject
	private InitializationDatabase initializationDatabase;

	@Inject
	private MerchantStoreService merchantStoreService;

	@Inject
	private UserService userService;

	@Inject
	private GroupService groupService;

	@Before
	public void setUp() throws Exception {
		if (initializationDatabase.isEmpty()) {
			initializationDatabase.populate("TEST");
		}

		MerchantStore defaultStore = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);
		if (merchantStoreService.getByCode(SECOND_STORE_CODE) == null) {
			MerchantStore secondStore = new MerchantStore();
			secondStore.setCode(SECOND_STORE_CODE);
			secondStore.setStorename(SECOND_STORE_CODE);
			secondStore.setStoreEmailAddress("authz-core-other@test.com");
			secondStore.setStorephone(defaultStore.getStorephone());
			secondStore.setStorecity(defaultStore.getStorecity());
			secondStore.setStorepostalcode(defaultStore.getStorepostalcode());
			secondStore.setCountry(defaultStore.getCountry());
			secondStore.setCurrency(defaultStore.getCurrency());
			secondStore.setDefaultLanguage(defaultStore.getDefaultLanguage());
			secondStore.setLanguages(defaultStore.getLanguages());
			merchantStoreService.saveOrUpdate(secondStore);
		}

		if (userService.getByUserName(USER_NAME, MerchantStore.DEFAULT_STORE) == null) {
			User user = new User();
			user.setAdminName(USER_NAME);
			user.setAdminEmail(USER_NAME);
			user.setAdminPassword("not-used-by-test");
			user.setFirstName("Authz");
			user.setLastName("Core");
			user.setMerchantStore(defaultStore);
			user.setDefaultLanguage(defaultStore.getDefaultLanguage());
			user.setActive(true);
			Group admin = groupService.findByName("ADMIN");
			assertNotNull(admin);
			user.getGroups().add(admin);
			userService.saveOrUpdate(user);
		}
	}

	@Test
	public void findsOnlyUsersBelongingToRequestedStore() throws ServiceException {
		User user = userService.getByUserName(USER_NAME, MerchantStore.DEFAULT_STORE);
		assertNotNull(user);

		User inDefault = userService.findByStore(user.getId(), MerchantStore.DEFAULT_STORE);
		assertNotNull(inDefault);
		assertEquals(USER_NAME, inDefault.getAdminName());
		assertNotNull(inDefault.getGroups());
		assertEquals("ADMIN", inDefault.getGroups().get(0).getGroupName());

		assertNull(userService.findByStore(user.getId(), SECOND_STORE_CODE));
		assertNull(userService.findByStore(Long.MAX_VALUE, MerchantStore.DEFAULT_STORE));
	}
}
