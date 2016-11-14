package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created by luke on 2016/11/14.
 */
@Controller
@RequestMapping("/cardauth")
public class IncomingCardAuthController {

    private static final Logger logger = LoggerFactory.getLogger(IncomingCardAuthController.class);

    @RequestMapping("/3dsecure/response")
    public @ResponseBody String receiveAuthorizationResponse(@RequestBody String serverResponse) {
        logger.info(serverResponse);
        return "hello";
    }

}
