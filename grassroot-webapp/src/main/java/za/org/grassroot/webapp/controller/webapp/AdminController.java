package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.AdminService;
import za.org.grassroot.services.AnalyticalService;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Random;

/**
 * Created by luke on 2015/10/08.
 * Class for G/R's internal administration functions
 */
@Controller
public class AdminController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Value("${grassroot.msisdn.length:11}")
    private int phoneNumberLength;

    private static final Random RANDOM = new SecureRandom();

    private final AdminService adminService;
    private final AnalyticalService analyticalService;

    @Autowired
    public AdminController(AdminService adminService, AnalyticalService analyticalService) {
        this.adminService = adminService;
        this.analyticalService = analyticalService;
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/home")
    public String adminIndex(Model model, @ModelAttribute("currentUser") UserDetails userDetails) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime week = now.minusWeeks(1L);
        LocalDateTime month = now.minusMonths(1L);

        // todo: put these into maps

        model.addAttribute("countLastWeek", analyticalService.countUsersCreatedInInterval(week, now));
        model.addAttribute("countLastMonth", analyticalService.countUsersCreatedInInterval(month, now));
        model.addAttribute("userCount", analyticalService.countAllUsers());

        model.addAttribute("countUssdLastWeek", analyticalService.countUsersCreatedAndInitiatedInPeriod(week, now));
        model.addAttribute("countUssdLastMonth", analyticalService.countUsersCreatedAndInitiatedInPeriod(month, now));
        model.addAttribute("countUssdTotal", analyticalService.countUsersThatHaveInitiatedSession());

        model.addAttribute("countWebLastWeek", analyticalService.countUsersCreatedWithWebProfileInPeriod(week, now));
        model.addAttribute("countWebLastMonth", analyticalService.countUsersCreatedWithWebProfileInPeriod(month, now));
        model.addAttribute("countWebTotal", analyticalService.countUsersThatHaveWebProfile());


        model.addAttribute("countAndroidLastWeek", analyticalService.countUsersCreatedWithAndroidProfileInPeriod(week, now));
        model.addAttribute("countAndroidLastMonth", analyticalService.countUsersCreatedWithAndroidProfileInPeriod(month, now));
        model.addAttribute("countAndroidTotal", analyticalService.countUsersThatHaveAndroidProfile());

        model.addAttribute("groupsLastWeek", analyticalService.countGroupsCreatedInInterval(week, now));
        model.addAttribute("groupsLastMonth", analyticalService.countGroupsCreatedInInterval(month, now));
        model.addAttribute("groupsTotal", analyticalService.countActiveGroups());

        model.addAttribute("meetingsLastWeek", analyticalService.countEventsCreatedInInterval(week, now, EventType.MEETING));
        model.addAttribute("meetingsLastMonth", analyticalService.countEventsCreatedInInterval(month, now, EventType.MEETING));
        model.addAttribute("meetingsTotal", analyticalService.countAllEvents(EventType.MEETING));

        model.addAttribute("votesLastWeek", analyticalService.countEventsCreatedInInterval(week, now, EventType.VOTE));
        model.addAttribute("votesLastMonth", analyticalService.countEventsCreatedInInterval(month, now, EventType.VOTE));
        model.addAttribute("votesTotal", analyticalService.countAllEvents(EventType.VOTE));

        model.addAttribute("todosLastWeek", analyticalService.countTodosRecordedInInterval(week, now));
        model.addAttribute("todosLastMonth", analyticalService.countTodosRecordedInInterval(month, now));
        model.addAttribute("todosTotal", analyticalService.countAllTodos());

        model.addAttribute("safetyLastWeek", analyticalService.countSafetyEventsInInterval(week, now));
        model.addAttribute("safetyLastMonth", analyticalService.countSafetyEventsInInterval(month, now));
        model.addAttribute("safetyTotal", analyticalService.countSafetyEventsInInterval(null, null));

        model.addAttribute("livewireLastWeek", analyticalService.countLiveWireAlertsInInterval(
                week.toInstant(ZoneOffset.UTC), Instant.now()));
        model.addAttribute("livewireLastMonth", analyticalService.countLiveWireAlertsInInterval(
                month.toInstant(ZoneOffset.UTC), Instant.now()));
        model.addAttribute("livewireTotal", analyticalService.countLiveWireAlertsInInterval(
                month.toInstant(ZoneOffset.UTC), Instant.now()));

        model.addAttribute("notificationsLastWeek", analyticalService.countNotificationsInInterval(
                week.toInstant(ZoneOffset.UTC), Instant.now()));
        model.addAttribute("notificationsLastMonth", analyticalService.countNotificationsInInterval(
                month.toInstant(ZoneOffset.UTC), Instant.now()));
        model.addAttribute("notificationsTotal", analyticalService.countNotificationsInInterval(
                DateTimeUtil.getEarliestInstant(), Instant.now()));

        return "admin/home";

    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/alpha", method = RequestMethod.GET)
    public String viewAlphaMembers(Model model) {
        model.addAttribute("users", adminService.getUsersWithStdRole(getUserProfile().getUid(),
                BaseRoles.ROLE_ALPHA_TESTER));
        return "admin/users/new_interface_users";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/alpha/user/add", method = RequestMethod.GET)
    public String assignAlphaToUser(RedirectAttributes attributes, HttpServletRequest request,
                                    @RequestParam String phoneNumberOrEmail) {
        try {
            User user = userManagementService.findByNumberOrEmail(phoneNumberOrEmail, phoneNumberOrEmail);
            adminService.addSystemRole(getUserProfile().getUid(), user.getUid(), BaseRoles.ROLE_ALPHA_TESTER);
            addMessage(attributes, MessageType.SUCCESS, "admin.alpha.add.done", request);
        } catch (NoSuchUserException e) {
            log.error("error! can't find that user", e);
            addMessage(attributes, MessageType.ERROR, "admin.alpha.error.nouser", request);
        }
        return "redirect:/admin/alpha";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/alpha/user/create", method = RequestMethod.POST)
    public String addAlphaMember(RedirectAttributes attributes, HttpServletRequest request,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String phone) {
        try {
            log.info("okay, creating a new alpha tester, with name: {}, email: {}, phone: {}",
                    name, email, phone);
            adminService.createUserWithSystemRole(getUserProfile().getUid(), name,
                    phone, email, BaseRoles.ROLE_ALPHA_TESTER);
            addMessage(attributes, MessageType.SUCCESS, "admin.alpha.add.done", request);
        } catch (IllegalArgumentException e) {
            addMessage(attributes, MessageType.ERROR, "admin.alpha.error.fields", request);
        }
        return "redirect:/admin/alpha";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/alpha/user/remove", method = RequestMethod.POST)
    public String removeAlphaMember(RedirectAttributes attributes, HttpServletRequest request,
                                    @RequestParam String removeUserUid) {
        adminService.removeStdRole(getUserProfile().getUid(), removeUserUid, BaseRoles.ROLE_ALPHA_TESTER);
        addMessage(attributes, MessageType.SUCCESS, "admin.alpha.remove.done", request);
        return "redirect:/admin/alpha";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/user/edit", method = RequestMethod.GET)
    public String editAlphaMember(RedirectAttributes attributes, HttpServletRequest request,
                                  @RequestParam String userUid,
                                  @RequestParam(required = false) String name,
                                  @RequestParam(required = false) String email,
                                  @RequestParam(required = false) String phone) {
        if (!StringUtils.isEmpty(name)) {
            userManagementService.updateDisplayName(getUserProfile().getUid(), userUid, name);
        }
        if (!StringUtils.isEmpty(email)) {
            userManagementService.updateEmailAddress(getUserProfile().getUid(), userUid, email);
        }
        if (!StringUtils.isEmpty(phone)) {
            userManagementService.updatePhoneNumber(getUserProfile().getUid(), userUid, phone);
        }
        addMessage(attributes, MessageType.SUCCESS, "admin.alpha.edit.done", request);
        return "redirect:/admin/alpha";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/user/pwd/reset", method = RequestMethod.POST)
    public String updateUserPassword(RedirectAttributes attributes, HttpServletRequest request,
                                     @RequestParam String changeUserUid) {
        String newPwd = generateRandomPwd();
        adminService.updateUserPassword(getUserProfile().getUid(), changeUserUid, newPwd);
        addMessage(attributes, MessageType.SUCCESS, "admin.user.pwd.reset", new String[] { newPwd }, request);
        return "redirect:/admin/alpha"; // or wherever came from, in future
    }

    private String generateRandomPwd() {
        String letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789+@";
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 8; i++){
            int index = (int)(RANDOM.nextDouble()*letters.length());
            password.append(letters.substring(index, index + 1));
        }

        return password.toString();
    }

}
