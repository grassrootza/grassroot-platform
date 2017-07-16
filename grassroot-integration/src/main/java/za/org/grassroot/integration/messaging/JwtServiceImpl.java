package za.org.grassroot.integration.messaging;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.TextCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import za.org.grassroot.integration.PublicCredentials;
import za.org.grassroot.integration.keyprovider.KeyPairProvider;

import javax.annotation.PostConstruct;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Created by luke on 2017/05/22.
 */
@Service
public class JwtServiceImpl implements JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtServiceImpl.class);

    private String kuid;
    @Value("${grassroot.jwt.token-time-to-live.inMilliSeconds:600000}")
    private Long jwtTimeToLiveInMilliSeconds;
    @Value("${grassroot.jwt.token-expiry-grace-period.inMilliseconds:1209600000}")
    private Long jwtTokenExpiryGracePeriodInMilliseconds;
    @Autowired
    private KeyPairProvider keyPairProvider;

    @PostConstruct
    public void init() {
        PublicCredentials credentials = refreshPublicCredentials();
        logger.debug("Public credentials generated: {}", credentials);
    }

    @Override
    public PublicCredentials getPublicCredentials() {
        return createCredentialEntity(kuid, keyPairProvider.getJWTKey().getPublic());
    }

    @Override
    public String createJwt(CreateJwtTokenRequest request) {
        Instant now = Instant.now();
        Instant exp = now.plus(1, ChronoUnit.MILLIS);
        request.getHeaderParameters().put("kid", kuid);
        return Jwts.builder()
                .setHeaderParams(request.getHeaderParameters())
                .setClaims(request.getClaims())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(
                        SignatureAlgorithm.RS256,
                        keyPairProvider.getJWTKey().getPrivate()
                )
                .compact();
    }

    @Override
    public boolean isJwtTokenValid(String token) {
        try {
            Jwts.parser().setSigningKey(keyPairProvider.getJWTKey().getPublic()).parse(token);
            return true;
        }
        catch (ExpiredJwtException e) {
            logger.error("Token validation failed. The token is expired.", e);
            return false;
        }
        catch (Exception e) {
            logger.error("Unexpected token validation error.", e);
            return false;
        }
    }

    @Override
    public String refreshToken(String oldToken) {
        boolean isTokenStillValid = false;
        Date expirationTime = null;
        String newToken = null;
        try {
            Jwts.parser().setSigningKey(keyPairProvider.getJWTKey().getPublic()).parse(oldToken);
            isTokenStillValid = true;
        }
        catch (ExpiredJwtException e) {
            logger.error("Token validation failed. The token is expired.", e);
            expirationTime = e.getClaims().getExpiration();
        }
        if (isTokenStillValid || expirationTime != null
                && expirationTime.toInstant().plus(jwtTokenExpiryGracePeriodInMilliseconds, ChronoUnit.MILLIS).isAfter(new Date().toInstant())) {
            newToken =  createJwt(new CreateJwtTokenRequest());
        }

        return newToken;
    }

    private PublicCredentials refreshPublicCredentials() {
        kuid = UUID.randomUUID().toString();
        logger.debug("created KUID for main platform: {}", kuid);
        return createCredentialEntity(kuid, keyPairProvider.getJWTKey().getPublic());
    }

    private PublicCredentials createCredentialEntity(String kuid, PublicKey key) {
        return new PublicCredentials(kuid, TextCodec.BASE64.encode(key.getEncoded()));
    }
}