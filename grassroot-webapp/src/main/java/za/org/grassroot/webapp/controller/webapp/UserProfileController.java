package za.org.grassroot.webapp.controller.webapp;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.socialmedia.ManagedPage;
import za.org.grassroot.integration.socialmedia.ManagedPagesResponse;
import za.org.grassroot.integration.socialmedia.SocialMediaBroker;
import za.org.grassroot.services.exception.InvalidTokenException;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * @author Lesetse Kimwaga
 */
@Controller
@RequestMapping(value = "/user/")
public class UserProfileController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    private final UserManagementService userManagementService;
    private MemberDataExportBroker memberDataExportBroker;
    private SocialMediaBroker socialMediaBroker;

    @Autowired
    public UserProfileController(UserManagementService userManagementService, MemberDataExportBroker memberDataExportBroker, SocialMediaBroker socialMediaBroker) {
        this.userManagementService = userManagementService;
        this.memberDataExportBroker = memberDataExportBroker;
        this.socialMediaBroker = socialMediaBroker;
    }

    @ModelAttribute("sessionUser")
    public User getCurrentUser(Authentication authentication) {
        return (authentication == null) ? null :
                userManagementService.fetchUserByUsernameStrict(((UserDetails) authentication.getPrincipal()).getUsername());
    }

    @RequestMapping(value = "settings", method = RequestMethod.GET)
    public String index(Model model) {
        return "user/settings";
    }

    @RequestMapping(value = "settings", method = RequestMethod.POST)
    public String post(@ModelAttribute User sessionUser, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        log.info("retrieved this user, displayName={}, alertPref={}, language={}", sessionUser.getDisplayName(),
                sessionUser.getAlertPreference(), sessionUser.getLanguageCode());
        try {
            userManagementService.updateUser(getUserProfile().getUid(), sessionUser.getDisplayName(), null, sessionUser.getEmailAddress(),
                    null, sessionUser.getAlertPreference(), new Locale(sessionUser.getLanguageCode()), null);
            addMessage(redirectAttributes, MessageType.SUCCESS, "user.profile.change.success", request);
            return "redirect:settings"; // using redirect to avoid reposting
        } catch (IllegalArgumentException e) {
            addMessage(redirectAttributes, MessageType.ERROR, "user.profile.change.error", request);
            return "redirect:settings";
        }
    }

    @RequestMapping(value = "password", method = RequestMethod.GET)
    public String changePasswordPrompt(@ModelAttribute User sessionUser) {
        return "user/password";
    }

    @RequestMapping(value = "password", method = RequestMethod.POST)
    public String changePasswordDo(Model model, @ModelAttribute User sessionUser, @RequestParam(value = "otp_entered") String otpField,
                                   @RequestParam String password, RedirectAttributes attributes, HttpServletRequest request) {

        // todo : extra validation
        try {
            userManagementService.resetUserPassword(getUserProfile().getUsername(), password, otpField);
            addMessage(attributes, MessageType.SUCCESS, "user.profile.password.done", request);
            return "redirect:/home";
        } catch (InvalidTokenException e) {
            addMessage(model, MessageType.ERROR, "user.profile.password.invalid.otp", request);
            return "user/password";
        }
    }


    @RequestMapping(value = "export-groups", method = RequestMethod.GET)
    public String exportGroupsPrompt(Model model) {

        User user = getUserProfile();
        user = userManagementService.load(user.getUid()); // take fresh copy in order to have groups added since login
        List<Group> activeGroups = user.getGroups().stream().filter(Group::isActive).sorted(Comparator.comparing(Group::getGroupName)).collect(Collectors.toList());
        model.addAttribute("groups", activeGroups);
        return "user/export-groups";
    }

    @RequestMapping(value = "export-groups", method = RequestMethod.POST)
    public void exportGroupsDo(@RequestParam String[] selectedGroupUids, HttpServletResponse response) throws IOException {

        User user = getUserProfile();
        user = userManagementService.load(user.getUid()); // take fresh copy in order to have groups added since login
        List<Group> userGroups = user.getGroups().stream().filter(Group::isActive).sorted(Comparator.comparing(Group::getGroupName)).collect(Collectors.toList());
        List<String> userGroupUids = userGroups.stream().map(Group::getUid).collect(Collectors.toList());

        XSSFWorkbook xls = memberDataExportBroker.exportMultipleGroupMembers(userGroupUids, Arrays.asList(selectedGroupUids));

        String fileName = "multiple_group_members.xlsx";
        response.setContentType(MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        xls.write(response.getOutputStream());
        response.flushBuffer();
    }

    @RequestMapping(value = "social-media", method = RequestMethod.GET)
    public String linkSocialMedia(Model model) {
        model.addAttribute("name", "NAAM");
        long startTime = System.currentTimeMillis();
        ManagedPagesResponse response = socialMediaBroker.getManagedFacebookPages(getUserProfile().getUid());
        model.addAttribute("fbConnected", response.isUserConnectionValid());
        model.addAttribute("fbPages", response.getManagedPages());
        log.info("time for first call: {} msecs", System.currentTimeMillis() - startTime);
        ManagedPage twitterAccount = socialMediaBroker.isTwitterAccountConnected(getUserProfile().getUid());
        model.addAttribute("twitterConnected", twitterAccount != null);
        model.addAttribute("twitterAccountName", twitterAccount != null ? twitterAccount.getDisplayName() : "");
        log.info("time for both calls: {} msecs", System.currentTimeMillis() - startTime);
        return "user/social-media";
    }

}
