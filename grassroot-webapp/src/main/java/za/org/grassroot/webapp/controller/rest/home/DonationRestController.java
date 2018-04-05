package za.org.grassroot.webapp.controller.rest.home;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.integration.payments.PaymentBroker;
import za.org.grassroot.integration.payments.peachp.PaymentCopyPayResponse;
import za.org.grassroot.services.account.AccountEmailService;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;

import static za.org.grassroot.integration.payments.peachp.PaymentCopyPayResponse.SUCCESS_MATCHER;

@Slf4j
@RestController
@Grassroot2RestController
@RequestMapping("/api/donate")
public class DonationRestController {

    @Value("${grassroot.payments.sharing.url:http://localhost:8080/donate]")
    private String donationLink;

    private final PaymentBroker paymentBroker;
    private final AccountEmailService emailService;
    private final MessagingServiceBroker messagingBroker;

    @Autowired
    public DonationRestController(PaymentBroker paymentBroker, AccountEmailService emailService, MessagingServiceBroker messagingBroker) {
        this.paymentBroker = paymentBroker;
        this.emailService = emailService;
        this.messagingBroker = messagingBroker;
    }

    @RequestMapping(value = "/initiate", method = RequestMethod.GET)
    public ResponseEntity initiateDonation(@RequestParam int amountZAR) {
        PaymentCopyPayResponse response = paymentBroker.initiateCopyPayCheckout(amountZAR);
        log.info("initiating a donation, for amount : {}, response: {}", amountZAR, response);
        return ResponseEntity.ok(response.getId());
    }

    @RequestMapping(value = "/result", method = RequestMethod.GET)
    public ResponseEntity paymentComplete(@RequestParam String resourcePath) {
        PaymentCopyPayResponse response = paymentBroker.getPaymentResult(resourcePath);
        if (SUCCESS_MATCHER.matcher(response.getInternalCode()).find()) {
            log.info("successful payment! internal code: {}", response.getInternalCode());
            return ResponseEntity.ok(RestMessage.PAYMENT_SUCCESS);
        } else {
            log.info("payment failed! internal code: {}", response.getInternalCode());
            return ResponseEntity.ok(RestMessage.PAYMENT_ERROR);
        }
    }

    @RequestMapping(value = "/share", method = RequestMethod.GET)
    public ResponseEntity shareLink(@RequestParam String senderName,
                                    @RequestParam String emailAddress) {
        GrassrootEmail email = emailService.generateDonationShareEmail(senderName, emailAddress, donationLink);
        messagingBroker.sendEmail(email);
        return ResponseEntity.ok().build();
    }
}
