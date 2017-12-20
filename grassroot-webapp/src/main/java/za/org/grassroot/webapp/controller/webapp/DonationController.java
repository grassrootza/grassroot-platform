package za.org.grassroot.webapp.controller.webapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.webapp.model.CheckoutInitiateResponse;

import javax.annotation.PostConstruct;

@Controller @Slf4j
@RequestMapping("/donate")
public class DonationController {

    private String paymentUrl;
    private String paymentUserId;
    private String paymentPassword;
    private String paymentEntityId;
    private String successUrl;

    private final RestTemplate restTemplate;
    private final Environment environment;

    @Autowired
    public DonationController(RestTemplate restTemplate, Environment environment) {
        this.restTemplate = restTemplate;
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        paymentUrl = environment.getProperty("grassroot.payments.url", "https://test.oppwa.com/v1/checkouts");
        paymentUserId = environment.getProperty("grassroot.payments.values.user", "8a8294174e735d0c014e78cf266b1794");
        paymentPassword = environment.getProperty("grassroot.payments.values.password", "qyyfHCN83e");
        paymentEntityId = environment.getProperty("grassroot.payments.values.channelId", "8a8294174e735d0c014e78cf26461790");
        successUrl = environment.getProperty("grassroot.payments.success.url", "https://staging.grassroot.org.za/donate/success");
    }

    @RequestMapping(value = { "", "/" }, method = RequestMethod.GET)
    public String initiateDonation() {
        return "donate/initiate";
    }

    @RequestMapping(value = "/payment", method = RequestMethod.GET)
    public String initiatePayment(Model model, @RequestParam int amount, @RequestParam String currency) {
        try {
            long startTime = System.currentTimeMillis();
            CheckoutInitiateResponse response = initiateCheckout(amount, currency);
            log.info("got checkout response in {} msecs, looks like {}", startTime - System.currentTimeMillis(), response);
            model.addAttribute("paymentJsUrl",
                    "https://test.oppwa.com/v1/paymentWidgets.js?checkoutId=" + response.getId());
            model.addAttribute("successUrl", successUrl);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return "donate/checkout";
    }

    @RequestMapping(value = "/success", method = RequestMethod.GET)
    public String paymentComplete(@RequestParam(required = false) String resourcePath) {

        return "donate/success";
    }

    private CheckoutInitiateResponse initiateCheckout(final int amount, final String currency) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(paymentUrl)
                .queryParam("authentication.userId", paymentUserId)
                .queryParam("authentication.password", paymentPassword)
                .queryParam("authentication.entityId", paymentEntityId)
                .queryParam("amount", amount + ".00")
                .queryParam("currency", currency)
                .queryParam("paymentType", "DB");

        HttpHeaders stdHeaders = new HttpHeaders();
        stdHeaders.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        ResponseEntity<CheckoutInitiateResponse> response = restTemplate.exchange(
                builder.build().toUri(), HttpMethod.POST, new HttpEntity<>(stdHeaders), CheckoutInitiateResponse.class);
        return response.getBody();
    }
}
