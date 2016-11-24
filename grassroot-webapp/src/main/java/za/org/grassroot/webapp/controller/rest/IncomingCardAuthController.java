package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.integration.payments.PaymentServiceBroker;

/**
 * Created by luke on 2016/11/14.
 */
@Controller
@RequestMapping("/cardauth")
public class IncomingCardAuthController {

    private static final Logger logger = LoggerFactory.getLogger(IncomingCardAuthController.class);

    private PaymentServiceBroker paymentBroker;

    @Autowired
    public IncomingCardAuthController(PaymentServiceBroker paymentBroker) {
        this.paymentBroker = paymentBroker;
    }

    @RequestMapping("/3dsecure/response/{type}")
    public @ResponseBody String receiveAuthorizationResponse(@PathVariable String type, @RequestParam String id,
                                                             @RequestParam String resourcePath, @RequestBody String serverResponse,
                                                             HttpRequest request, RedirectAttributes attributes) {

        logger.info("server response: {}", serverResponse);
        attributes.addAttribute("paymentId", id);
        attributes.addAttribute("succeeded", true);
        logger.info("request: {}", request.toString());

        // AsyncPaymentResponse response = unmarshaller.unmarshal(serverResponse);

        return "redirect:/account/payment/done";
    }

}
