package com.salesmanager.shop.store.security;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.salesmanager.shop.store.security.user.JWTUser;
import com.salesmanager.shop.utils.DateUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

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
	        return getClaimFromToken(token, claims -> {
	            Set<String> audience = claims.getAudience();
	            return audience == null || audience.isEmpty() ? null : audience.iterator().next();
	        });
	    }

	    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
	        final Claims claims = getAllClaimsFromToken(token);
	        return claimsResolver.apply(claims);
	    }

	    private Claims getAllClaimsFromToken(String token) {
	        return Jwts.parser()
	                .verifyWith(signingKey())
	                .build()
	                .parseSignedClaims(token)
	                .getPayload();
	    }

	    /**
	     * HS512 requires a key of at least 512 bits; shorter configured secrets are
	     * stretched with SHA-512 so existing jwt.secret values keep working.
	     */
	    private SecretKey signingKey() {
	        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
	        if (keyBytes.length < 64) {
	            try {
	                keyBytes = MessageDigest.getInstance("SHA-512").digest(keyBytes);
	            } catch (NoSuchAlgorithmException e) {
	                throw new IllegalStateException("SHA-512 not available", e);
	            }
	        }
	        return Keys.hmacShaKeyFor(keyBytes);
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

	    public String generateToken(UserDetails userDetails) {
	        Map<String, Object> claims = new HashMap<>();
	        return doGenerateToken(claims, userDetails.getUsername(), generateAudience());
	    }

	    private String doGenerateToken(Map<String, Object> claims, String subject, String audience) {
	        final Date createdDate = DateUtil.getDate();
	        final Date expirationDate = calculateExpirationDate(createdDate);

	        System.out.println("doGenerateToken " + createdDate);

	        return Jwts.builder()
	                .claims(claims)
	                .subject(subject)
	                .audience().add(audience).and()
	                .issuedAt(createdDate)
	                .expiration(expirationDate)
	                .signWith(signingKey(), Jwts.SIG.HS512)
	                .compact();
	    }
	    
        public Boolean canTokenBeRefreshedWithGrace(String token, Date lastPasswordReset) {
          final Date created = getIssuedAtDateFromToken(token);
          boolean t = isCreatedBeforeLastPasswordResetWithGrace(created, lastPasswordReset);
          boolean u = isTokenExpiredWithGrace(token);
          boolean v =  ignoreTokenExpiration(token);
          System.out.println(t + " " +  u + " " + v);
          System.out.println(!isCreatedBeforeLastPasswordResetWithGrace(created, lastPasswordReset)
                  && (!isTokenExpiredWithGrace(token) || ignoreTokenExpiration(token)));
          //return !isCreatedBeforeLastPasswordResetWithGrace(created, lastPasswordReset)
          //        && (!isTokenExpired(token) || ignoreTokenExpiration(token));
          return true;
        }	    

	    public Boolean canTokenBeRefreshed(String token, Date lastPasswordReset) {
	        final Date created = getIssuedAtDateFromToken(token);
	        return !isCreatedBeforeLastPasswordReset(created, lastPasswordReset)
	                && (!isTokenExpired(token) || ignoreTokenExpiration(token));
	    }

	    public String refreshToken(String token) {
	        final Date createdDate = DateUtil.getDate();
	        final Date expirationDate = calculateExpirationDate(createdDate);

	        final Claims claims = getAllClaimsFromToken(token);

	        return Jwts.builder()
	                .claims(claims)
	                .issuedAt(createdDate)
	                .expiration(expirationDate)
	                .signWith(signingKey(), Jwts.SIG.HS512)
	                .compact();
	    }

	    public Boolean validateToken(String token, UserDetails userDetails) {
	        JWTUser user = (JWTUser) userDetails;
	        final String username = getUsernameFromToken(token);
	        final Date created = getIssuedAtDateFromToken(token);
	        //final Date expiration = getExpirationDateFromToken(token);
	        
	        boolean usernameEquals = username.equals(user.getUsername());
	        boolean isTokenExpired = isTokenExpired(token);
	        boolean isTokenCreatedBeforeLastPasswordReset = isCreatedBeforeLastPasswordReset(created, user.getLastPasswordResetDate());
	        
	        return (

	        		usernameEquals && !isTokenExpired && !isTokenCreatedBeforeLastPasswordReset
	        );
	    }

	    private Date calculateExpirationDate(Date createdDate) {
	        return new Date(createdDate.getTime() + expiration * 1000);
	    }

}
