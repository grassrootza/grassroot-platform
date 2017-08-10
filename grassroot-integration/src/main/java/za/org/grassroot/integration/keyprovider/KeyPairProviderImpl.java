package za.org.grassroot.integration.keyprovider;

import io.jsonwebtoken.impl.crypto.RsaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;

@Service
public class KeyPairProviderImpl implements KeyPairProvider{

    private static final Logger logger = LoggerFactory.getLogger(KeyPairProviderImpl.class);

    private final Environment environment;

    private Map<String, KeyPair> keyPairMap = new HashMap<>();

    @Autowired
    public KeyPairProviderImpl(Environment environment){
        this.environment = environment;
    }

    @PostConstruct
    void initProvider() {
        String  jwtKeyAlias = getJWTKeyAlias();
        KeyPair keyPair;
        if (StringUtils.isEmpty(environment.getProperty("JWT_KEYSTORE_PATH"))) {
            keyPair = RsaProvider.generateKeyPair(1024);
        } else {
            try {
                File file = new File(environment.getProperty("JWT_KEYSTORE_PATH"));
                FileInputStream is = new FileInputStream(file);
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

                final String password = environment.getProperty("JWT_KEYSTORE_PASS");
                final String keypass = environment.getProperty("JWT_KEY_PASS", password);

                keyStore.load(is, password.toCharArray());
                PrivateKey privateKey = (PrivateKey) keyStore.getKey(jwtKeyAlias, keypass.toCharArray());
                Certificate certificate = keyStore.getCertificate(jwtKeyAlias);
                PublicKey publicKey = certificate.getPublicKey();
                keyPair = new KeyPair(publicKey, privateKey);
            } catch (Exception e) {
                logger.error("Exception loading keystore, defaulting to in-memory generation");
                keyPair = RsaProvider.generateKeyPair(1024);
            }
        }
        keyPairMap.put(jwtKeyAlias, keyPair);
    }

    @Override
    public KeyPair getKey(String alias) {
        return keyPairMap.get(alias);
    }


    @Override
    public KeyPair getJWTKey() {
        return getKey(getJWTKeyAlias());
    }

    private String getJWTKeyAlias() {
        String alias = environment.getProperty("JWT_KEY_ALIAS");
        if (alias == null) {
            alias = "grassroot_jwt";
        }
        return alias;
    }
}
