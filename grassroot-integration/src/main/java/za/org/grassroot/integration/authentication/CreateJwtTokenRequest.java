package za.org.grassroot.integration.authentication;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.StandardRole;
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
        claims.put(JwtService.TYPE_KEY, jwtType);
    }

    public CreateJwtTokenRequest(JwtType jwtType, User user) {
        this(jwtType);
        claims.put(JwtService.USER_UID_KEY, user.getUid());
        claims.put(JwtService.SYSTEM_ROLE_KEY, user.getStandardRoles().stream().map(Enum::name).collect(Collectors.joining(",")));
    }

    // NB: never insert roles in here, this is exclusively for minimal scope tokens
    public CreateJwtTokenRequest(JwtType jwtType, String userUid, Set<String> permissions) {
        this(jwtType);
        claims.put(JwtService.USER_UID_KEY, userUid);
        claims.put(JwtService.PERMISSIONS_KEY, String.join(",", permissions));
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

    // strictly for microservices
    public static CreateJwtTokenRequest makeSystemToken() {
        CreateJwtTokenRequest request = new CreateJwtTokenRequest(JwtType.GRASSROOT_MICROSERVICE);
        request.claims.put(JwtService.SYSTEM_ROLE_KEY, StandardRole.ROLE_SYSTEM_CALL);
        return request;
    }

    public void addClaim(String key, String value) {
        claims.put(key, value);
    }

}
