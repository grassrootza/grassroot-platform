package za.org.grassroot.webapp.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapperImpl;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Created by paballo on 2016/03/15.
 */

public class TokenValidationInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    private PasswordTokenService passwordTokenService;

    // private static final Logger log = LoggerFactory.getLogger(TokenValidationInterceptor.class);

    private static final String contentType = "application/json";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception {

        Map pathVariables = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        String phoneNumber = String.valueOf(pathVariables.get("phoneNumber")).trim();
        String code = String.valueOf(pathVariables.get("code")).trim();

        if (passwordTokenService.isLongLiveAuthValid(phoneNumber, code)) {
            return true;
        } else {
            final ObjectMapper mapper = new ObjectMapper();
            final ObjectWriter ow = mapper.writer();
            VerificationTokenCode tokenCode = passwordTokenService.fetchLongLivedAuthCode(phoneNumber);

            ResponseWrapper responseWrapper;
            if (tokenCode != null && passwordTokenService.isExpired(tokenCode)) {
                responseWrapper = new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.TOKEN_EXPIRED,
                        RestStatus.FAILURE);
            } else {
                responseWrapper = new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN,
                        RestStatus.FAILURE);
            }
            response.setContentType(contentType);
            response.getWriter().write(ow.writeValueAsString(responseWrapper));
            response.setStatus(responseWrapper.getCode());

            return false;
        }
    }
}

