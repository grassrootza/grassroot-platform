package za.org.grassroot.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;


/**
 * Created by aakilomar on 11/15/15.
 */
@Component
public class AuthenticationUtil {

    private Logger log = LoggerFactory.getLogger(AuthenticationUtil.class);


    public void debugAuthentication() {
        log.info("debugAuthentication...");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            log.info("authentication is NULL!!!");
        } else {
            log.info("debugAuthentication...name..." + authentication.getName());
            log.info("debugAuthentication...principal..." + authentication.getPrincipal().toString() );
            log.info("debugAuthentication...class..." + authentication.getClass().toString() );
            log.info("debugAuthentication...getAuthorities..." + authentication.getAuthorities().toString() );
            if (authentication.getCredentials() != null) {
                log.info("debugAuthentication...getCredentials..." + authentication.getCredentials().toString() );
            } else {
                log.info("debugAuthentication...getCredentials...is NULL" );
            }
            log.info("debugAuthentication...isAuthenticated..." + authentication.isAuthenticated() );
            if (authentication.getDetails() != null) {
                log.info("debugAuthentication...getDetails..." + authentication.getDetails().toString() );
            } else {
                log.info("debugAuthentication...getDetails...is NULL" );

            }


        }

    }

}
