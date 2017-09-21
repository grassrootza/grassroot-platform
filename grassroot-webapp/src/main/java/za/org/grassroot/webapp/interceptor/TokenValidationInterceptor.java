package za.org.grassroot.webapp.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.http.AuthorizationHeader;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapperImpl;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Created by paballo on 2016/03/15.
 */

public class TokenValidationInterceptor extends HandlerInterceptorAdapter {

    private PasswordTokenService passwordTokenService;

    private JwtService jwtService;

    private static final Logger log = LoggerFactory.getLogger(TokenValidationInterceptor.class);

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
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception {

        AuthorizationHeader authorizationHeader = new AuthorizationHeader(request);

        boolean isTokenExpired = false;

        if (authorizationHeader.hasBearerToken()
                && jwtService.isJwtTokenValid(authorizationHeader.getBearerToken())) {
            return true;
        } else if (authorizationHeader.hasBearerToken()
                && jwtService.isJwtTokenExpired(authorizationHeader.getBearerToken())) {
            isTokenExpired = true;
        } else if(authorizationHeader.doesNotHaveBearerToken()) {
            Map pathVariables = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            String phoneNumber = String.valueOf(pathVariables.get("phoneNumber")).trim();
            String code = String.valueOf(pathVariables.get("code")).trim();
            if (passwordTokenService.isLongLiveAuthValid(phoneNumber, code)) {
                return true;
            } else {
                VerificationTokenCode tokenCode = passwordTokenService.fetchLongLivedAuthCode(phoneNumber);
                log.info("token code: {}", tokenCode);
                if (tokenCode != null && passwordTokenService.isExpired(tokenCode)) {
                    isTokenExpired = true;
                }
            }
        }


        final ObjectMapper mapper = new ObjectMapper();
        final ObjectWriter ow = mapper.writer();

        ResponseWrapper responseWrapper;

        if (isTokenExpired) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.TOKEN_EXPIRED,
                        RestStatus.FAILURE);
        } else {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN,
                        RestStatus.FAILURE);
        }
        response.setContentType(contentType);
        response.getWriter().write(ow.writeValueAsString(responseWrapper));
        response.setStatus(responseWrapper.getCode());

        log.info("Returning invalid token response, rest message: {}", responseWrapper.getMessage());

        return false;
    }
}

