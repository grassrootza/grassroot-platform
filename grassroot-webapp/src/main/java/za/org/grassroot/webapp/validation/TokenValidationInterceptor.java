package za.org.grassroot.webapp.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.services.PasswordTokenManager;
import za.org.grassroot.services.PasswordTokenService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Created by paballo on 2016/03/15.
 */



public class TokenValidationInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    PasswordTokenService passwordTokenService;

    private static final Logger log = LoggerFactory.getLogger(TokenValidationInterceptor.class);
    private static final int TOKEN_LIFE_SPAN_DAYS = 10;
    private ObjectMapper mapper;
    private ObjectWriter ow;
    private ResponseWrapper responseWrapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception {

        Map pathVariables = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String phoneNumber = String.valueOf(pathVariables.get("phoneNumber"));
        String code = String.valueOf(pathVariables.get("code"));
        if(passwordTokenService.isVerificationCodeValid(phoneNumber, code)){
            return true;
        }
            mapper = new ObjectMapper();
            ow = mapper.writer();
            VerificationTokenCode tokenCode = passwordTokenService.getVerificationCode(phoneNumber);
            if(tokenCode !=null && isExpired(tokenCode)) {
                responseWrapper = new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.TOKEN_EXPIRED,
                        RestStatus.FAILURE);
            }else {
                responseWrapper = new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN,
                        RestStatus.FAILURE);
            }
            response.setContentType("application/json");
            response.getWriter().write(ow.writeValueAsString(responseWrapper));

        return false;

    }

    private boolean isExpired(VerificationTokenCode tokenCode){
        return Duration.between(LocalDateTime.now(),tokenCode.getCreatedDateTime().toLocalDateTime()).toDays()>
                TOKEN_LIFE_SPAN_DAYS;
    }





    }

