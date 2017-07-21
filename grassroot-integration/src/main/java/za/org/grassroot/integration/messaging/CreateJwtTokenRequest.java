package za.org.grassroot.integration.messaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by edwin on 2017/07/08.
 */
public class CreateJwtTokenRequest {
    private JwtType jwtType;
    private Map<String, Object> claims = new HashMap<>();
    private Map<String, Object> headerParameters = new HashMap<>();

    public CreateJwtTokenRequest(JwtType jwtType) {
        this.jwtType = jwtType;
    }

    public JwtType getJwtType() {
        return jwtType;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public void setClaims(Map<String, Object> claims) {
        this.claims = claims;
    }

    public Map<String, Object> getHeaderParameters() {
        return headerParameters;
    }

    public void setHeaderParameters(Map<String, Object> headerParameters) {
        this.headerParameters = headerParameters;
    }
}
