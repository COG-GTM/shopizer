package com.salesmanager.test.shop.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.populator.customer.CustomerPopulator;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CustomerFacadeImplRegisterTest {

	@Mock
	private CustomerService customerService;
	@Mock
	private CustomerPopulator customerPopulator;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private GroupService groupService;

	@InjectMocks
	private CustomerFacadeImpl facade;

	private MerchantStore store;
	private Language language;
	private final AtomicReference<Long> idSeenByPopulator = new AtomicReference<>(-1L);

	@Before
	public void setUp() throws Exception {
		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");
		language = new Language("en");

		when(customerService.getByNick(anyString())).thenReturn(null);
		when(passwordEncoder.encode(anyString())).thenReturn("ENC");
		when(customerPopulator.populate(any(PersistableCustomer.class), any(MerchantStore.class), any(Language.class)))
				.thenAnswer(i -> {
					PersistableCustomer source = i.getArgument(0);
					idSeenByPopulator.set(source.getId());
					Customer model = new Customer();
					model.setEmailAddress(source.getEmailAddress());
					return model;
				});
		doAnswer(i -> {
			Customer c = i.getArgument(0);
			c.setId(c.getId() == null ? 1001L : c.getId());
			return null;
		}).when(customerService).saveOrUpdate(any(Customer.class));
	}

	private PersistableCustomer request(Long id) {
		PersistableCustomer c = new PersistableCustomer();
		c.setId(id);
		c.setEmailAddress("attacker@test.com");
		c.setUserName("attacker@test.com");
		c.setPassword("attacker123");
		return c;
	}

	@Test
	public void registerClearsClientSuppliedIdBeforePopulating() throws Exception {
		PersistableCustomer result = facade.registerCustomer(request(5L), store, language);
		assertNull("populator must not see the client supplied id", idSeenByPopulator.get());
		assertEquals(Long.valueOf(1001L), result.getId());
	}

	@Test
	public void registerWithoutIdAssignsGeneratedId() throws Exception {
		PersistableCustomer result = facade.registerCustomer(request(null), store, language);
		assertNull(idSeenByPopulator.get());
		assertEquals(Long.valueOf(1001L), result.getId());
	}

	@Test(expected = com.salesmanager.shop.model.customer.UserAlreadyExistException.class)
	public void registerExistingUserNameIsRejectedAndNothingSaved() throws Exception {
		when(customerService.getByNick("attacker@test.com")).thenReturn(new Customer());
		try {
			facade.registerCustomer(request(5L), store, language);
		} finally {
			verify(customerService, never()).saveOrUpdate(any(Customer.class));
		}
	}
}
