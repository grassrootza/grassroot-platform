package za.org.grassroot.integration.messaging;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    public CreateJwtTokenRequest(JwtType jwtType, User user) {
        this(jwtType);
        claims.put(JwtService.USER_UID_KEY, user.getUid());
        claims.put(JwtService.SYSTEM_ROLE_KEY, user.getStandardRoles().stream().map(Role::getName).collect(Collectors.joining(",")));
    }

    // only used in refresh, and system roles must be comma separated
    protected CreateJwtTokenRequest(JwtType jwtType, Long shortExpiryMillis, String userUid, String systemRoles) {
        this(jwtType);
        if (userUid != null)
            claims.put(JwtService.USER_UID_KEY, userUid);
        if (systemRoles != null)
            claims.put(JwtService.SYSTEM_ROLE_KEY, systemRoles);
        if (shortExpiryMillis != null) {
            this.shortExpiryMillis = shortExpiryMillis;
        }
    }

}
