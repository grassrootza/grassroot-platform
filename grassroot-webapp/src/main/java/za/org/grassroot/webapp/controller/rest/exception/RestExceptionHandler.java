package za.org.grassroot.webapp.controller.rest.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import za.org.grassroot.services.exception.CampaignNotFoundException;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.persistence.Entity;
import java.util.Locale;

@Slf4j
@ControllerAdvice(annotations = Grassroot2RestController.class)
public class RestExceptionHandler {

    private  final MessageSource messageSource;

    @Autowired
    public RestExceptionHandler(MessageSource messageSource){
        this.messageSource = messageSource;
    }

    @ExceptionHandler(CampaignNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ServiceErrorMessage handleCampaignNotFoundException(CampaignNotFoundException e){
        String message = messageSource.getMessage(e.getErrorCode(), new Object[]{}, Locale.getDefault());
        if(!StringUtils.isEmpty(message)) {
            log.error(message);
        }
        return new ServiceErrorMessage(e.getErrorCode(), message);
    }

    @ExceptionHandler(value = MemberLacksPermissionException.class)
    public ResponseEntity<ResponseWrapper> permissionDeniedResponse(MemberLacksPermissionException e) {
        return RestUtil.lackPermissionResponse(e.getPermissionRequired());
    }

    @ExceptionHandler(value = InterfaceNotOpenException.class)
    public ResponseEntity interfaceNotOpenToUser() {
        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INTERFACE_NOT_OPEN);
    }

}