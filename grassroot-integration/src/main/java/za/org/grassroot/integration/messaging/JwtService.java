package za.org.grassroot.integration.messaging;

import za.org.grassroot.integration.PublicCredentials;

import java.util.Map;

/**
 * Created by luke on 2017/05/22.
 */
public interface JwtService {

    PublicCredentials getPublicCredentials();
    String createJwt(Map<String, Object> claims);

}
