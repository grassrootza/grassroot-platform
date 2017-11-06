package za.org.grassroot.webapp.controller.rest.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import za.org.grassroot.services.exception.CampaignNotFoundException;

import java.util.Locale;

@ControllerAdvice
public class RestControllersAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(RestControllersAdvice.class);
    private  final MessageSource messageSource;

    @Autowired
    public RestControllersAdvice(MessageSource messageSource){
        this.messageSource = messageSource;
    }

    @ExceptionHandler(CampaignNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ServiceErrorMessage handleCampaignNotFoundException(CampaignNotFoundException e){
        String message = messageSource.getMessage(e.getErrorCode(), new Object[]{}, Locale.getDefault());
        if(!StringUtils.isEmpty(message)) {
            LOG.error(message);
        }
        return new ServiceErrorMessage(e.getErrorCode(), message);
    }
}
