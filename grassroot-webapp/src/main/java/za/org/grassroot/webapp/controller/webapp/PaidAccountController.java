package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.AccountManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by luke on 2016/01/13.
 */
@Controller
@RequestMapping("/paid_account")
@SessionAttributes("user")
public class PaidAccountController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(PaidAccountController.class);
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-M-yyyy");

    @Autowired
    private AccountManagementService accountManagementService;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private EventManagementService eventManagementService;

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping("/index")
    public String paidAccountIndex(Model model, HttpServletRequest request) {
        User user = getUserProfile(); // todo: make sure this is coming from the session, not the DB
        if (request.isUserInRole("ROLE_SYSTEM_ADMIN")) {
            model.addAttribute("accounts", accountManagementService.loadAllAccounts());
            return "paid_account/index";
        } else {
            Account account = accountManagementService.findAccountByAdministrator(user);
            log.info("Not system admin, but found this account ... " + account);
            return viewPaidAccount(model, account.getId(), request);
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/view")
    public String viewPaidAccount(Model model, @RequestParam("accountId") Long accountId, HttpServletRequest request) {
        Account account = accountManagementService.loadAccount(accountId);
        log.info("We are checking against this account ..." + account);
        /*if (!sessionUserHasAccountPermission(account, request))
            throw new AccessDeniedException("Not an administrator for this account");*/
        model.addAttribute("account", account);
        model.addAttribute("paidGroups", accountManagementService.getGroupsPaidForByAccount(account));
        return "paid_account/view";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/view")
    public String viewPaidAccountLogs(Model model, @RequestParam Long accountId, @RequestParam Long paidGroupId,
                                      @RequestParam(value = "monthToView", required = false) String monthToView, HttpServletRequest request) {

        Account account = accountManagementService.loadAccount(accountId);
        if (!sessionUserHasAccountPermission(account, request)) throw new AccessDeniedException("");

        final LocalDateTime beginDate;
        final LocalDateTime endDate;
        final String dateDescription;

        if (monthToView == null) {
            beginDate = LocalDateTime.now().minusMonths(1L); // todo: maybe make these coincide with prior month
            endDate = LocalDateTime.now();
            dateDescription = "Meetings and votes held since " + beginDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        } else {
            beginDate = LocalDate.parse("01-" + monthToView, dtf).atStartOfDay();
            endDate = beginDate.plusMonths(1L);
            dateDescription = "Meetings and votes held in " + beginDate.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            log.info(String.format("Getting records between %s and a month later",
                                   DateTimeUtil.getPreferredDateFormat().format(beginDate)));
        }

        final Long timeStart = System.currentTimeMillis();
        PaidGroup paidGroupRecord = accountManagementService.loadPaidGroupEntity(paidGroupId);
        Group underlyingGroup = paidGroupRecord.getGroup();

        addRecordsToModel("meetingsInPeriod", model, underlyingGroup, EventType.MEETING, beginDate, endDate);
        addRecordsToModel("votesInPeriod", model, underlyingGroup, EventType.VOTE, beginDate, endDate);

        final Long timeEnd = System.currentTimeMillis();

        log.info(String.format("Loaded a bunch of stuff for group ... %s, and it took %d msecs", underlyingGroup.getGroupName(), timeEnd - timeStart));

        model.addAttribute("account", account);
        model.addAttribute("group", underlyingGroup);
        model.addAttribute("paidGroupRecord", paidGroupRecord);
        model.addAttribute("dateDescription", dateDescription);
        model.addAttribute("beginDate", beginDate.toLocalDate());
        model.addAttribute("monthsToView", groupBroker.getMonthsGroupActive(underlyingGroup.getUid()));

        return "paid_account/view_logs";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/designate")
    public String designateGroupPaidFor(Model model, @RequestParam("accountId") Long accountId, HttpServletRequest request) {
        Account account = accountManagementService.loadAccount(accountId);
        if (!sessionUserHasAccountPermission(account, request)) throw new AccessDeniedException("");
        model.addAttribute("account", account);
        model.addAttribute("candidateGroups", getCandidateGroupsToDesignate(getUserProfile(), account));
        return "paid_account/designate";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/search")
    public String searchForGroup(Model model, @RequestParam Long accountId, @RequestParam String searchTerm,
                                 @RequestParam boolean tokenSearch, HttpServletRequest request) {
        Account account = accountManagementService.loadAccount(accountId);
        if (!sessionUserHasAccountPermission(account, request)) throw new AccessDeniedException("");
        model.addAttribute("account", account);
        model.addAttribute("tokenSearch", tokenSearch);

        if (tokenSearch) {
            model.addAttribute("groupFound", groupBroker.findGroupFromJoinCode(searchTerm));
        } else {
            model.addAttribute("groupCandidates", groupBroker.findPublicGroups(searchTerm, getUserProfile().getUid()));
        }

        return "paid_account/find_group";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/designate", method = RequestMethod.POST)
    public String doDesignation(Model model, @RequestParam Long accountId, @RequestParam String groupUid,
                                HttpServletRequest request) {
        // todo: some form of confirmation screen
        Account account = accountManagementService.loadAccount(accountId);
        if (!sessionUserHasAccountPermission(account, request)) throw new AccessDeniedException("");
        accountManagementService.addGroupToAccount(account, groupBroker.load(groupUid), getUserProfile());
        addMessage(model, MessageType.SUCCESS, "account.addgroup.success", request);
        return viewPaidAccount(model, accountId, request);
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/remove")
    public String confirmRemovePaidFor(Model model, @RequestParam Long accountId, @RequestParam Long paidGroupId,
                                       HttpServletRequest request) {
        Account account = accountManagementService.loadAccount(accountId);
        if (!sessionUserHasAccountPermission(account, request)) throw new AccessDeniedException("");
        PaidGroup paidGroupEntity = accountManagementService.loadPaidGroupEntity(paidGroupId);
        model.addAttribute("account", account);
        model.addAttribute("paidGroup", paidGroupEntity);
        return "paid_account/remove";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/remove", method = RequestMethod.POST)
    public String removePaidForDesignation(Model model, @RequestParam("accountId") Long accountId,
                                           @RequestParam("paidGroupId") Long paidGroupId, HttpServletRequest request) {
        Account account = accountManagementService.loadAccount(accountId);
        PaidGroup paidGroupRecord = accountManagementService.loadPaidGroupEntity(paidGroupId);
        if (!sessionUserHasAccountPermission(account, request)) throw new AccessDeniedException("");
        accountManagementService.removeGroupFromAccount(account, paidGroupRecord, getUserProfile());
        addMessage(model, MessageType.INFO, "account.remgroup.success", request);
        return viewPaidAccount(model, accountId, request);
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/admin/settings", method = RequestMethod.POST)
    public String changeAccountSettings(Model model, @RequestParam("accountId") Long accountId, HttpServletRequest request) {
        Account account = accountManagementService.loadAccount(accountId);
        if (!sessionUserHasAccountPermission(account, request)) throw new AccessDeniedException("");
        return "paid_account/settings";
    }

    /*
        quick private helper method to do role & permission check
        major todo: cut down the db calls -- the isInRole check takes 0ms, the getUserProfile... takes 19ms!!
        major todo: move this to a service and call it via @preauthorize
    */
    private boolean sessionUserHasAccountPermission(Account account, HttpServletRequest request) {
        log.info("This user is in system admin role ..." + request.isUserInRole("ROLE_SYSTEM_ADMIN"));
        log.info("This user's account matches ..." + (getUserProfile().getAccountAdministered() == account));
        Long startTime = System.currentTimeMillis();
        boolean response = request.isUserInRole("ROLE_SYSTEM_ADMIN") || getUserProfile().getAccountAdministered() == account;
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Role check took %d ms", endTime - startTime));
        return response;
    }

    private List<Group> getCandidateGroupsToDesignate(User user, Account account) {
        Long startTime = System.currentTimeMillis();
        Set<Group> groupsPartOf = permissionBroker.getActiveGroupsWithPermission(user, null);
        List<PaidGroup> alreadyDesignated = accountManagementService.getGroupsPaidForByAccount(account);
        for (PaidGroup paidGroup : alreadyDesignated)
            if (groupsPartOf.contains(paidGroup.getGroup())) groupsPartOf.remove(paidGroup.getGroup());
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Fetched list of groups that can be designated, took %d ms", endTime - startTime));
        return new ArrayList<>(groupsPartOf);
    }

    private Model addRecordsToModel(String attr, Model model, Group group, EventType type, LocalDateTime start, LocalDateTime end) {

        // todo: this is _very_ expensive, ~150-200 ms, we will need to optimize it
        List<Event> eventsInPeriod = eventManagementService.getEventsForGroupInTimePeriod(group, type, start, end);

        model.addAttribute(attr, eventsInPeriod);
        return model;
    }

}
