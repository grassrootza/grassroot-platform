package za.org.grassroot.webapp.controller.webapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.PaymentSystemResponse;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

@Controller @Slf4j
@RequestMapping("/donate")
@PropertySource(value = "${grassroot.payments.properties}", ignoreResourceNotFound = true)
public class DonationController extends BaseController {

    private static final Pattern SUCCESS_MATCHER = Pattern.compile("^(000\\.000\\.|000\\.100\\.1|000\\.[36])");

    @Value("${grassroot.payments.url:http://paymentsurl.com}")
    private String paymentUrl;

    private String paymentUserId;
    private String paymentPassword;
    private String paymentEntityId;

    private String successUrl;
    private String resultsEmail;
    private String sharingUrl;

    private final RestTemplate restTemplate;
    private final Environment environment;
    private final MessagingServiceBroker messageBroker;
    private final TemplateEngine templateEngine;

    @Autowired
    public DonationController(RestTemplate restTemplate, Environment environment, MessagingServiceBroker messageBroker, TemplateEngine templateEngine) {
        this.restTemplate = restTemplate;
        this.environment = environment;
        this.messageBroker = messageBroker;
        this.templateEngine = templateEngine;
    }

    @PostConstruct
    public void init() {
        // paymentUrl = environment.getProperty("grassroot.payments.url", "https://test.oppwa.com/v1/checkouts");
        paymentUserId = environment.getProperty("grassroot.payments.values.user", "8a8294174e735d0c014e78cf266b1794");
        paymentPassword = environment.getProperty("grassroot.payments.values.password", "qyyfHCN83e");
        paymentEntityId = environment.getProperty("grassroot.payments.values.channelId3d", "8a8294174e735d0c014e78cf26461790");
        successUrl = environment.getProperty("grassroot.payments.success.url", "https://test.grassroot.org.za/donate/success");
        resultsEmail = environment.getProperty("grassroot.payments.donate.email", "test@grassroot.org.za");
        sharingUrl = environment.getProperty("grassroot.payments.sharing.url", "https://localhost:8080/donate");
    }

    @RequestMapping(value = { "", "/" }, method = RequestMethod.GET)
    public String initiateDonation() {
        log.info("initiating donation, url ={}, entity ID = {}", paymentUrl, paymentEntityId);
        return "donate/initiate";
    }

    @RequestMapping(value = "/payment", method = RequestMethod.GET)
    public String initiatePayment(Model model, @RequestParam int amount, @RequestParam String name, @RequestParam String email) {
        try {
            long startTime = System.currentTimeMillis();
            PaymentSystemResponse response = initiateCheckout(amount);
            log.info("got checkout response in {} msecs, looks like {}", startTime - System.currentTimeMillis(), response);
            model.addAttribute("paymentJsUrl",
                    paymentUrl + "/v1/paymentWidgets.js?checkoutId=" + response.getId());
            model.addAttribute("successUrl", successUrl + "?name=" + urlEncode(urlEncode(name)) + "&email=" + urlEncode(email) + "&amount=" + amount);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return "donate/checkout";
    }

    private String urlEncode(String string) {
        try {
            return URLEncoder.encode(string, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return string;
        }
    }

    @RequestMapping(value = "/success", method = RequestMethod.GET)
    public String paymentComplete(@RequestParam(required = false) String resourcePath,
                                  @RequestParam(required = false) String name,
                                  @RequestParam(required = false) String email,
                                  @RequestParam(required = false) int amount,
                                  Model model, RedirectAttributes attributes, HttpServletRequest request) {
        try {
            PaymentSystemResponse response = getPaymentResult(resourcePath);
            log.info("got this response: {}", response.toString());
            if (SUCCESS_MATCHER.matcher(response.getInternalCode()).find()) {
                log.info("successful payment! internal code: {}", response.getInternalCode());
                sendSuccessEmail(name, email, amount);
                model.addAttribute("fromName", name);
                return "donate/success";
            } else {
                log.info("no success! internal code: {}", response.getInternalCode());
                return errorRedirectToCheckout(attributes, request, amount, name, email);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return errorRedirectToCheckout(attributes, request, amount, name, email);
        }
    }

    private String errorRedirectToCheckout(RedirectAttributes attributes, HttpServletRequest request, int amount, String name, String email) {
        addMessage(attributes, MessageType.ERROR, "donation.payment.error", request);
        attributes.addAttribute("amount", amount);
        attributes.addAttribute("name", name);
        attributes.addAttribute("email", email);
        return "redirect:/donate/payment";
    }

    @RequestMapping(value = "/share", method = RequestMethod.GET)
    public String shareLink(Model model, HttpServletRequest request, @RequestParam String fromName,
                            @RequestParam(required = false) String toName,
                            @RequestParam(required = false) String toAddress) {
        if (!StringUtils.isEmpty(toName) && !StringUtils.isEmpty(toAddress)) {
            log.info("trying to send sharing email ...");
            sendSharingEmail(fromName, toName, toAddress);
            addMessage(model, MessageType.SUCCESS, "donation.share.success", new String[] { fromName }, request);
            model.addAttribute("fromName", fromName);
            return "donate/success";
        } else {
            addMessage(model, MessageType.ERROR, "donation.share.error", request);
            model.addAttribute("fromName", fromName);
            return "donate/success";
        }
    }

    private PaymentSystemResponse initiateCheckout(final int amount) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(paymentUrl + "/v1/checkouts")
                .queryParam("authentication.userId", paymentUserId)
                .queryParam("authentication.password", paymentPassword)
                .queryParam("authentication.entityId", paymentEntityId)
                .queryParam("amount", amount + ".00")
                .queryParam("currency", "ZAR")
                .queryParam("paymentType", "DB");

        HttpHeaders stdHeaders = new HttpHeaders();
        stdHeaders.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        ResponseEntity<PaymentSystemResponse> response = restTemplate.exchange(
                builder.build().toUri(), HttpMethod.POST, new HttpEntity<>(stdHeaders), PaymentSystemResponse.class);
        return response.getBody();
    }

    private PaymentSystemResponse getPaymentResult(String resourcePath) {
        URI requestUri = UriComponentsBuilder.fromUriString(paymentUrl + resourcePath).build().toUri();
        log.info("requesting payment result via URI: {}", requestUri.toString());
        ResponseEntity<PaymentSystemResponse> response = restTemplate.getForEntity(requestUri, PaymentSystemResponse.class);
        return response.getBody();
    }

    private void sendSuccessEmail(String name, String email, int amount) {
        messageBroker.sendEmail(Collections.singletonList(resultsEmail), new GrassrootEmail.EmailBuilder("Successful donation")
                .content("Successful donation received from " + name  + " (" + email + "), for R" + amount + ",")
                .build());
    }

    private void sendSharingEmail(String fromName, String toName, String toEmail) {
        try {
            final String subject = fromName + " thinks you should donate to Grassroot";
            final Context ctx = new Context(Locale.getDefault());
            ctx.setVariable("toName", toName);
            ctx.setVariable("fromName", fromName);
            ctx.setVariable("shareLink", sharingUrl);
            final String htmlContent = templateEngine.process("donate/share_email", ctx);
            log.info("processed template ... firing off mail");
            log.debug("email template processed looks like: {}", htmlContent);
            messageBroker.sendEmail(Collections.singletonList(toEmail), new GrassrootEmail.EmailBuilder(subject)
                    .from("Grassroot").htmlContent(htmlContent).content(htmlContent).build());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
