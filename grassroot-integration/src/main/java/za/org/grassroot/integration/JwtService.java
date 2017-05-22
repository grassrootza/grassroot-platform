package za.org.grassroot.integration;

import java.util.Map;

/**
 * Created by luke on 2017/05/22.
 */
public interface JwtService {

    PublicCredentials getPublicCredentials();
    String createJwt(Map<String, Object> claims);

}
