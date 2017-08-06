package za.org.grassroot.integration.messaging;

import za.org.grassroot.integration.PublicCredentials;

/**
 * Created by luke on 2017/05/22.
 */
public interface JwtService {

    PublicCredentials getPublicCredentials();
    String createJwt(CreateJwtTokenRequest request);

    /**
     * Refresh token if old token is still valid or has expired but is still within the expiration grace period.
     * @param oldToken
     * @param jwtType
     * @return new token if old token is still valid or has expired but is still within the expiration grace period.
     * Otherwise, return <code></code>null.
     */
    String refreshToken(String oldToken, JwtType jwtType);
    boolean isJwtTokenValid(String token);
    boolean isJwtTokenExpired(String token);
}
