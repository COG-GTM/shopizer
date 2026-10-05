package com.salesmanager.shop.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.utils.CoreConfiguration;
import com.salesmanager.shop.constants.ApplicationConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates a request to reCaptcha 2
 * https://www.google.com/recaptcha/api/siteverify
 * Throws an exception if it can't connect to reCaptcha
 * returns true or false if validation has passed
 * @author carlsamson
 *
 */
@Component
public class CaptchaRequestUtils {
	
	@Inject
	private CoreConfiguration configuration; //for reading public and secret key
	
	private static final String SUCCESS_INDICATOR = "success";
	
	  @Value("${config.recaptcha.secretKey}")
	  private String secretKey;
	
	public boolean checkCaptcha(String gRecaptchaResponse) throws Exception {

	    
	    String url = configuration.getProperty(ApplicationConstants.RECAPTCHA_URL);;

        List<NameValuePair> data = new ArrayList<NameValuePair>();
        data.add(new BasicNameValuePair("secret",  secretKey));
        data.add(new BasicNameValuePair("response",  gRecaptchaResponse));

	    
	    // Create a method instance.
        HttpPost post = new HttpPost(url);
	    post.setEntity(new UrlEncodedFormEntity(data,StandardCharsets.UTF_8));
	    
	    boolean checkCaptcha = false;
	    

	    try (CloseableHttpClient client = HttpClients.createDefault()) {
	      // Execute the method.
            byte[] responseBody = client.execute(post, httpResponse -> {
              if (httpResponse.getCode() != HttpStatus.SC_OK) {
                throw new IOException("Got an invalid response from reCaptcha " + url + " [" + httpResponse.getCode() + " " + httpResponse.getReasonPhrase() + "]");
              }
              return EntityUtils.toByteArray(httpResponse.getEntity());
            });


	      // Deal with the response.
	      // Use caution: ensure correct character encoding and is not binary data
	      //System.out.println(new String(responseBody));
	      
	      String json = new String(responseBody);
	      
	      Map<String,String> map = new HashMap<String,String>();
	  	  ObjectMapper mapper = new ObjectMapper();
	  	  
	  	  map = mapper.readValue(json, 
			    new TypeReference<HashMap<String,String>>(){});
	  	  
	  	  String successInd = map.get(SUCCESS_INDICATOR);
	  	  
	  	  if(StringUtils.isBlank(successInd)) {
	  		  throw new Exception("Unreadable response from reCaptcha " + json);
	  	  }
	  	  
	  	  Boolean responseBoolean = Boolean.valueOf(successInd);
	  	  
	  	  if(responseBoolean) {
	  		checkCaptcha = true;
	  	  }
	  	  
	  	  return checkCaptcha;

	    }
	  }


}
