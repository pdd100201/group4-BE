package com.onlinelearning.config;
import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys; import org.springframework.beans.factory.annotation.Value; import org.springframework.security.core.*; import org.springframework.stereotype.Component; import java.nio.charset.*; import java.security.*; import javax.crypto.SecretKey; import java.time.*; import java.util.*;
@Component public class JwtTokenProvider {
 private final SecretKey key; private final long accessMinutes; private final long refreshDays;
 public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,@Value("${app.jwt.access-token-minutes}") long accessMinutes,@Value("${app.jwt.refresh-token-days}") long refreshDays){this.key=Keys.hmacShaKeyFor(sha256(secret));this.accessMinutes=accessMinutes;this.refreshDays=refreshDays;}
 public String accessToken(String email,Collection<? extends GrantedAuthority> authorities){return token(email,authorities,Duration.ofMinutes(accessMinutes));}
 public String refreshToken(String email){return token(email,List.of(),Duration.ofDays(refreshDays));}
 public String subject(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();}
 public boolean valid(String token){try{subject(token);return true;}catch(JwtException|IllegalArgumentException e){return false;}}
 private String token(String subject,Collection<? extends GrantedAuthority> roles,Duration ttl){return Jwts.builder().subject(subject).claim("roles",roles.stream().map(GrantedAuthority::getAuthority).toList()).issuedAt(new Date()).expiration(Date.from(Instant.now().plus(ttl))).signWith(key).compact();}
 private byte[] sha256(String input){try{return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
