package za.org.grassroot.integration.messaging;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by edwin on 2017/07/08.
 */
@Getter
public class CreateJwtTokenRequest {
    private JwtType jwtType;
    @Setter private Long shortExpiryMillis;
    @Setter private Map<String, Object> claims = new HashMap<>();
    @Setter private Map<String, Object> headerParameters = new HashMap<>();

    public CreateJwtTokenRequest(JwtType jwtType) {
        this.jwtType = jwtType;
    }

    public CreateJwtTokenRequest(JwtType jwtType, Long shortExpiryMillis) {
        this(jwtType);
        if (shortExpiryMillis != null) {
            this.shortExpiryMillis = shortExpiryMillis;
        }
    }

}
