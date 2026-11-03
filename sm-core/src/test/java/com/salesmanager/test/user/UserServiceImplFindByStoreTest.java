package com.salesmanager.test.user;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.repositories.user.UserRepository;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.user.UserServiceImpl;
import com.salesmanager.core.model.user.User;

@RunWith(MockitoJUnitRunner.class)
public class UserServiceImplFindByStoreTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private MerchantStoreService merchantStoreService;

	@InjectMocks
	private UserServiceImpl userService;

	@Before
	public void injectFieldMock() {
		ReflectionTestUtils.setField(userService, "merchantStoreService", merchantStoreService);
	}

	@Test
	public void returnsUserFromRepositoryStoreScopedLookup() throws Exception {
		User user = new User();
		when(userRepository.findByUserAndStore(42L, "authz-unit")).thenReturn(user);

		assertSame(user, userService.findByStore(42L, "authz-unit"));

		verify(userRepository).findByUserAndStore(42L, "authz-unit");
		verify(userRepository, never()).findOne(42L);
		verifyNoInteractions(merchantStoreService);
	}

	@Test
	public void returnsNullWhenRepositoryFindsNoUserInStore() throws Exception {
		when(userRepository.findByUserAndStore(42L, "authz-unit")).thenReturn(null);

		assertNull(userService.findByStore(42L, "authz-unit"));

		verify(userRepository).findByUserAndStore(42L, "authz-unit");
		verify(userRepository, never()).findOne(42L);
		verifyNoInteractions(merchantStoreService);
	}
}
