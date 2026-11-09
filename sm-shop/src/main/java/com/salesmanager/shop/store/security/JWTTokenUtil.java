package com.salesmanager.shop.store.security;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.salesmanager.shop.store.security.user.JWTUser;
import com.salesmanager.shop.utils.DateUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import jakarta.annotation.PostConstruct;

/**
 * Used for managing token based authentication for customer and user
 * @author c.samson
 *
 */
@Component
public class JWTTokenUtil implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = LoggerFactory.getLogger(JWTTokenUtil.class);

	static final int GRACE_PERIOD = 200;

	static final String AUDIENCE_UNKNOWN = "unknown";
	static final String AUDIENCE_API = "api";
	static final String AUDIENCE_WEB = "web";
	static final String AUDIENCE_MOBILE = "mobile";
	static final String AUDIENCE_TABLET = "tablet";

	static final MacAlgorithm SIGNATURE_ALGORITHM = Jwts.SIG.HS512;

	static final String DEFAULT_DEV_SECRET_MARKER = "change-me";

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.expiration}")
	private Long expiration;

	private transient SecretKey signingKey;

	public JWTTokenUtil() {
	}

	JWTTokenUtil(String secret, Long expiration) {
		this.secret = secret;
		this.expiration = expiration;
		init();
	}

	/**
	 * HS512 requires a key of at least 512 bits, so {@code jwt.secret} must be at
	 * least 64 bytes (UTF-8).
	 */
	@PostConstruct
	void init() {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("jwt.secret must be configured (set the JWT_SECRET environment variable)");
		}
		byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		int minBytes = SIGNATURE_ALGORITHM.getKeyBitLength() / Byte.SIZE;
		if (keyBytes.length < minBytes) {
			throw new IllegalStateException("jwt.secret must be at least " + minBytes + " bytes long for "
					+ SIGNATURE_ALGORITHM.getId() + " (was " + keyBytes.length + ")");
		}
		if (secret.contains(DEFAULT_DEV_SECRET_MARKER)) {
			LOGGER.warn("Using the default development jwt.secret; set JWT_SECRET for any non-local deployment");
		}
		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
	}

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
		Set<String> audience = getClaimFromToken(token, Claims::getAudience);
		return (audience == null || audience.isEmpty()) ? null : audience.iterator().next();
	}

	public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = getAllClaimsFromToken(token);
		return claimsResolver.apply(claims);
	}

	private Claims getAllClaimsFromToken(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	private Boolean isTokenExpired(String token) {
		final Date expiration = getExpirationDateFromToken(token);
		return expiration.before(DateUtil.getDate());
	}

	private Boolean isTokenExpiredWithGrace(String token) {
		Date expiration = getExpirationDateFromToken(token);
		expiration = addSeconds(expiration, GRACE_PERIOD);
		return expiration.before(DateUtil.getDate());
	}

	private Boolean isCreatedBeforeLastPasswordReset(Date created, Date lastPasswordReset) {
		return (lastPasswordReset != null && created.before(lastPasswordReset));
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
		return doGenerateToken(userDetails.getUsername(), generateAudience());
	}

	private String doGenerateToken(String subject, String audience) {
		final Date createdDate = DateUtil.getDate();
		final Date expirationDate = calculateExpirationDate(createdDate);

		return Jwts.builder()
				.subject(subject)
				.audience().add(audience).and()
				.issuedAt(createdDate)
				.expiration(expirationDate)
				.signWith(signingKey, SIGNATURE_ALGORITHM)
				.compact();
	}

	public Boolean canTokenBeRefreshedWithGrace(String token, Date lastPasswordReset) {
		final Date created = getIssuedAtDateFromToken(token);
		return !isCreatedBeforeLastPasswordReset(created, lastPasswordReset)
				&& (!isTokenExpiredWithGrace(token) || ignoreTokenExpiration(token));
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
				.signWith(signingKey, SIGNATURE_ALGORITHM)
				.compact();
	}

	public Boolean validateToken(String token, UserDetails userDetails) {
		JWTUser user = (JWTUser) userDetails;
		final String username = getUsernameFromToken(token);
		final Date created = getIssuedAtDateFromToken(token);

		boolean usernameEquals = username.equals(user.getUsername());
		boolean isTokenExpired = isTokenExpired(token);
		boolean isTokenCreatedBeforeLastPasswordReset = isCreatedBeforeLastPasswordReset(created, user.getLastPasswordResetDate());

		return usernameEquals && !isTokenExpired && !isTokenCreatedBeforeLastPasswordReset;
	}

	private Date calculateExpirationDate(Date createdDate) {
		return new Date(createdDate.getTime() + expiration * 1000);
	}

}
