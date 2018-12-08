package za.org.grassroot.webapp.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.http.AuthorizationHeader;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapperImpl;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by paballo on 2016/03/15.
 */
@Component @Slf4j
public class TokenValidationInterceptor extends HandlerInterceptorAdapter {

    private PasswordTokenService passwordTokenService;
    private JwtService jwtService;

    private static final String contentType = "application/json";

    @Autowired
    public void setPasswordTokenService(PasswordTokenService passwordTokenService) {
        this.passwordTokenService = passwordTokenService;
    }

    @Autowired
    public void setJwtService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("Prehandling legacy request");
        if (request.getMethod().equalsIgnoreCase(RequestMethod.OPTIONS.toString()))
            return true;

        AuthorizationHeader authorizationHeader = new AuthorizationHeader(request);
        final String token = authorizationHeader.hasBearerToken() ? authorizationHeader.getBearerToken() : null;

        boolean isTokenExpired = false;

        if (authorizationHeader.hasBearerToken()
                && jwtService.isJwtTokenValid(authorizationHeader.getBearerToken())) {
            log.info("Found a header in legacy interceptor, so returning true and allowing other filters to handle");
            return true;
        } else if (authorizationHeader.hasBearerToken() && jwtService.isJwtTokenExpired(token)) {
            isTokenExpired = true;
        } else if(authorizationHeader.doesNotHaveBearerToken()) {
            Map<String, String> legacyVars = getLegacyTokenParams(request);
            log.debug("Legacy params: {}", legacyVars);
            if (isLegacyTokenValid(legacyVars)) {
                log.info("Legacy token is valid, allowing request");
                return true;
            }
            isTokenExpired = isLegacyTokenExpired(legacyVars);
        }

        setResponseBody(isTokenExpired, response);
        return false;
    }

    private void setResponseBody(boolean isTokenExpired, HttpServletResponse response) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectWriter ow = mapper.writer();

        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED,
                isTokenExpired ? RestMessage.TOKEN_EXPIRED : RestMessage.INVALID_TOKEN, RestStatus.FAILURE);

        response.setContentType(contentType);
        response.getWriter().write(ow.writeValueAsString(responseWrapper));
        response.setStatus(responseWrapper.getCode());

        log.info("Returning invalid token response, rest message: {}", responseWrapper.getMessage());
    }

    private Map<String, String> getLegacyTokenParams(HttpServletRequest request) {
        Map<String, String> vars = new HashMap<>();
        Map pathVariables = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables != null && pathVariables.containsKey("phoneNumber"))
            vars.put("phoneNumber", String.valueOf(pathVariables.get("phoneNumber")).trim());
        if (pathVariables != null && pathVariables.containsKey("code"))
            vars.put("code", String.valueOf(pathVariables.get("code")).trim());
        return vars;
    }

    private boolean isLegacyTokenValid(Map<String, String> pathVars) {
        if (pathVars.isEmpty()) {
            log.info("No path variables, returning false");
            return false;
        }
        boolean tokenValid = passwordTokenService.isLongLiveAuthValid(pathVars.get("phoneNumber"), pathVars.get("code"));
        log.info("Completed token code check, result: {}", tokenValid);
        return tokenValid || passwordTokenService.extendAuthCodeIfExpiring(pathVars.get("phoneNumber"), pathVars.get("code"));
    }

    private boolean isLegacyTokenExpired(Map<String, String> pathVars) {
        if (pathVars.isEmpty())
            return false;
        VerificationTokenCode tokenCode = passwordTokenService.fetchLongLivedAuthCode(pathVars.get("phoneNumber"));
        return tokenCode != null && passwordTokenService.isExpired(tokenCode);
    }
}

