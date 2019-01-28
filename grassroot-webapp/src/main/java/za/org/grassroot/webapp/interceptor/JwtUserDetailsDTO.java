package za.org.grassroot.webapp.interceptor;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import za.org.grassroot.core.domain.StandardRole;

import java.util.Collection;

public class JwtUserDetailsDTO implements UserDetails {
    private final String userId;
    private final Collection<StandardRole> userSystemRoles;

    public JwtUserDetailsDTO(String userId, Collection<StandardRole> userSystemRoles) {
        this.userId = userId;
        this.userSystemRoles = userSystemRoles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return userSystemRoles;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return userId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
