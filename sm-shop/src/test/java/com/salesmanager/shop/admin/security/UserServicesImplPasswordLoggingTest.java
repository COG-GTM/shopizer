package com.salesmanager.shop.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.constants.Constants;

class UserServicesImplPasswordLoggingTest {

	private final UserService userService = mock(UserService.class);
	private final MerchantStoreService merchantStoreService = mock(MerchantStoreService.class);
	private final GroupService groupService = mock(GroupService.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final UserServicesImpl service = new UserServicesImpl();
	private final Logger logger = (Logger) LoggerFactory.getLogger(UserServicesImpl.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void setUp() throws Exception {
		ReflectionTestUtils.setField(service, "userService", userService);
		ReflectionTestUtils.setField(service, "merchantStoreService", merchantStoreService);
		ReflectionTestUtils.setField(service, "groupService", groupService);
		ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
		when(merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE)).thenReturn(new MerchantStore());
		when(groupService.listGroup(GroupType.ADMIN)).thenReturn(Arrays.asList(
				new Group(Constants.GROUP_SUPERADMIN), new Group(Constants.GROUP_ADMIN)));
		when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "{enc}" + invocation.getArgument(0));
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(appender);
		appender.stop();
	}

	@Test
	void configuredPasswordIsEncodedOnceAndNeverLogged() throws Exception {
		String configuredPassword = "Sh0pizer-Test-Admin-1";
		ReflectionTestUtils.setField(service, "configuredInitialPassword", configuredPassword);

		createAdmin();

		ArgumentCaptor<String> encodedPassword = ArgumentCaptor.forClass(String.class);
		verify(passwordEncoder, times(1)).encode(encodedPassword.capture());
		assertEquals(configuredPassword, encodedPassword.getValue());
		assertTrue(appender.list.stream()
				.noneMatch(event -> event.getFormattedMessage().contains(configuredPassword)));
	}

	@Test
	void generatedPasswordAppearsInExactlyOneWarning() throws Exception {
		ReflectionTestUtils.setField(service, "configuredInitialPassword", "");

		createAdmin();

		ArgumentCaptor<String> encodedPassword = ArgumentCaptor.forClass(String.class);
		verify(passwordEncoder, times(1)).encode(encodedPassword.capture());
		String generatedPassword = encodedPassword.getValue();
		assertEquals(DefaultAdminPassword.GENERATED_LENGTH, generatedPassword.length());
		assertTrue(DefaultAdminPassword.isAcceptable(generatedPassword));
		assertEquals(1, appender.list.stream()
				.filter(event -> event.getLevel() == Level.WARN)
				.filter(event -> event.getFormattedMessage().contains(generatedPassword))
				.count());
	}

	@Test
	void weakConfiguredPasswordIsIgnoredAndNeverLogged() throws Exception {
		String weakPassword = "123456789012";
		ReflectionTestUtils.setField(service, "configuredInitialPassword", weakPassword);

		createAdmin();

		ArgumentCaptor<String> encodedPassword = ArgumentCaptor.forClass(String.class);
		verify(passwordEncoder, times(1)).encode(encodedPassword.capture());
		assertNotEquals(weakPassword, encodedPassword.getValue());
		assertTrue(appender.list.stream()
				.anyMatch(event -> event.getLevel() == Level.WARN
						&& event.getFormattedMessage().contains("Ignoring shopizer.admin.initial-password")));
		assertFalse(appender.list.stream()
				.anyMatch(event -> event.getFormattedMessage().contains(weakPassword)));
	}

	private User createAdmin() throws Exception {
		service.createDefaultAdmin();
		ArgumentCaptor<User> admin = ArgumentCaptor.forClass(User.class);
		verify(userService).create(admin.capture());
		return admin.getValue();
	}
}
