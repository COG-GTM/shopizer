package com.salesmanager.shop.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.constants.Constants;

class UserServicesImplTest {

	private final UserService userService = mock(UserService.class);
	private final MerchantStoreService merchantStoreService = mock(MerchantStoreService.class);
	private final GroupService groupService = mock(GroupService.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final UserServicesImpl service = new UserServicesImpl();

	@BeforeEach
	void setUp() throws Exception {
		ReflectionTestUtils.setField(service, "userService", userService);
		ReflectionTestUtils.setField(service, "merchantStoreService", merchantStoreService);
		ReflectionTestUtils.setField(service, "groupService", groupService);
		ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
		when(merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE)).thenReturn(new MerchantStore());
		when(groupService.listGroup(GroupType.ADMIN)).thenReturn(Arrays.asList(
				new Group(Constants.GROUP_SUPERADMIN), new Group(Constants.GROUP_ADMIN)));
		when(passwordEncoder.encode(anyString())).thenAnswer(i -> "{enc}" + i.getArgument(0));
	}

	@Test
	void usesConfiguredInitialPassword() throws Exception {
		ReflectionTestUtils.setField(service, "configuredInitialPassword", "Correct-Horse-9");

		assertEquals("{enc}Correct-Horse-9", createAdmin().getAdminPassword());
	}

	@Test
	void generatesRandomPasswordWhenNotConfigured() throws Exception {
		String first = createAdmin().getAdminPassword();
		String second = createAdmin().getAdminPassword();

		assertNotEquals("{enc}password", first);
		assertEquals("{enc}".length() + DefaultAdminPassword.GENERATED_LENGTH, first.length());
		assertNotEquals(first, second);
	}

	@Test
	void ignoresWeakConfiguredPassword() throws Exception {
		for (String weak : new String[] { "password", "PASSWORD", "admin", "Short1!", "   " }) {
			ReflectionTestUtils.setField(service, "configuredInitialPassword", weak);

			assertNotEquals("{enc}" + weak, createAdmin().getAdminPassword());
		}
	}

	@Test
	void adminKeepsUserNameAndGroups() throws Exception {
		User admin = createAdmin();

		assertEquals(UserServicesImpl.DEFAULT_ADMIN_USER, admin.getAdminName());
		assertEquals(2, admin.getGroups().size());
	}

	@Test
	void acceptsOnlyStrongConfiguredPasswords() {
		assertTrue(DefaultAdminPassword.isAcceptable("Correct-Horse-9"));
		assertFalse(DefaultAdminPassword.isAcceptable(null));
		assertFalse(DefaultAdminPassword.isAcceptable("password"));
		assertFalse(DefaultAdminPassword.isAcceptable("admin@shopizer.com"));
		assertFalse(DefaultAdminPassword.isAcceptable("Abcdefgh123"));
		assertTrue(DefaultAdminPassword.generate().chars().allMatch(Character::isLetterOrDigit));
	}

	private User createAdmin() throws Exception {
		org.mockito.Mockito.clearInvocations(userService);
		service.createDefaultAdmin();
		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userService).create(captor.capture());
		return captor.getValue();
	}

}
