package com.salesmanager.test.user;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import com.salesmanager.core.business.repositories.user.PageableUserRepository;
import com.salesmanager.core.business.repositories.user.UserRepository;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.user.UserServiceImpl;
import com.salesmanager.core.model.user.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UserServiceImplTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private MerchantStoreService merchantStoreService;

	@Mock
	private PageableUserRepository pageableUserRepository;

	private UserServiceImpl userService;

	@Before
	public void setUp() throws Exception {
		userService = new UserServiceImpl(userRepository);
		for (Field field : UserServiceImpl.class.getDeclaredFields()) {
			if ("merchantStoreService".equals(field.getName())) {
				field.setAccessible(true);
				field.set(userService, merchantStoreService);
				break;
			}
		}
	}

	@Test
	public void findByStoreReturnsRepositoryUser() throws Exception {
		User expected = new User();
		when(userRepository.findByUserAndStore(5L, "STORE_A")).thenReturn(expected);

		User actual = userService.findByStore(5L, "STORE_A");

		assertSame(expected, actual);
		verify(userRepository).findByUserAndStore(5L, "STORE_A");
		verify(userRepository, never()).findOne(5L);
	}

	@Test
	public void findByStoreReturnsNullWhenUserIsNotInStore() throws Exception {
		when(userRepository.findByUserAndStore(5L, "STORE_A")).thenReturn(null);

		assertNull(userService.findByStore(5L, "STORE_A"));

		verify(userRepository).findByUserAndStore(5L, "STORE_A");
		verify(userRepository, never()).findOne(5L);
	}

	@Test
	public void findByStoreReturnsNullWithoutRepositoryInteractionForNullUserId() throws Exception {
		assertNull(userService.findByStore(null, "STORE_A"));

		verifyNoInteractions(userRepository);
	}

	@Test
	public void findByStoreReturnsNullWithoutRepositoryInteractionForBlankStoreCode() throws Exception {
		assertNull(userService.findByStore(5L, " "));
		assertNull(userService.findByStore(5L, null));

		verifyNoInteractions(userRepository);
	}
}
