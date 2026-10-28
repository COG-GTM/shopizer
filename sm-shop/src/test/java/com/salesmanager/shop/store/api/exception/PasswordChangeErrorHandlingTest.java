package com.salesmanager.shop.store.api.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class PasswordChangeErrorHandlingTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionController())
				.setControllerAdvice(new RestErrorHandler())
				.build();
	}

	@Test
	void defaultForbiddenExceptionUsesItsDefaultMessage() throws Exception {
		mockMvc.perform(get("/errors/forbidden-default"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("403"))
				.andExpect(jsonPath("$.message").value("Forbidden"));
	}

	@Test
	void forbiddenExceptionReturnsItsMessage() throws Exception {
		mockMvc.perform(get("/errors/forbidden"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("403"))
				.andExpect(jsonPath("$.message").value("forbidden message"));
	}

	@Test
	void tooManyRequestsExceptionReturnsItsMessage() throws Exception {
		mockMvc.perform(get("/errors/too-many-requests"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorCode").value("429"))
				.andExpect(jsonPath("$.message").value("too many requests"));
	}

	@Test
	void resourceNotFoundExceptionReturnsNotFound() throws Exception {
		mockMvc.perform(get("/errors/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("404"))
				.andExpect(jsonPath("$.message").value("missing resource"));
	}

	@Test
	void serviceRuntimeExceptionReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/errors/service-runtime"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("service error")));
	}

	@RestController
	static class ExceptionController {

		@GetMapping("/errors/forbidden-default")
		void forbiddenDefault() {
			throw new ForbiddenException();
		}

		@GetMapping("/errors/forbidden")
		void forbidden() {
			throw new ForbiddenException("forbidden message");
		}

		@GetMapping("/errors/too-many-requests")
		void tooManyRequests() {
			throw new TooManyRequestsException("too many requests");
		}

		@GetMapping("/errors/not-found")
		void notFound() {
			throw new ResourceNotFoundException("missing resource");
		}

		@GetMapping("/errors/service-runtime")
		void serviceRuntime() {
			throw new ServiceRuntimeException("400", "service error", new IllegalStateException("root cause"));
		}
	}
}
