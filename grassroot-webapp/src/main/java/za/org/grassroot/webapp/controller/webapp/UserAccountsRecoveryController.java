package za.org.grassroot.webapp.controller.webapp;

import org.apache.log4j.spi.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.services.PasswordTokenService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.UserAccountRecovery;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class UserAccountsRecoveryController extends BaseController {

    private Logger log = org.slf4j.LoggerFactory.getLogger(UserAccountsRecoveryController.class);

    @Autowired
    private PasswordTokenService  passwordTokenService;
    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    @Qualifier("userAccountRecoveryValidator")
    private Validator validator;

    @InitBinder
    private void initBinder(WebDataBinder binder) {
        binder.setValidator(validator);
    }

    @RequestMapping(value = "/accounts/recovery", method = RequestMethod.GET)
    public ModelAndView index(Model model) {
        model.addAttribute("userAccountRecovery", new UserAccountRecovery());
        return new ModelAndView("accounts/recovery", model.asMap());
    }


    @RequestMapping(value = "/grass-root-verification/{grassRootID}", method = RequestMethod.GET)
    public
    @ResponseBody
    String getTime(@PathVariable("grassRootID") String grassRootID) {

        /******************************************************************************************
         * DUE TO SECURITY CONCERNS: NEVER EVER send token back to Web Client! Must be sent to Mobile Phone.
         ******************************************************************************************/

        VerificationTokenCode verificationTokenCode = null;
        try {
            verificationTokenCode = passwordTokenService.generateVerificationCode(grassRootID);
        } catch (Exception e) {
            log.error("Could not generate verification token for {}", grassRootID);
        }
        temporaryTokenSend(verificationTokenCode);
        return null;
    }

    @RequestMapping(value = "/accounts/recovery", method = RequestMethod.POST)
    public ModelAndView handleAccountRecovery(Model model,
                                              @ModelAttribute("userAccountRecovery") @Validated UserAccountRecovery userAccountRecovery,
                                              BindingResult bindingResult, HttpServletRequest request,
                                              RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("userAccountRecovery", userAccountRecovery);
            addMessage(model, MessageType.ERROR, "user.account.recovery.error", request);
            return new ModelAndView("accounts/recovery", model.asMap());
        }
        userManagementService.resetUserPassword(userAccountRecovery.getUsername(), userAccountRecovery.getNewPassword(),
                userAccountRecovery.getVerificationCode());

        ModelAndView modelView = new ModelAndView(new RedirectView("/login"));

        addMessage(redirectAttributes, MessageType.SUCCESS, "user.account.recovery.password.reset.success", request);

        return modelView;
    }

    @RequestMapping(value = "/accounts/recovery/success", method = RequestMethod.GET)
    public ModelAndView index(Model model, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (redirectAttributes.getFlashAttributes().containsKey("accountRecoverySuccess")) {
            addMessage(model, MessageType.SUCCESS, "user.account.recovery.password.reset.success", request);
        } else {
            new ModelAndView(new RedirectView("/login"));
        }
        return new ModelAndView("accounts/recovery-success", model.asMap());

    }

    /**
     * @param verificationTokenCode
     * @todo This piece of code is temporary and will be moved to messaging
     */
    private void temporaryTokenSend(VerificationTokenCode verificationTokenCode) {
        try {

            if (verificationTokenCode != null && System.getenv("SMSUSER") != null && System.getenv("SMSPASS") != null) {
                RestTemplate sendGroupSMS = new RestTemplate();
                UriComponentsBuilder sendMsgURI = UriComponentsBuilder.newInstance().scheme("https").host("xml2sms.gsm.co.za");
                sendMsgURI.path("send/").queryParam("username", System.getenv("SMSUSER")).queryParam("password", System.getenv("SMSPASS"));

                sendMsgURI.queryParam("number", verificationTokenCode.getUsername());
                sendMsgURI.queryParam("message", String.join("Your GrassRoot verification code is: ", verificationTokenCode.getCode()));

                String messageResult = sendGroupSMS.getForObject(sendMsgURI.build().toUri(), String.class);
                log.debug("SMS Send result: {}", messageResult);
            } else {
                log.warn("Did not send verification message. No system messaging configuration found.");
            }
        } catch (Exception exception) {
            log.error("Could not send token message", exception);
        }

    }
}
