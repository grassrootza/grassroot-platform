package za.org.grassroot.webapp.controller.rest;

import lombok.extern.slf4j.Slf4j;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.model.http.AuthorizationHeader;

import javax.servlet.http.HttpServletRequest;

@Slf4j
public class BaseRestController {

    private final JwtService jwtService;
    private final UserManagementService userManagementService;

    public BaseRestController(JwtService jwtService, UserManagementService userManagementService) {
        this.jwtService = jwtService;
        this.userManagementService = userManagementService;
    }

    protected String getJwtTokenFromRequest(HttpServletRequest request) {

        String jwtToken = null;
        AuthorizationHeader authorizationHeader = new AuthorizationHeader(request);
        if (authorizationHeader.hasBearerToken()) {
            jwtToken = authorizationHeader.getBearerToken();
        }
        return jwtToken;
    }

    protected String getUserIdFromRequest(HttpServletRequest request) {
        String jwtToken = getJwtTokenFromRequest(request);
        return getUserIdFromToken(jwtToken);
    }

    protected User getUserFromToken(String jwtToken) {

        if (jwtToken != null) {
            String userUid = jwtService.getUserIdFromJwtToken(jwtToken);
            return userManagementService.load(userUid);
        } else
            return null;
    }

    protected String getUserIdFromToken(String jwtToken) {

        if (jwtToken != null) {
            return jwtService.getUserIdFromJwtToken(jwtToken);
        } else
            return null;
    }

    protected User getUserFromRequest(HttpServletRequest request) {

        String jwtToken = getJwtTokenFromRequest(request);
        if (jwtToken != null) {
            String userUid = jwtService.getUserIdFromJwtToken(jwtToken);
            if (userUid != null)
                return userManagementService.load(userUid);
        }

        return null;
    }
}
