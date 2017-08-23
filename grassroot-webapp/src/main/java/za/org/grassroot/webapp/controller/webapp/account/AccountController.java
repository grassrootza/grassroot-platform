package za.org.grassroot.webapp.controller.webapp.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.domain.account.PaidGroup;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.integration.PdfGeneratingService;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.account.AccountSponsorshipBroker;
import za.org.grassroot.services.exception.AdminRemovalException;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.exception.UserAlreadyAdminException;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.PrivateGroupWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/01/13.
 */
@Controller
@RequestMapping("/account")
@SessionAttributes("user")
public class AccountController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountBroker accountBroker;
    private final AccountBillingBroker accountBillingBroker;
    private final AccountGroupBroker accountGroupBroker;
    private final AccountSponsorshipBroker sponsorshipBroker;
    private final PdfGeneratingService pdfGeneratingService;

    @Autowired
    public AccountController(AccountBroker accountBroker, AccountGroupBroker accountGroupBroker, AccountBillingBroker accountBillingBroker,
                             AccountSponsorshipBroker sponsorshipBroker, PdfGeneratingService pdfGeneratingService) {
        this.accountBroker = accountBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.accountBillingBroker = accountBillingBroker;
        this.sponsorshipBroker = sponsorshipBroker;
        this.pdfGeneratingService = pdfGeneratingService;
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = { "", "/" }, method = RequestMethod.GET)
    public String paidAccountIndex(Model model, HttpServletRequest request) {
        User user = userManagementService.load(getUserProfile().getUid());
        Account account = user.getPrimaryAccount();
        if (account == null && !user.getAccountsAdministered().isEmpty()) {
            model.addAttribute("accounts", user.getAccountsAdministered().stream()
                    .sorted(Comparator.comparing(Account::getName)).collect(Collectors.toList()));
            return "account/list";
        } else if (account != null) {
            return viewPaidAccount(model, account.getUid());
        } else {
            return "redirect:/account/signup";
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "setprimary", method = RequestMethod.GET)
    public String setAccountPrimary(RedirectAttributes attributes, @RequestParam String accountUid,
                                    HttpServletRequest request) {
        accountBroker.setAccountPrimary(getUserProfile().getUid(), accountUid);
        addMessage(attributes, MessageType.SUCCESS, "account.primary.done", request);
        attributes.addAttribute("accountUid", accountUid);
        return "redirect:/account/view";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/view", method = RequestMethod.GET)
    public String viewPaidAccount(Model model, @RequestParam(required = false) String accountUid) {
        User user = userManagementService.load(getUserProfile().getUid()); // make sure loads latest set of accounts
        Account account = StringUtils.isEmpty(accountUid) ?
                accountBroker.loadPrimaryAccountForUser(user.getUid(), true) :
                accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        model.addAttribute("account", account);
        model.addAttribute("user", user);

        if (!account.isEnabled()) {
            return viewDisabledAccount(model, account.getUid());
        } else if (AccountPaymentType.FREE_TRIAL.equals(account.getDefaultPaymentType())) {
            return viewTrialAccount(model, accountUid);
        } else {
            checkForMultipleAccounts(user, account, model);
            List<PaidGroup> currentlyPaidGroups = account.getPaidGroups().stream()
                    .filter(PaidGroup::isActive)
                    .collect(Collectors.toList());

            model.addAttribute("paidGroups", currentlyPaidGroups);
            model.addAttribute("groupsLeft", accountGroupBroker.numberGroupsLeft(account.getUid()));
            model.addAttribute("billingRecords", accountBillingBroker.findRecordsWithStatementDates(account.getUid()));
            model.addAttribute("canAddAllGroups", currentlyPaidGroups.isEmpty()
                    && accountGroupBroker.canAddAllCreatedGroupsToAccount(getUserProfile().getUid(), account.getUid()));
            model.addAttribute("administrators", account.getAdministrators());
            return "account/view";
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/disabled", method = RequestMethod.GET)
    public String viewDisabledAccount(Model model, @RequestParam String accountUid) {
        Account account = accountBroker.loadAccount(accountUid);
        User user = userManagementService.load(getUserProfile().getUid()); // make sure loads latest set of roles etc

        validateUserIsAdministrator(account);
        model.addAttribute("account", account);
        model.addAttribute("user", user);

        model.addAttribute("needEmailAddress", StringUtils.isEmpty(getUserProfile().getEmailAddress()));
        model.addAttribute("hasRequestedSponsorship", sponsorshipBroker.accountHasOpenRequests(account.getUid()));
        model.addAttribute("openSponsorshipRequests", sponsorshipBroker.openRequestsForAccount(account.getUid(),
                new Sort(Sort.Direction.DESC, "creationTime")));
        return "account/disabled";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/trial", method = RequestMethod.GET)
    public String viewTrialAccount(Model model, @RequestParam String accountUid) {
        Account account = accountBroker.loadAccount(accountUid);
        User user = userManagementService.load(getUserProfile().getUid());
        model.addAttribute("account", accountBroker.loadAccount(accountUid));
        checkForMultipleAccounts(user, account, model);
        boolean canAddAnyGroups = accountGroupBroker.canAddGroupToAccount(user.getUid(), accountUid);
        model.addAttribute("canAddAnyGroups", canAddAnyGroups);
        if (canAddAnyGroups) {
            model.addAttribute("canAddAllGroups", accountGroupBroker.canAddAllCreatedGroupsToAccount(user.getUid(), accountUid));
            model.addAttribute("groupsCanAdd", accountGroupBroker.fetchUserCreatedGroupsUnpaidFor(user.getUid(), new Sort(Sort.Direction.ASC, "groupName")));
        } else {
            model.addAttribute("canAddAllGroups", false);
            model.addAttribute("groupsCanAdd", new ArrayList<Group>()); // just to avoid Thymeleaf null pointer etc
        }
        return "account/trial";
    }

    private void checkForMultipleAccounts(User user, Account account, Model model) {
        if (user.hasMultipleAccounts()) {
            model.addAttribute("isPrimary", account.equals(user.getPrimaryAccount()));
            model.addAttribute("otherAccounts", user.getAccountsAdministered().stream()
                    .filter(a -> !account.equals(a) && a.isVisible())
                    .sorted(Comparator.comparing(Account::getName))
                    .collect(Collectors.toList()));
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/add", method = RequestMethod.GET)
    public String addGroupAsPaidFor(@RequestParam String accountUid, @RequestParam String groupUid,
                                    @RequestParam(required = false) String searchTerm,
                                    Model model, RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        String returnTemplate;

        // not great using try/catch in this way, but alternative is a messy set of checks to DB; in future use specific exception to customize message
        try {
            accountGroupBroker.addGroupToAccount(accountUid, groupUid, getUserProfile().getUid());
            attributes.addAttribute("accountUid", accountUid);
            addMessage(attributes, MessageType.SUCCESS, "account.addgroup.success", request);
            returnTemplate = "redirect:/account/view";
        } catch(GroupAlreadyPaidForException e) {
            addMessage(attributes, MessageType.INFO, "account.addgroup.paidfor", request);
            attributes.addAttribute("accountUid", accountUid);
            returnTemplate = "redirect:/account/view";
        } catch (Exception e) {
            if (!StringUtils.isEmpty(searchTerm)) {
                List<PrivateGroupWrapper> candidateGroups = accountGroupBroker
                        .searchGroupsForAddingToAccount(getUserProfile().getUid(), accountUid, searchTerm)
                        .stream().map(PrivateGroupWrapper::new).collect(Collectors.toList());
                if (candidateGroups.isEmpty()) {
                    attributes.addAttribute("accountUid", accountUid);
                    addMessage(attributes, MessageType.ERROR, "account.addgroup.notfound", request);
                    returnTemplate = "redirect:/account/view";
                } else {
                    model.addAttribute("account", account);
                    model.addAttribute("searchType", "group");
                    model.addAttribute("candidateGroups", candidateGroups);
                    returnTemplate = "account/results";
                }
            } else {
                addMessage(attributes, MessageType.ERROR, "account.addgroup.error", request);
                attributes.addAttribute("accountUid", accountUid);
                returnTemplate = "redirect:/account/view";
            }
        }
        return returnTemplate;
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/add/all", method = RequestMethod.GET)
    public String addAllGroupsToAccount(@RequestParam String accountUid, @RequestParam boolean trial,
                                        RedirectAttributes attributes, HttpServletRequest request) {
        // todo : specify account in case not primary
        Account account = accountBroker.loadPrimaryAccountForUser(getUserProfile().getUid(), false);
        int groupsAdded = accountGroupBroker.addUserCreatedGroupsToAccount(account.getUid(), getUserProfile().getUid());
        if (groupsAdded > 0) {
            addMessage(attributes, MessageType.SUCCESS, "account.groups.many.added", new Object[] { groupsAdded }, request);
        } else {
            addMessage(attributes, MessageType.ERROR, "account.groups.many.error", request);
        }
        attributes.addAttribute("accountUid", account.getUid());
        return "redirect:/account/" + (trial ? "trial" : "view");
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/add/multiple", method = RequestMethod.POST)
    public String addMultipleGroupsToAccount(@RequestParam String accountUid, @RequestParam boolean trial,
                                             @RequestParam String[] addGroupUids, RedirectAttributes attributes, HttpServletRequest request) {
        log.info("group UIDs: {}", Arrays.toString(addGroupUids));
        accountGroupBroker.addGroupsToAccount(accountUid, new HashSet<>(Arrays.asList(addGroupUids)), getUserProfile().getUid());

        attributes.addAttribute("accountUid", accountUid);
        addMessage(attributes, MessageType.SUCCESS, "account.groups.many.added", new Integer[] { addGroupUids.length }, request);

        return "redirect:/account/" + (trial ? "trial" : "view");
    }


    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/remove", method = RequestMethod.POST)
    public String removePaidForDesignation(Model model, @RequestParam String accountUid, @RequestParam String paidGroupUid,
                                           @RequestParam(value = "confirm_field") String confirmed, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        if (confirmed.equalsIgnoreCase("confirmed")) {
            accountGroupBroker.removeGroupsFromAccount(accountUid, Collections.singleton(paidGroupUid), getUserProfile().getUid());
            addMessage(model, MessageType.INFO, "account.remgroup.success", request);
        } else {
            addMessage(model, MessageType.ERROR, "account.remgroup.failed", request);
        }
        return viewPaidAccount(model, accountUid);
    }

    // todo : a bit more descriptiveness in file name, and in future maybe add "paid" stamp
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/statement", method = RequestMethod.GET, produces = "application/pdf")
    @ResponseBody public FileSystemResource viewAccountBillingStatement(@RequestParam String statementUid, HttpServletResponse response) {
        response.setHeader("Content-Disposition", "attachment; filename=statement.pdf");
        List<AccountBillingRecord> associatedRecords = accountBillingBroker.findRecordsInSameStatementCycle(statementUid);
        return new FileSystemResource(pdfGeneratingService.generateInvoice(associatedRecords.stream()
                .map(AccountBillingRecord::getUid).collect(Collectors.toList())));
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/admin/add", method = RequestMethod.GET)
    public String addAccountAdmin(@RequestParam String accountUid, @RequestParam(required = false) String newAdminMsisdn,
                                  @RequestParam(required = false) String addAdminName,
                                  Model model, RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        String returnTemplate;
        attributes.addAttribute("accountUid", accountUid);

        try {
            User newAdmin = userManagementService.findByInputNumber(newAdminMsisdn == null ? "" : newAdminMsisdn);
            accountBroker.addAdministrator(getUserProfile().getUid(), accountUid, newAdmin.getUid());
            addMessage(attributes, MessageType.SUCCESS, "account.admin.added", request);
            returnTemplate = "redirect:/account/view";
        } catch (UserAlreadyAdminException e) {
            addMessage(attributes, MessageType.ERROR, "account.admin.already", request);
            returnTemplate = "redirect:/account/view";
        } catch (NoSuchUserException|InvalidPhoneNumberException e) {
            if (!StringUtils.isEmpty(addAdminName)) {
                User thisUser = userManagementService.load(getUserProfile().getUid());
                List<String[]> candidateAdmins = userManagementService.findOthersInGraph(thisUser, addAdminName);
                if (candidateAdmins.isEmpty()) {
                    attributes.addAttribute("accountUid", accountUid);
                    addMessage(attributes, MessageType.ERROR, "account.addadmin.notfound", request);
                    returnTemplate = "redirect:/account/view";
                } else {
                    model.addAttribute("account", account);
                    model.addAttribute("searchType", "user");
                    model.addAttribute("candidateAdmins", candidateAdmins);
                    returnTemplate = "account/results";
                }
            } else {
                addMessage(attributes, MessageType.ERROR, "account.addadmin.error", request);
                attributes.addAttribute("accountUid", accountUid);
                returnTemplate = "redirect:/account/view";
            }
        }
        return returnTemplate;
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/admin/remove", method = RequestMethod.POST)
    public String removeAccountAdmin(@RequestParam String accountUid, @RequestParam String adminUid,
                                     RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        attributes.addAttribute("accountUid", account.getUid());

        try {
            accountBroker.removeAdministrator(getUserProfile().getUid(), accountUid, adminUid, true);
            addMessage(attributes, MessageType.SUCCESS, "account.admin.remove.success", request);
        } catch (AdminRemovalException e) {
            addMessage(attributes, MessageType.ERROR, e.getMessage(), request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "account.admin.remove.access", request);
        }

        return "redirect:/account/view";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "close", method = RequestMethod.POST)
    public String closeAccount(@RequestParam String accountUid, @RequestParam String confirmText,
                               RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        User loadedUser = userManagementService.load(getUserProfile().getUid());

        if (!account.getAdministrators().contains(loadedUser)) {
            permissionBroker.validateSystemRole(loadedUser, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        if ("confirmed".equalsIgnoreCase(confirmText.trim())) {
            accountBillingBroker.generateClosingBill(getUserProfile().getUid(), accountUid);
            accountBroker.closeAccount(getUserProfile().getUid(), accountUid, false);
            addMessage(attributes, MessageType.INFO, "account.closed.done", request);
            refreshAuthorities();
            return "redirect:/home";
        } else {
            addMessage(attributes, MessageType.ERROR, "account.closed.error", request);
            attributes.addAttribute("accountUid", account.getUid());
            if (account.isEnabled()) {
                return "redirect:/account/view";
            } else {
                return "redirect:/account/disabled";
            }
        }
    }

    /* quick helper method to do role & permission check */
    private void validateUserIsAdministrator(Account account) {
        User user = userManagementService.load(getUserProfile().getUid());
        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }
    }

}
