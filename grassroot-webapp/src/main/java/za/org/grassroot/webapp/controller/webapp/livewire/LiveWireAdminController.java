package za.org.grassroot.webapp.controller.webapp.livewire;

import liquibase.util.StringUtils;
import org.apache.poi.EmptyFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.enums.DataSubscriberType;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.DataImportBroker;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by luke on 2017/05/06.
 */
@Controller
@RequestMapping("/livewire/")
@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
public class LiveWireAdminController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireAdminController.class);

    private static final Pattern emailSplitPattern = Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b",
            Pattern.CASE_INSENSITIVE);

    private final DataSubscriberBroker subscriberBroker;
    private final PasswordTokenService tokenService;
    private final DataImportBroker dataImportBroker;

    @Autowired
    public LiveWireAdminController(DataSubscriberBroker subscriberBroker, PasswordTokenService tokenService, DataImportBroker dataImportBroker) {
        this.subscriberBroker = subscriberBroker;
        this.tokenService = tokenService;
        this.dataImportBroker = dataImportBroker;
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/home", method = RequestMethod.GET)
    public String listLiveWireSubscribers(Model model) {
        model.addAttribute("subscribers",
                subscriberBroker.listSubscribers(false, new Sort(Sort.Direction.ASC, "displayName")));
        return "livewire/admin/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/create", method = RequestMethod.GET)
    public String createDataSubscriberForm() {
        return "livewire/admin/create_sub";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/create", method = RequestMethod.POST)
    public String createDataSubscriberDo(@RequestParam String displayName, @RequestParam String primaryEmail,
                                         @RequestParam(required = false) Boolean addToPushEmails,
                                         @RequestParam(required = false) String emailsForPush,
                                         @RequestParam(required = false) Boolean active,
                                         RedirectAttributes attributes, HttpServletRequest request) {
        List<String> emails = emailsForPush == null ? null : splitEmailInput(emailsForPush);
        try {
            subscriberBroker.create(getUserProfile().getUid(), displayName, primaryEmail,
                    addToPushEmails != null && addToPushEmails, emails, active != null && active);
            addMessage(attributes, MessageType.SUCCESS, "livewire.subscriber.create.success", request);
        } catch (Exception e) {
            logger.error(e.toString());
            addMessage(attributes, MessageType.ERROR, "livewire.subscriber.create.error", request);
        }
        return "redirect:/livewire/admin/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/toggleactive", method = RequestMethod.POST)
    public String activateSubscriber(@RequestParam String subscriberUid, @RequestParam String otpEntered,
                                     RedirectAttributes attributes, HttpServletRequest request) {
        if (!tokenService.isShortLivedOtpValid(getUserProfile().getPhoneNumber(), otpEntered)) {
            // todo : show page instead
            throw new AccessDeniedException("Error! Admin user did not validate with OTP");
        }

        DataSubscriber subscriber = subscriberBroker.validateSubscriberAdmin(getUserProfile().getUid(), subscriberUid);
        subscriberBroker.updateActiveStatus(getUserProfile().getUid(), subscriberUid, !subscriber.isActive());
        addMessage(attributes, MessageType.SUCCESS,
                "livewire.subscriber." + (subscriber.isActive() ? "activated" : "deactivated"),
                request);
        return "redirect:/livewire/admin/home";
    }

    @RequestMapping(value = "/subscriber/view", method = RequestMethod.GET)
    public String viewDataSubscriber(@RequestParam String subscriberUid, Model model) {
        try {
            DataSubscriber subscriber = subscriberBroker.validateSubscriberAdmin(
                    getUserProfile().getUid(),
                    subscriberUid
            );
            model.addAttribute("subscriber", subscriber);
            model.addAttribute("adminPhoneNumber", getUserProfile().getPhoneNumber());
            // this is a little heavy but will be very infrequently used (if an issue, just create a loadUsers)
            List<User> users = subscriber.getUsersWithAccess().stream()
                    .map(uid -> userManagementService.load(uid)).collect(Collectors.toList());
            model.addAttribute("users", users);
            return "livewire/subscriber_view";
        } catch (AccessDeniedException e) {
            return "livewire/access_denied";
        }
    }

    @RequestMapping(value = "/subscriber/emails/add", method = RequestMethod.POST)
    public String addPushEmailsToSubscriber(@RequestParam String subscriberUid,
                                            @RequestParam(required = false) String emailsToAdd,
                                            @RequestParam(required = false) MultipartFile emailsXls,
                                            RedirectAttributes attributes, HttpServletRequest request) {
        try {
            DataSubscriber subscriber = subscriberBroker.validateSubscriberAdmin(getUserProfile().getUid(), subscriberUid);
            List<String> emails = new ArrayList<>();

            if (emailsToAdd != null) {
                emails.addAll(splitEmailInput(emailsToAdd));
            }

            logger.info("emailsXls exists? {}", emailsXls != null);
            if (emailsXls != null) {
                emails.addAll(extractEmailsFromXls(emailsXls));
                logger.info("Extracted emails from spreadsheet, result: {}", emails);
            }

            if (emails.isEmpty()) {
                addMessage(attributes, MessageType.ERROR, "livewire.emails.add.empty", request);
            } else {
                subscriberBroker.addPushEmails(getUserProfile().getUid(), subscriber.getUid(), emails);
                addMessage(attributes, MessageType.SUCCESS, "livewire.emails.add.done", request);
            }
            attributes.addAttribute("subscriberUid", subscriber.getUid());
            return "redirect:/livewire/subscriber/view";
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.emails.add.denied", request);
            return "livewire/access_denied";
        }
    }

    private List<String> extractEmailsFromXls(MultipartFile file) {
        try {
            File tempStore = File.createTempFile("emails", "xls");
            tempStore.deleteOnExit();
            file.transferTo(tempStore);
            return dataImportBroker.extractFirstColumnOfSheet(tempStore);
        } catch (IOException|EmptyFileException e) {
            return new ArrayList<>();
        }
    }

    @RequestMapping(value = "/subscriber/emails/remove", method = RequestMethod.POST)
    public String removePushEmailFromSubscriber(@RequestParam String subscriberUid, @RequestParam String emailsToRemove,
                                                RedirectAttributes attributes, HttpServletRequest request) {
        DataSubscriber subscriber = subscriberBroker.validateSubscriberAdmin(getUserProfile().getUid(), subscriberUid);
        List<String> emails = splitEmailInput(emailsToRemove);
        if (StringUtils.isEmpty(emailsToRemove)) {
            addMessage(attributes, MessageType.ERROR, "livewire.emails.remove.empty", request);
        } else {
            subscriberBroker.removePushEmails(getUserProfile().getUid(), subscriber.getUid(), emails);
            addMessage(attributes, MessageType.SUCCESS, "livewire.emails.remove.done", request);
        }
        attributes.addAttribute("subscriberUid", subscriber.getUid());
        return "redirect:/livewire/subscriber/view";
    }

    @RequestMapping(value = "/subscriber/user/add", method = RequestMethod.POST)
    public String addUserUidToSubscriber(@RequestParam String subscriberUid, @RequestParam String addUserPhone,
                                         RedirectAttributes attributes, HttpServletRequest request) {
        try {
            String msisdn = PhoneNumberUtil.convertPhoneNumber(addUserPhone);
            User user = userManagementService.findByInputNumber(msisdn);
            subscriberBroker.addUsersWithViewAccess(getUserProfile().getUid(), subscriberUid,
                    Collections.singleton(user.getUid()));
            addMessage(attributes, MessageType.SUCCESS, "livewire.user.added.done", request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.user.added.denied", request);
        } catch (InvalidPhoneNumberException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.user.added.badnumber", request);
        } catch (NoSuchUserException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.user.added.nouser", request);
        }
        attributes.addAttribute("subscriberUid", subscriberUid);
        return "redirect:/livewire/subscriber/view";
    }

    @RequestMapping(value = "/subscriber/user/remove", method = RequestMethod.POST)
    public String removeUserUidFromSubscriber(@RequestParam String subscriberUid, @RequestParam String userToRemoveUid,
                                              RedirectAttributes attributes, HttpServletRequest request) {
        try {
            subscriberBroker.removeUsersWithViewAccess(getUserProfile().getUid(), subscriberUid,
                    Collections.singleton(userToRemoveUid));
            addMessage(attributes, MessageType.SUCCESS, "livewire.user.removed.done", request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.user.removed.denied", request);
        }
        attributes.addAttribute("subscriberUid", subscriberUid);
        return "redirect:/livewire/subscriber/view";
    }

    @RequestMapping(value = "/subscriber/permissions/change", method = RequestMethod.POST)
    public String alterDataSubscriberPermissions(@RequestParam String subscriberUid,
                                                 @RequestParam(required = false) Boolean canTag,
                                                 @RequestParam(required = false) Boolean canRelease,
                                                 RedirectAttributes attributes, HttpServletRequest request) {
        try {
            subscriberBroker.updateSubscriberPermissions(getUserProfile().getUid(), subscriberUid,
                    canTag != null ? canTag : false, canRelease != null ? canRelease : false);
            addMessage(attributes, MessageType.SUCCESS, "livewire.permissions.change.done", request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.permissions.change.denied", request);
        }
        attributes.addAttribute("subscriberUid", subscriberUid);
        return "redirect:/livewire/subscriber/view";
    }

    private List<String> splitEmailInput(String emailsInSingleString) {
        Matcher emailMatcher = emailSplitPattern.matcher(emailsInSingleString);
        List<String> emails = new ArrayList<>();
        while (emailMatcher.find()) {
            emails.add(emailMatcher.group());
        }
        return emails;
    }

    @RequestMapping(value = "/subscriber/type/change", method = RequestMethod.POST)
    public String alterDataSubscriberType(@RequestParam String subscriberUid,
                                          @RequestParam DataSubscriberType subscriberType,
                                          RedirectAttributes attributes, HttpServletRequest request) {
        subscriberBroker.updateSubscriberType(getUserProfile().getUid(), subscriberUid, subscriberType);
        addMessage(attributes, MessageType.SUCCESS, "livewire.subscriber.type.changed", request);
        attributes.addAttribute("subscriberUid", subscriberUid);
        return "redirect:/livewire/subscriber/view";
    }

}
