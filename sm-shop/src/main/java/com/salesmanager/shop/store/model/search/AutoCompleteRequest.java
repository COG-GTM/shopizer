package com.salesmanager.shop.store.model.search;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class AutoCompleteRequest {
	
	private String merchantCode;
	private String languageCode;
	
	
	private final static String WILDCARD_QUERY = "wildcard";
	private final static String KEYWORD = "keyword";
	private final static String UNDERSCORE = "_";
	private final static String ALL = "*";
	private final static String TYPE = "type";
	private final static String ANALYZER = "analyzer";
	private final static String STD_ANALYZER = "standard";
	private final static String TYPE_PHRASE = "phrase_prefix";
	private final static String QUERY = "query";
	private final static String MATCH = "match";
	
	public AutoCompleteRequest(String merchantCode, String languageCode) {
		this.merchantCode = merchantCode;
		this.languageCode = languageCode;
	}

	@SuppressWarnings("unchecked")
	@Deprecated
	public String toJSONString(String query) {

		//{"size": 10,"query": {"match": {"keyword": {"query": "wat","operator":"and"}}}}";

		
		
		ObjectNode keyword = JsonNodeFactory.instance.objectNode();
		ObjectNode q = JsonNodeFactory.instance.objectNode();
		ObjectNode mq = JsonNodeFactory.instance.objectNode();
		ObjectNode match = JsonNodeFactory.instance.objectNode();
		
		q.put(QUERY, query);
		q.put(ANALYZER, STD_ANALYZER);
		
		keyword.set(KEYWORD, q);
		
		match.set(MATCH, keyword);
		
		mq.set(QUERY, match);
		return mq.toString();
	}
	
	/** keyword_en_default **/
	public String getCollectionName() {
		StringBuilder qBuilder = new StringBuilder();
		qBuilder.append(KEYWORD).append(UNDERSCORE).append(getLanguageCode()).append(UNDERSCORE)
		.append(getMerchantCode());
		
		return qBuilder.toString().toLowerCase();
	}
	
	

	public String getMerchantCode() {
		return merchantCode;
	}

	public void setMerchantCode(String merchantCode) {
		this.merchantCode = merchantCode;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}

}
