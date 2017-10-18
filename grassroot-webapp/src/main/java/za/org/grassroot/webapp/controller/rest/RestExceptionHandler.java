package za.org.grassroot.webapp.controller.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

@ControllerAdvice(annotations = Grassroot2RestController.class)
public class RestExceptionHandler {

    @ExceptionHandler(value = MemberLacksPermissionException.class)
    public ResponseEntity<ResponseWrapper> permissionDeniedResponse(MemberLacksPermissionException e) {
        return RestUtil.lackPermissionResponse(e.getPermissionRequired());
    }



}
