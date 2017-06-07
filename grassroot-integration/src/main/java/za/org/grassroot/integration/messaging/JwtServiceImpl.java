package za.org.grassroot.integration.messaging;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.TextCodec;
import io.jsonwebtoken.impl.crypto.RsaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import za.org.grassroot.integration.PublicCredentials;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
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

    private final Environment environment;

    private KeyPair keyPair;
    private String kuid;

    @Autowired
    public JwtServiceImpl(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        PublicCredentials credentials = refreshPublicCredentials();
        logger.debug("Public credentials generated: {}", credentials);
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
        kuid = UUID.randomUUID().toString();
        if (StringUtils.isEmpty(environment.getProperty("JWT_KEYSTORE_PATH"))) {
            keyPair = RsaProvider.generateKeyPair(1024);
        } else {
            try {
                File file = new File(environment.getProperty("JWT_KEYSTORE_PATH"));
                FileInputStream is = new FileInputStream(file);
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

                final String password = environment.getProperty("JWT_KEYSTORE_PASS");
                final String alias = environment.getProperty("JWT_KEY_ALIAS");
                final String keypass = environment.getProperty("JWT_KEY_PASS", password);

                keyStore.load(is, password.toCharArray());
                PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keypass.toCharArray());
                Certificate certificate = keyStore.getCertificate(alias);
                PublicKey publicKey = certificate.getPublicKey();
                keyPair = new KeyPair(publicKey, privateKey);
            } catch (Exception e) {
                logger.error("Exception loading keystore, defaulting to in-memory generation");
                keyPair = RsaProvider.generateKeyPair(1024);
            }
        }
        return createCredentialEntity(kuid, keyPair.getPublic());
    }

    private PublicCredentials createCredentialEntity(String kuid, PublicKey key) {
        return new PublicCredentials(kuid, TextCodec.BASE64.encode(key.getEncoded()));
    }
}