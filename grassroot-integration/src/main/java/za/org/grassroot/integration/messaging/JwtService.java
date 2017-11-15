package za.org.grassroot.integration.messaging;

import za.org.grassroot.integration.PublicCredentials;

/**
 * Created by luke on 2017/05/22.
 */
public interface JwtService {

    String USER_UID_KEY = "USER_UID";

    PublicCredentials getPublicCredentials();
    String createJwt(CreateJwtTokenRequest request);

    /**
     * Refresh token if old token is still valid or has expired but is still within the expiration grace period.
     * @param oldToken
     * @param jwtType
     * @param shortExpiryMillis
     * @return new token if old token is still valid or has expired but is still within the expiration grace period.
     * Otherwise, return <code></code>null.
     */
    String refreshToken(String oldToken, JwtType jwtType, Long shortExpiryMillis);

    boolean isJwtTokenValid(String token);

    boolean isJwtTokenExpired(String token);

    String getUserIdFromJwtToken(String token);

}
