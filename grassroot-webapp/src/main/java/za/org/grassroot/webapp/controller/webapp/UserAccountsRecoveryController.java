package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.integration.messaging.MessageServicePushResponse;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.UserAccountRecovery;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class UserAccountsRecoveryController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(UserAccountsRecoveryController.class);

    @Autowired
    private PasswordTokenService  passwordTokenService;
    @Autowired
    private UserManagementService userManagementService;
    @Autowired
    private MessagingServiceBroker messagingServiceBroker;
    @Autowired
    private Environment environment;

    @Autowired
    @Qualifier("userAccountRecoveryValidator")
    private Validator validator;

    @InitBinder
    private void initBinder(WebDataBinder binder) {
        binder.setValidator(validator);
    }

    @RequestMapping(value = "/user/recovery", method = RequestMethod.GET)
    public String index(Model model) {
        model.addAttribute("userAccountRecovery", new UserAccountRecovery());
        return "user/recovery";
    }


    @RequestMapping(value = "/grass-root-verification/{msisdn}", method = RequestMethod.GET)
    public @ResponseBody String getTime(@PathVariable("msisdn") String phoneNumber) {

        /******************************************************************************************
         * DUE TO SECURITY CONCERNS: NEVER EVER send token back to Web Client! Must be sent to Mobile Phone.
         ******************************************************************************************/

        VerificationTokenCode verificationTokenCode = null;
        try {
            verificationTokenCode = passwordTokenService.generateShortLivedOTP(phoneNumber);
        } catch (Exception e) {
            log.error("Could not generate verification token for {}", phoneNumber);
        }
        temporaryTokenSend(verificationTokenCode);
        return null;
    }

    @RequestMapping(value = "/user/recovery", method = RequestMethod.POST)
    public ModelAndView handleAccountRecovery(Model model,
                                              @ModelAttribute("userAccountRecovery") @Validated UserAccountRecovery userAccountRecovery,
                                              BindingResult bindingResult, HttpServletRequest request,
                                              RedirectAttributes redirectAttributes) {

        log.info("Okay we've been given the token code back, it is : " + userAccountRecovery.getVerificationCode());

        if (bindingResult.hasErrors()) {
            log.info("Error on the binding result");
            model.addAttribute("userAccountRecovery", userAccountRecovery);
            if (bindingResult.getFieldError("verificationCode") != null) {
                addMessage(model, MessageType.ERROR, bindingResult.getFieldError("verificationCode").getCode(), request);
            } else {
                addMessage(model, MessageType.ERROR, "user.account.recovery.error", request);
            }
            return new ModelAndView("user/recovery", model.asMap());
        }

        userManagementService.resetUserPassword(userAccountRecovery.getUsername(), userAccountRecovery.getNewPassword(),
                userAccountRecovery.getVerificationCode());

        ModelAndView modelView = new ModelAndView(new RedirectView("/login"));

        addMessage(redirectAttributes, MessageType.SUCCESS, "user.account.recovery.password.reset.success", request);

        return modelView;
    }

    @RequestMapping(value = "/user/recovery/success", method = RequestMethod.GET)
    public ModelAndView index(Model model, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (redirectAttributes.getFlashAttributes().containsKey("accountRecoverySuccess")) {
            log.info("Succesfully reset password!");
            addMessage(model, MessageType.SUCCESS, "user.account.recovery.password.reset.success", request);
            return new ModelAndView("user/recovery-success", model.asMap());
        } else {
            return new ModelAndView(new RedirectView("/login"));
        }
    }

    private void temporaryTokenSend(VerificationTokenCode verificationTokenCode) {
        if (verificationTokenCode != null) {
            MessageServicePushResponse messageResult = messagingServiceBroker.
                    sendPrioritySMS(getMessage("user.profile.token.message",
                            verificationTokenCode.getCode()),
                            verificationTokenCode.getUsername());
            if (!environment.acceptsProfiles("production")) {
                log.info("For token {}, sms send result: {}", verificationTokenCode.getCode(), messageResult.toString());
            }
        }
    }
}
