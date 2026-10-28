package com.salesmanager.shop.store.api.v1.marketplace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.store.api.exception.RestErrorHandler;
import com.salesmanager.shop.store.controller.marketplace.facade.MarketPlaceFacade;
import com.salesmanager.shop.utils.LanguageUtils;

public class MarketPlaceApiSignupContractTest {

	private static final String EXPECTED_BODY =
			"{\"errorCode\":\"501\",\"message\":\"Store signup is not available\"}";

	private MockMvc mockMvc;
	private MarketPlaceFacade marketPlaceFacade;
	private LanguageUtils languageUtils;
	private Language english;

	@Before
	public void setUp() {
		marketPlaceFacade = mock(MarketPlaceFacade.class);
		languageUtils = mock(LanguageUtils.class);
		english = mock(Language.class);
		org.mockito.Mockito.when(languageUtils.getServiceLanguage("en")).thenReturn(english);

		MarketPlaceApi controller = new MarketPlaceApi();
		ReflectionTestUtils.setField(controller, "marketPlaceFacade", marketPlaceFacade);
		ReflectionTestUtils.setField(controller, "languageUtils", languageUtils);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new RestErrorHandler())
				.build();
	}

	@Test
	public void signupIgnoresContentTypeBodyAndQueryParameters() throws Exception {
		MockHttpServletResponse text = signup(MediaType.TEXT_PLAIN, "not json", "");
		MockHttpServletResponse form = signup(MediaType.APPLICATION_FORM_URLENCODED,
				"email=admin%40shopizer.com&code=DEFAULT", "");
		MockHttpServletResponse noContentType = signup(null, "email=admin@shopizer.com&code=DEFAULT", "");
		MockHttpServletResponse jsonWithQuery = signup(MediaType.APPLICATION_JSON,
				"{\"email\":\"admin@shopizer.com\",\"code\":\"DEFAULT\"}", "?store=DEFAULT&lang=fr");

		for (MockHttpServletResponse response : new MockHttpServletResponse[] {
				text, form, noContentType, jsonWithQuery }) {
			assertSignupResponse(response);
		}
		verifyNoMoreInteractions(marketPlaceFacade);
	}

	@Test
	public void verificationReturnsSameResponseForDifferentStoresAndTokens() throws Exception {
		String longToken = new String(new char[200]).replace('\0', 'x');
		MockHttpServletResponse knownStore = verifySignup("DEFAULT", "abc");
		MockHttpServletResponse unknownStore = verifySignup("NO_SUCH_STORE", "xyz");
		MockHttpServletResponse longTokenResponse = verifySignup("DEFAULT", longToken);

		for (MockHttpServletResponse response : new MockHttpServletResponse[] {
				knownStore, unknownStore, longTokenResponse }) {
			assertSignupResponse(response);
		}
		verifyNoMoreInteractions(marketPlaceFacade);
	}

	@Test
	public void signupAndVerificationMethodsHaveNoParameters() throws Exception {
		Method signup = MarketPlaceApi.class.getDeclaredMethod("signup");
		Method verify = MarketPlaceApi.class.getDeclaredMethod("storeSignupVerify");

		assertEquals(0, signup.getParameterCount());
		assertEquals(0, verify.getParameterCount());
	}

	@Test
	public void marketplaceLookupStillUsesLanguageAndFacade() throws Exception {
		mockMvc.perform(get("/api/v1/private/marketplace/DEFAULT?lang=en"))
				.andExpect(status().isOk());

		verify(languageUtils).getServiceLanguage("en");
		verify(marketPlaceFacade).get("DEFAULT", english);
		verifyNoMoreInteractions(marketPlaceFacade);
	}

	private MockHttpServletResponse signup(MediaType contentType, String body, String query) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/v1/store/signup" + query).content(body);
		if (contentType != null) {
			request.contentType(contentType);
		}
		return mockMvc.perform(request).andReturn().getResponse();
	}

	private MockHttpServletResponse verifySignup(String store, String token) throws Exception {
		return mockMvc.perform(get("/api/v1/store/{store}/signup/{token}", store, token))
				.andReturn().getResponse();
	}

	private void assertSignupResponse(MockHttpServletResponse response) throws Exception {
		assertEquals(501, response.getStatus());
		assertEquals(EXPECTED_BODY, response.getContentAsString());
		assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(
				MediaType.parseMediaType(response.getContentType())));
	}
}
