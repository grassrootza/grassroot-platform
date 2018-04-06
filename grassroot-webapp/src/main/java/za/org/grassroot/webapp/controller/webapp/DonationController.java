package za.org.grassroot.webapp.controller.webapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.integration.payments.PaymentBroker;
import za.org.grassroot.integration.payments.peachp.PaymentCopyPayResponse;
import za.org.grassroot.webapp.controller.BaseController;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Pattern;

import static za.org.grassroot.integration.payments.peachp.PaymentCopyPayResponse.SUCCESS_MATCHER;

@Controller @Slf4j
@RequestMapping("/donate")
@PropertySource(value = "${grassroot.payments.properties}", ignoreResourceNotFound = true)
public class DonationController extends BaseController {

    @Value("${grassroot.payments.url:http://paymentsurl.com}")
    private String paymentUrl;

    private String paymentEntityId;

    private String successUrl;
    private String resultsEmail;
    private String sharingUrl;

    private final Environment environment;
    private final MessagingServiceBroker messageBroker;
    private final PaymentBroker paymentBroker;
    private final TemplateEngine templateEngine;

    @Autowired
    public DonationController(Environment environment, MessagingServiceBroker messageBroker, PaymentBroker paymentBroker, TemplateEngine templateEngine) {
        this.environment = environment;
        this.messageBroker = messageBroker;
        this.paymentBroker = paymentBroker;
        this.templateEngine = templateEngine;
    }

    @PostConstruct
    public void init() {
        // paymentUrl = environment.getProperty("grassroot.payments.url", "https://test.oppwa.com/v1/checkouts");
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
            PaymentCopyPayResponse response = paymentBroker.initiateCopyPayCheckout(amount);
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
            PaymentCopyPayResponse response = paymentBroker.getPaymentResult(resourcePath);
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

    private void sendSuccessEmail(String name, String email, int amount) {
        messageBroker.sendEmail(new GrassrootEmail.EmailBuilder("Successful donation")
                .toAddress(resultsEmail).toName("Grassroot")
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
            messageBroker.sendEmail(new GrassrootEmail.EmailBuilder(subject)
                    .toAddress(toEmail).toName(toName).fromName("Grassroot")
                    .htmlContent(htmlContent).content(htmlContent).build());
        } catch (Exception e) {
            log.error("error seniding sharing mail: ", e);
        }

    }
}
