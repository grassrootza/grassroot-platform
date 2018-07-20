package za.org.grassroot.webapp.controller.rest.home;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.account.AccountEmailService;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

@Slf4j
@RestController @Grassroot2RestController
@RequestMapping("/v2/api/donate")
@ConditionalOnProperty("spring.thymeleaf.enabled")
public class DonationRestController {

    @Value("${grassroot.payments.sharing.url:http://localhost:8080/donate]")
    private String donationLink;

    private final AccountEmailService emailService;
    private final MessagingServiceBroker messagingBroker;

    @Autowired
    public DonationRestController(AccountEmailService emailService, MessagingServiceBroker messagingBroker) {
        this.emailService = emailService;
        this.messagingBroker = messagingBroker;
    }

    @RequestMapping(value = "/share", method = RequestMethod.GET)
    public ResponseEntity shareLink(@RequestParam String senderName,
                                    @RequestParam String emailAddress) {
        GrassrootEmail email = emailService.generateDonationShareEmail(senderName, emailAddress, donationLink);
        messagingBroker.sendEmail(email);
        return ResponseEntity.ok().build();
    }
}
