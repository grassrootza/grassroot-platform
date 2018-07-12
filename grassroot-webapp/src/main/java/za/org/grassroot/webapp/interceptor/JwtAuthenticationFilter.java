package za.org.grassroot.webapp.interceptor;

import com.google.api.client.http.HttpMethods;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.webapp.model.http.AuthorizationHeader;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private JwtService jwtService;

    @Autowired
    public void setJwtService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.debug("request method: {}", request.getMethod());
        if (HttpMethods.OPTIONS.equals(request.getMethod())) { // to handle CORS
            filterChain.doFilter(request, response);
            return;
        }

        AuthorizationHeader authorizationHeader = new AuthorizationHeader(request);
        final String token = authorizationHeader.hasBearerToken() ? authorizationHeader.getBearerToken() : null;

        log.debug("auth headers: {}, token: {}", request.getHeaderNames(), token);

        if (authorizationHeader.hasBearerToken() && jwtService.isJwtTokenValid(token)) {
            String userId = jwtService.getUserIdFromJwtToken(token);
            Set<Role> userRoles = jwtService.getStandardRolesFromJwtToken(token).stream().map(name -> new Role(name, null))
                    .collect(Collectors.toSet());
            log.debug("and user roles = {}", userRoles);
            JwtBasedAuthentication auth = new JwtBasedAuthentication(userRoles, token, userId);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } else {
            SecurityContextHolder.getContext().setAuthentication(new AnonAuthentication()); // to avoid redirects etc
        }

        filterChain.doFilter(request, response);
    }

}
