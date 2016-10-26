package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/01/13.
 */
@Controller
@RequestMapping("/accounts")
@SessionAttributes("user")
public class AccountsController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AccountsController.class);
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-M-yyyy");

    private AccountBroker accountBroker;
    private AccountGroupBroker accountGroupBroker;
    private GroupQueryBroker groupQueryBroker;
    private EventBroker eventBroker;

    @Autowired
    public AccountsController(AccountBroker accountBroker, AccountGroupBroker accountGroupBroker, GroupQueryBroker groupQueryBroker, EventBroker eventBroker) {
        this.accountBroker = accountBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.groupQueryBroker = groupQueryBroker;
        this.eventBroker = eventBroker;
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping("/index")
    public String paidAccountIndex(Model model, HttpServletRequest request) {
        if (request.isUserInRole("ROLE_SYSTEM_ADMIN")) {
            model.addAttribute("accounts", accountBroker.loadAllAccounts());
            return "accounts/index";
        } else if (request.isUserInRole("ROLE_ACCOUNT_ADMIN")) {
            User user = userManagementService.load(getUserProfile().getUid());
            Account account = user.getAccountAdministered();
            return viewPaidAccount(model, account.getUid());
        } else {
            throw new AccessDeniedException("Error! Only system admin or account admin can access this page");
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/view")
    public String viewPaidAccount(Model model, @RequestParam("accountUid") String accountUid) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);

        List<PaidGroup> currentlyPaidGroups = account.getPaidGroups().stream()
                .filter(PaidGroup::isActive)
                .collect(Collectors.toList());

        model.addAttribute("account", account);
        model.addAttribute("paidGroups", currentlyPaidGroups);
        return "accounts/view";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/settings/view", method = RequestMethod.GET)
    public String changeAccountSettingsView(Model model, @RequestParam("accountUid") String accountUid) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        model.addAttribute("account", account);
        return "accounts/settings";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/settings/change", method = RequestMethod.POST)
    public String changeAccountSettingsDo(Model model, @RequestAttribute String accountUid, @RequestAttribute String billingEmail,
                                          @RequestAttribute AccountType accountType, HttpServletRequest request) {
        try {
            Account account = accountBroker.loadAccount(accountUid);
            if (!billingEmail.equals(account.getBillingUser().getEmailAddress())) {
                accountBroker.updateBillingEmail(getUserProfile().getUid(), accountUid, billingEmail);
            }

            if (!accountType.equals(account.getType())) {
                accountBroker.changeAccountType(getUserProfile().getUid(), accountUid, accountType);
            }

            addMessage(model, MessageType.SUCCESS, "account.settings.changed.success", request);
            model.addAttribute("account", account);
        } catch (AccessDeniedException e) {
            addMessage(model, MessageType.ERROR, "account.settings.changed.error", request);
        }
        return "accounts/settings";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/view")
    public String viewPaidAccountLogs(Model model, @RequestParam String accountUid, @RequestParam String paidGroupUid,
                                      @RequestParam(value = "monthToView", required = false) String monthToView, HttpServletRequest request) {

        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);

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
        PaidGroup paidGroupRecord = accountGroupBroker.loadPaidGroup(paidGroupUid);
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
        model.addAttribute("monthsToView", groupQueryBroker.getMonthsGroupActive(underlyingGroup.getUid()));

        return "accounts/view_logs";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/designate")
    public String designateGroupPaidFor(Model model, @RequestParam("accountUid") String accountUid, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        model.addAttribute("account", account);
        model.addAttribute("candidateGroups", getCandidateGroupsToDesignate(getUserProfile()));
        return "accounts/designate";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/search")
    public String searchForGroup(Model model, @RequestParam String accountUid, @RequestParam String searchTerm,
                                 @RequestParam boolean tokenSearch) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);

        model.addAttribute("account", account);
        model.addAttribute("tokenSearch", tokenSearch);

        if (tokenSearch) {
            Optional<Group> searchResult = groupQueryBroker.findGroupFromJoinCode(searchTerm);
            if (searchResult.isPresent()) {
                model.addAttribute("groupFound", groupQueryBroker.findGroupFromJoinCode(searchTerm));
            }
        } else {
            model.addAttribute("groupCandidates", groupQueryBroker.findPublicGroups(getUserProfile().getUid(), searchTerm, null, false));
        }

        return "accounts/find_group";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/designate", method = RequestMethod.POST)
    public String doDesignation(Model model, @RequestParam String accountUid, @RequestParam String groupUid,
                                HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        accountGroupBroker.addGroupToAccount(accountUid, groupUid, getUserProfile().getUid());
        addMessage(model, MessageType.SUCCESS, "account.addgroup.success", request);
        return viewPaidAccount(model, accountUid);
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/remove", method = RequestMethod.POST)
    public String removePaidForDesignation(Model model, @RequestParam String accountUid, @RequestParam String paidGroupUid,
                                           @RequestParam(value = "confirm_field") String confirmed, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        if (confirmed.equalsIgnoreCase("confirmed")) {
            accountGroupBroker.removeGroupFromAccount(accountUid, paidGroupUid, getUserProfile().getUid());
            addMessage(model, MessageType.INFO, "account.remgroup.success", request);
        } else {
            addMessage(model, MessageType.ERROR, "account.remgroup.failed", request);
        }
        return viewPaidAccount(model, accountUid);
    }

    /* quick helper method to do role & permission check */
    private void validateUserIsAdministrator(Account account) {
        User user = userManagementService.load(getUserProfile().getUid());
        if (user.getAccountAdministered() == null || !user.getAccountAdministered().equals(account)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }
    }

    private List<Group> getCandidateGroupsToDesignate(User user) {
        List<Group> groupsPartOf = permissionBroker.getActiveGroupsSorted(user, null);
        return groupsPartOf.stream()
                .filter(g -> !g.isPaidFor())
                .collect(Collectors.toList());
    }

    private Model addRecordsToModel(String attr, Model model, Group group, EventType type, LocalDateTime start, LocalDateTime end) {
        List<Event> eventsInPeriod = eventBroker.retrieveGroupEvents(group, type, DateTimeUtil.convertToSystemTime(start, DateTimeUtil.getSAST()),
                DateTimeUtil.convertToSystemTime(end, DateTimeUtil.getSAST()));
        model.addAttribute(attr, eventsInPeriod);
        return model;
    }

}
