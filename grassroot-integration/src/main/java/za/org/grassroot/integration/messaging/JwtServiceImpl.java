package za.org.grassroot.integration.messaging;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.TextCodec;
import io.jsonwebtoken.impl.crypto.RsaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import za.org.grassroot.integration.PublicCredentials;

import javax.annotation.PostConstruct;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Created by luke on 2017/05/22.
 */
@Service
public class JwtServiceImpl implements JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtServiceImpl.class);

    private KeyPair keyPair;
    private String kuid;

    @PostConstruct
    public void init() {
        PublicCredentials credentials = refreshPublicCredentials();
        logger.info("Public credentials generated: {}", credentials);
    }

    @Override
    public PublicCredentials getPublicCredentials() {
        return createCredentialEntity(kuid, keyPair.getPublic());
    }

    @Override
    public String createJwt(Map<String, Object> claims) {
        Instant now = Instant.now();
        Instant exp = now.plus(1L, ChronoUnit.MINUTES);
        return Jwts.builder()
                .setHeaderParam("kid", kuid)
                .setClaims(claims)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(
                        SignatureAlgorithm.RS256,
                        keyPair.getPrivate()
                )
                .compact();
    }

    private PublicCredentials refreshPublicCredentials() {
        keyPair = RsaProvider.generateKeyPair(1024);
        kuid = UUID.randomUUID().toString();
        return createCredentialEntity(kuid, keyPair.getPublic());
    }

    private PublicCredentials createCredentialEntity(String kuid, PublicKey key) {
        return new PublicCredentials(kuid, TextCodec.BASE64.encode(key.getEncoded()));
    }
}
