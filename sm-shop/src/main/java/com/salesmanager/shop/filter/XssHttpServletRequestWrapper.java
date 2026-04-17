package com.salesmanager.shop.filter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import com.salesmanager.shop.utils.SanitizeUtils;

/**
 * Cross Site Scripting filter enforcing html encoding of request parameters
 * @author carlsamson
 *
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

	private byte[] sanitizedBody;

	public XssHttpServletRequestWrapper(HttpServletRequest request) {
		super(request);	
		
	}
	

	 
	 @Override
	    public String getHeader(String name) {
	        String value = super.getHeader(name);
	        if (value == null)
	            return null;
	        return cleanXSS(value);
	    }
	 
	 
	    public String[] getParameterValues(String parameter) {
	        String[] values = super.getParameterValues(parameter);
	        if (values == null) {
	            return null;
	        }
	        int count = values.length;
	        String[] encodedValues = new String[count];
	        for (int i = 0; i < count; i++) {
	            encodedValues[i] = cleanXSS(values[i]);
	        }
	        return encodedValues;
	    }
	    
	    @Override
	    public String getParameter(String parameter) {
	        String value = super.getParameter(parameter);
	        if (value == null) {
	            return null;
	        }
	        return cleanXSS(value);
	    }

	    @Override
	    public int getContentLength() {
	        if (sanitizedBody != null) {
	            return sanitizedBody.length;
	        }
	        return super.getContentLength();
	    }

	    @Override
	    public long getContentLengthLong() {
	        if (sanitizedBody != null) {
	            return sanitizedBody.length;
	        }
	        return super.getContentLengthLong();
	    }

	    @Override
	    public ServletInputStream getInputStream() throws IOException {
	        if (sanitizedBody == null) {
	            sanitizedBody = sanitizeBody();
	        }
	        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(sanitizedBody);
	        return new ServletInputStream() {
	            @Override
	            public int read() throws IOException {
	                return byteArrayInputStream.read();
	            }

	            @Override
	            public boolean isFinished() {
	                return byteArrayInputStream.available() == 0;
	            }

	            @Override
	            public boolean isReady() {
	                return true;
	            }

	            @Override
	            public void setReadListener(ReadListener readListener) {
	                throw new UnsupportedOperationException();
	            }
	        };
	    }

	    @Override
	    public BufferedReader getReader() throws IOException {
	        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
	    }

	    private byte[] sanitizeBody() throws IOException {
	        StringBuilder sb = new StringBuilder();
	        try (BufferedReader reader = new BufferedReader(
	                new InputStreamReader(super.getInputStream(), StandardCharsets.UTF_8))) {
	            char[] buffer = new char[1024];
	            int bytesRead;
	            while ((bytesRead = reader.read(buffer)) != -1) {
	                sb.append(buffer, 0, bytesRead);
	            }
	        }
	        String body = sb.toString();
	        if (body.isEmpty()) {
	            return body.getBytes(StandardCharsets.UTF_8);
	        }
	        String sanitized = cleanXSS(body);
	        return sanitized.getBytes(StandardCharsets.UTF_8);
	    }

	    private String cleanXSS(String value) {
	        // You'll need to remove the spaces from the html entities below
	    	return SanitizeUtils.getSafeString(value);
	    }

}
