package com.salesmanager.shop.store.security;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.salesmanager.shop.store.security.user.JWTUser;
import com.salesmanager.shop.utils.DateUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Used for managing token based authentication for customer and user
 * @author c.samson
 *
 */
@Component
public class JWTTokenUtil implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	
	    static final int GRACE_PERIOD = 200;
	
	
	
	    static final String CLAIM_KEY_USERNAME = "sub";
	    static final String CLAIM_KEY_AUDIENCE = "aud";
	    static final String CLAIM_KEY_CREATED = "iat";
	    public static final String CLAIM_KEY_TOKEN_TYPE = "tokenType";
	    public static final String CLAIM_KEY_USER_ID = "uid";

	    public static final String TOKEN_TYPE_ADMIN = "admin";
	    public static final String TOKEN_TYPE_CUSTOMER = "customer";

	    static final String AUDIENCE_UNKNOWN = "unknown";
	    static final String AUDIENCE_API = "api";
	    static final String AUDIENCE_WEB = "web";
	    static final String AUDIENCE_MOBILE = "mobile";
	    static final String AUDIENCE_TABLET = "tablet";


	    @Value("${jwt.secret}")
	    private String secret;

	    @Value("${jwt.expiration}")
	    private Long expiration;

	    public String getUsernameFromToken(String token) {
	        return getClaimFromToken(token, Claims::getSubject);
	    }

	    public Date getIssuedAtDateFromToken(String token) {
	        return getClaimFromToken(token, Claims::getIssuedAt);
	    }

	    public Date getExpirationDateFromToken(String token) {
	        return getClaimFromToken(token, Claims::getExpiration);
	    }

	    public String getAudienceFromToken(String token) {
	        return getClaimFromToken(token, Claims::getAudience);
	    }

	    public String getTokenTypeFromToken(String token) {
	        return getClaimFromToken(token, claims -> claims.get(CLAIM_KEY_TOKEN_TYPE, String.class));
	    }

	    public Long getUserIdFromToken(String token) {
	        final Object userId = getClaimFromToken(token, claims -> claims.get(CLAIM_KEY_USER_ID));
	        return userId instanceof Number ? ((Number) userId).longValue() : null;
	    }

	    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
	        final Claims claims = getAllClaimsFromToken(token);
	        return claimsResolver.apply(claims);
	    }

	    private Claims getAllClaimsFromToken(String token) {
	        return Jwts.parser()
	                .setSigningKey(secret)
	                .parseClaimsJws(token)
	                .getBody();
	    }

	    private Boolean isTokenExpired(String token) {
	        final Date expiration = getExpirationDateFromToken(token);
	        return expiration.before(DateUtil.getDate());
	    }
	    
	    private Boolean isTokenExpiredWithGrace(String token) {
	            Date expiration = getExpirationDateFromToken(token);
	            expiration = addSeconds(expiration,GRACE_PERIOD);
	            return expiration.before(DateUtil.getDate());
	    }

	    private Boolean isCreatedBeforeLastPasswordReset(Date created, Date lastPasswordReset) {
	        return (lastPasswordReset != null && created.before(lastPasswordReset));
	    }
	    
	    private Boolean isCreatedBeforeLastPasswordResetWithGrace(Date created, Date lastPasswordReset) {
	        return (lastPasswordReset != null && created.before(addSeconds(lastPasswordReset,GRACE_PERIOD)));
	    }
	    
	    private Date addSeconds(Date date, Integer seconds) {
	      Calendar cal = Calendar.getInstance();
	      cal.setTime(date);
	      cal.add(Calendar.SECOND, seconds);
	      return cal.getTime();
	    }

	    private String generateAudience() {
	        return AUDIENCE_API;
	    }

	    private Boolean ignoreTokenExpiration(String token) {
	        String audience = getAudienceFromToken(token);
	        return (AUDIENCE_TABLET.equals(audience) || AUDIENCE_MOBILE.equals(audience));
	    }

	    public String generateToken(UserDetails userDetails, String tokenType) {
	        Validate.notBlank(tokenType, "Token type must not be blank");
	        Map<String, Object> claims = new HashMap<>();
	        claims.put(CLAIM_KEY_TOKEN_TYPE, tokenType);
	        if (userDetails instanceof JWTUser && ((JWTUser) userDetails).getId() != null) {
	            claims.put(CLAIM_KEY_USER_ID, ((JWTUser) userDetails).getId());
	        }
	        return doGenerateToken(claims, userDetails.getUsername(), generateAudience());
	    }

	    private String doGenerateToken(Map<String, Object> claims, String subject, String audience) {
	        final Date createdDate = DateUtil.getDate();
	        final Date expirationDate = calculateExpirationDate(createdDate);

	        return Jwts.builder()
	                .setClaims(claims)
	                .setSubject(subject)
	                .setAudience(audience)
	                .setIssuedAt(createdDate)
	                .setExpiration(expirationDate)
	                .signWith(SignatureAlgorithm.HS512, secret)
	                .compact();
	    }
	    
        public Boolean canTokenBeRefreshedWithGrace(String token, JWTUser user, String expectedTokenType) {
          final Date created = getIssuedAtDateFromToken(token);
          return isTokenBoundTo(token, user, expectedTokenType)
                  && !isCreatedBeforeLastPasswordResetWithGrace(created, user.getLastPasswordResetDate())
                  && (!isTokenExpiredWithGrace(token) || ignoreTokenExpiration(token));
        }

	    public Boolean canTokenBeRefreshed(String token, JWTUser user, String expectedTokenType) {
	        final Date created = getIssuedAtDateFromToken(token);
	        return isTokenBoundTo(token, user, expectedTokenType)
	                && !isCreatedBeforeLastPasswordReset(created, user.getLastPasswordResetDate())
	                && (!isTokenExpired(token) || ignoreTokenExpiration(token));
	    }

	    public String refreshToken(String token) {
	        final Date createdDate = DateUtil.getDate();
	        final Date expirationDate = calculateExpirationDate(createdDate);

	        final Claims claims = getAllClaimsFromToken(token);
	        claims.setIssuedAt(createdDate);
	        claims.setExpiration(expirationDate);

	        return Jwts.builder()
	                .setClaims(claims)
	                .signWith(SignatureAlgorithm.HS512, secret)
	                .compact();
	    }

	    public Boolean validateToken(String token, UserDetails userDetails, String expectedTokenType) {
	        JWTUser user = (JWTUser) userDetails;
	        final Date created = getIssuedAtDateFromToken(token);
	        boolean isTokenExpired = isTokenExpired(token);
	        boolean isTokenCreatedBeforeLastPasswordReset = isCreatedBeforeLastPasswordReset(created, user.getLastPasswordResetDate());

	        return isTokenBoundTo(token, user, expectedTokenType)
	                && !isTokenExpired && !isTokenCreatedBeforeLastPasswordReset;
	    }

	    public boolean isTokenBoundTo(String token, JWTUser user, String expectedTokenType) {
	        return user.getUsername().equals(getUsernameFromToken(token))
	                && expectedTokenType.equals(getTokenTypeFromToken(token))
	                && user.getId() != null && user.getId().equals(getUserIdFromToken(token));
	    }

	    private Date calculateExpirationDate(Date createdDate) {
	        return new Date(createdDate.getTime() + expiration * 1000);
	    }

}
