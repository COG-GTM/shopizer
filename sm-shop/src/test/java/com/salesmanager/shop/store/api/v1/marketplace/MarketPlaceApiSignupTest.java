package com.salesmanager.shop.store.api.v1.marketplace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.salesmanager.shop.store.api.exception.RestErrorHandler;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

public class MarketPlaceApiSignupTest {

	private static final String EXPECTED_BODY = "{\"errorCode\":\"501\",\"message\":\"Store signup is not available\"}";

	private MockMvc mockMvc;

	@Before
	public void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new MarketPlaceApi())
				.setControllerAdvice(new RestErrorHandler())
				.build();
	}

	@Test
	public void signupResponseDoesNotDependOnInput() throws Exception {
		MockHttpServletResponse adminEmail = signup("{\"email\":\"admin@shopizer.com\",\"code\":\"DEFAULT\"}");
		MockHttpServletResponse unknownEmail = signup("{\"email\":\"nobody@example.invalid\",\"code\":\"NO_SUCH_STORE\"}");
		MockHttpServletResponse malformed = signup("{not json");
		MockHttpServletResponse empty = signup("");

		for (MockHttpServletResponse response : new MockHttpServletResponse[] { adminEmail, unknownEmail, malformed, empty }) {
			assertEquals(501, response.getStatus());
			assertEquals(EXPECTED_BODY, response.getContentAsString());
		}
	}

	@Test
	public void signupTokenVerificationIsNotImplemented() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/api/v1/store/DEFAULT/signup/any-token"))
				.andReturn().getResponse();
		assertEquals(501, response.getStatus());
		assertEquals(EXPECTED_BODY, response.getContentAsString());
	}

	@Test
	public void controllerHasNoUserOrStoreLookup() {
		for (Field field : MarketPlaceApi.class.getDeclaredFields()) {
			assertFalse(field.getName(), UserFacade.class.isAssignableFrom(field.getType()));
			assertFalse(field.getName(), StoreFacade.class.isAssignableFrom(field.getType()));
		}
	}

	private MockHttpServletResponse signup(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/store/signup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andReturn().getResponse();
	}
}
