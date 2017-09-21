package za.org.grassroot.integration.keyprovider;

import java.security.KeyPair;

/**
 * Provider for key pairs.
 */
public interface KeyPairProvider {

    KeyPair getJWTKey();

    /**
     * Get a key with the specified alias;
     * If there is no key of this alias, return
     * {@code null}
     *
     * @param alias the alias of key to load
     * @return a valid key pair or {@code null} if a key with this alias is not available
     */
    KeyPair getKey(String alias);
}
