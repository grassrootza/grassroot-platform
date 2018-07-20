package za.org.grassroot.webapp.controller.rest.account;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.payments.PaymentBroker;
import za.org.grassroot.integration.payments.peachp.PaymentCopyPayResponse;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;

import static za.org.grassroot.integration.payments.peachp.PaymentCopyPayResponse.SUCCESS_MATCHER;

@RestController @Grassroot2RestController
@RequestMapping("/v2/api/payment") @Slf4j
@ConditionalOnProperty("grassroot.payments.enabled")
public class PaymentRestController {

    private final PaymentBroker paymentBroker;

    @Autowired
    public PaymentRestController(PaymentBroker paymentBroker) {
        this.paymentBroker = paymentBroker;
    }

    @RequestMapping(value = "/initiate", method = RequestMethod.GET)
    public ResponseEntity initiateDonation(@RequestParam int amountZAR,
                                           @RequestParam(required = false) Boolean recurring) {
        PaymentCopyPayResponse response = paymentBroker.initiateCopyPayCheckout(amountZAR, recurring != null && recurring);
        log.info("initiating a donation, for amount : {}, recurring: {}, response: {}", amountZAR, recurring, response);
        return ResponseEntity.ok(response.getId());
    }

    @RequestMapping(value = "/result", method = RequestMethod.GET)
    public ResponseEntity paymentComplete(@RequestParam String resourcePath,
                                          @RequestParam(required = false) String accountId) {
        PaymentCopyPayResponse response = paymentBroker.getPaymentResult(resourcePath, accountId != null, accountId);
        if (response != null && SUCCESS_MATCHER.matcher(response.getInternalCode()).find()) {
            log.info("successful payment! response: {}", response);
            return ResponseEntity.ok(response.getRegistrationId());
        } else {
            log.info("payment failed! internal code: {}", response != null ? response.getInternalCode() : "null");
            return ResponseEntity.ok(RestMessage.PAYMENT_ERROR);
        }
    }

}
