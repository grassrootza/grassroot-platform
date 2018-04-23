package za.org.grassroot.webapp.interceptor;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import za.org.grassroot.core.domain.Role;

import java.util.Collection;

public class JwtBasedAuthentication extends AbstractAuthenticationToken {

    @Getter private String token;
    private String userId;
    private UserDetails userDetails;

    public JwtBasedAuthentication(Collection<Role> authorities, String token, String userId) {
        super(authorities);
        this.token = token;
        this.userId = userId;
        this.userDetails = new JwtUserDetailsDTO(userId, authorities);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return userDetails;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }
}
