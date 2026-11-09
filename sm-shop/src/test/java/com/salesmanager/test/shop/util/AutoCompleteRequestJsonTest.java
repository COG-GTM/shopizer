package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.store.model.search.AutoCompleteRequest;

public class AutoCompleteRequestJsonTest {

	@SuppressWarnings("deprecation")
	@Test
	void buildsMatchQueryWithEscapedText() throws Exception {
		AutoCompleteRequest req = new AutoCompleteRequest("DEFAULT", "en");
		String json = req.toJSONString("wat \"ch\" / é");
		JsonNode q = new ObjectMapper().readTree(json).get("query").get("match").get("keyword");
		assertEquals("wat \"ch\" / é", q.get("query").asText());
		assertEquals("standard", q.get("analyzer").asText());
		assertEquals("keyword_en_default", req.getCollectionName());
	}
}
