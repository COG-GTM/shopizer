package com.salesmanager.test.business.user;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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

	private UserServiceImpl userService;

	@Before
	public void setUp() throws Exception {
		userService = new UserServiceImpl(userRepository);
		try {
			Field field = UserServiceImpl.class.getDeclaredField("merchantStoreService");
			field.setAccessible(true);
			field.set(userService, merchantStoreService);
			when(merchantStoreService.isStoreInGroup(anyString())).thenReturn(true);
		} catch (NoSuchFieldException ignored) {
		}
	}

	@Test
	public void nullUserIdReturnsNullWithoutQueryingRepository() throws Exception {
		assertNull(userService.findByStore(null, "DEFAULT"));
		verifyNoInteractions(userRepository);
	}

	@Test
	public void nullStoreCodeReturnsNullWithoutQueryingRepository() throws Exception {
		assertNull(userService.findByStore(1L, null));
		verifyNoInteractions(userRepository);
	}

	@Test
	public void emptyStoreCodeReturnsNullWithoutQueryingRepository() throws Exception {
		assertNull(userService.findByStore(1L, ""));
		verifyNoInteractions(userRepository);
	}

	@Test
	public void blankStoreCodeReturnsNullWithoutQueryingRepository() throws Exception {
		assertNull(userService.findByStore(1L, "  "));
		verifyNoInteractions(userRepository);
	}

	@Test
	public void returnsUserFoundInRequestedStore() throws Exception {
		User expected = new User();
		when(userRepository.findByUserId(1L, "DEFAULT")).thenReturn(expected);

		assertSame(expected, userService.findByStore(1L, "DEFAULT"));

		verify(userRepository).findByUserId(1L, "DEFAULT");
		verify(userRepository, never()).findOne(anyLong());
	}

	@Test
	public void returnsNullWhenUserIsNotInRequestedStore() throws Exception {
		when(userRepository.findByUserId(1L, "globex")).thenReturn(null);

		assertNull(userService.findByStore(1L, "globex"));

		verify(userRepository).findByUserId(1L, "globex");
		verify(userRepository, never()).findOne(anyLong());
	}
}
