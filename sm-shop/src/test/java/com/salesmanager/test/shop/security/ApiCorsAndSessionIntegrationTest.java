package com.salesmanager.test.shop.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;

@SpringBootTest(classes = ShopApplication.class, properties = {
		"cors.allowedOrigins=https://admin.acme.example", "cors.allowCredentials=false" })
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
public class ApiCorsAndSessionIntegrationTest {

	private static final String ALLOWED = "https://admin.acme.example";
	private static final String EVIL = "https://evil.example";

	@Autowired
	private MockMvc mockMvc;

	@Test
	public void evilOriginGetsNoCorsGrantOnPrivateApi() throws Exception {
		String token = adminToken();
		MvcResult result = mockMvc.perform(get("/api/v1/private/user/profile")
				.header("Origin", EVIL)
				.header("Authorization", "Bearer " + token)).andReturn();

		assertEquals(403, result.getResponse().getStatus());
		assertNull(result.getResponse().getHeader("Access-Control-Allow-Origin"));
		assertNull(result.getResponse().getHeader("Access-Control-Allow-Credentials"));
	}

	@Test
	public void evilOriginPreflightIsRejected() throws Exception {
		MvcResult result = mockMvc.perform(options("/api/v1/private/user/profile")
				.header("Origin", EVIL)
				.header("Access-Control-Request-Method", "GET")).andReturn();

		assertEquals(403, result.getResponse().getStatus());
		assertNull(result.getResponse().getHeader("Access-Control-Allow-Origin"));
	}

	@Test
	public void allowedOriginPreflightIsGranted() throws Exception {
		MvcResult result = mockMvc.perform(options("/api/v1/auth/customers/profile")
				.header("Origin", ALLOWED)
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "Authorization")).andReturn();

		assertEquals(200, result.getResponse().getStatus());
		assertEquals(ALLOWED, result.getResponse().getHeader("Access-Control-Allow-Origin"));
		assertNull(result.getResponse().getHeader("Access-Control-Allow-Credentials"));
	}

	@Test
	public void tokenAuthenticatedPrivateRequestCreatesNoSession() throws Exception {
		String token = adminToken();
		MvcResult result = mockMvc.perform(get("/api/v1/private/user/profile")
				.header("Origin", ALLOWED)
				.header("Authorization", "Bearer " + token)).andReturn();

		assertEquals(200, result.getResponse().getStatus());
		assertEquals(ALLOWED, result.getResponse().getHeader("Access-Control-Allow-Origin"));
		assertNull(result.getRequest().getSession(false));
		assertNull(result.getResponse().getCookie("JSESSIONID"));
	}

	@Test
	public void localeParameterCreatesNoSessionOnApi() throws Exception {
		String token = adminToken();
		MvcResult result = mockMvc.perform(get("/api/v1/private/user/profile")
				.param("locale", "de")
				.header("Authorization", "Bearer " + token)).andReturn();

		assertEquals(200, result.getResponse().getStatus());
		assertNull(result.getRequest().getSession(false));
		assertNull(result.getResponse().getCookie("JSESSIONID"));
	}

	@Test
	public void basicAuthIsNotAcceptedOnPrivateApi() throws Exception {
		String basic = Base64.getEncoder()
				.encodeToString("admin@shopizer.com:password".getBytes(StandardCharsets.UTF_8));
		MvcResult result = mockMvc.perform(get("/api/v1/private/user/profile")
				.header("Authorization", "Basic " + basic)).andReturn();

		assertEquals(401, result.getResponse().getStatus());
		assertNull(result.getResponse().getHeader("WWW-Authenticate"));
		assertNull(result.getRequest().getSession(false));
	}

	private String adminToken() throws Exception {
		MvcResult login = mockMvc.perform(post("/api/v1/private/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin@shopizer.com\",\"password\":\"password\"}")).andReturn();
		assertEquals(200, login.getResponse().getStatus());
		assertNull(login.getRequest().getSession(false));
		String token = new ObjectMapper().readTree(login.getResponse().getContentAsString()).get("token").asText();
		assertTrue(token.length() > 0);
		return token;
	}
}
