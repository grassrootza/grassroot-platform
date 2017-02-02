package za.org.grassroot.webapp.controller.webapp.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.integration.PdfGeneratingService;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AdminRemovalException;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.exception.UserAlreadyAdminException;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.PrivateGroupWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/01/13.
 */
@Controller
@RequestMapping("/account")
@SessionAttributes("user")
public class AccountController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private AccountBroker accountBroker;
    private AccountBillingBroker accountBillingBroker;
    private AccountGroupBroker accountGroupBroker;
    private PdfGeneratingService pdfGeneratingService;

    @Autowired
    public AccountController(AccountBroker accountBroker, AccountGroupBroker accountGroupBroker, AccountBillingBroker accountBillingBroker,
                             PdfGeneratingService pdfGeneratingService) {
        this.accountBroker = accountBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.accountBillingBroker = accountBillingBroker;
        this.pdfGeneratingService = pdfGeneratingService;
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = { "", "/" })
    public String paidAccountIndex(Model model, HttpServletRequest request) {
        User user = userManagementService.load(getUserProfile().getUid());
        Account account = user.getAccountAdministered();
        if (account == null && request.isUserInRole("ROLE_SYSTEM_ADMIN")) {
            return "redirect:/admin/accounts/home";
        } else if (account != null) {
            return viewPaidAccount(model, account.getUid());
        } else {
            return "redirect:/account/signup";
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/view", method = RequestMethod.GET)
    public String viewPaidAccount(Model model, @RequestParam String accountUid) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        model.addAttribute("account", account);
        if (account.isEnabled()) {
            List<PaidGroup> currentlyPaidGroups = account.getPaidGroups().stream()
                    .filter(PaidGroup::isActive)
                    .collect(Collectors.toList());
            model.addAttribute("paidGroups", currentlyPaidGroups);
            model.addAttribute("groupsLeft", accountGroupBroker.numberGroupsLeft(accountUid));
            model.addAttribute("billingRecords", accountBillingBroker.findRecordsWithStatementDates(accountUid));
            model.addAttribute("canAddAllGroups", currentlyPaidGroups.isEmpty()
                    && accountGroupBroker.canAddMultipleGroupsToOwnAccount(getUserProfile().getUid()));
            model.addAttribute("administrators", account.getAdministrators());
            return "account/view";
        } else {
            model.addAttribute("needEmailAddress", StringUtils.isEmpty(getUserProfile().getEmailAddress()));
            return "account/disabled";
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
                        .candidateGroupsForAccount(getUserProfile().getUid(), accountUid, searchTerm)
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
    public String addAllGroupsToAccount(RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountBroker.loadUsersAccount(getUserProfile().getUid(), false);
        int groupsAdded = accountGroupBroker.addUserCreatedGroupsToAccount(account.getUid(), getUserProfile().getUid());
        if (groupsAdded > 0) {
            addMessage(attributes, MessageType.SUCCESS, "account.groups.many.added", new Object[] { groupsAdded }, request);
        } else {
            addMessage(attributes, MessageType.ERROR, "account.groups.many.error", request);
        }
        attributes.addAttribute("accountUid", account.getUid());
        return "redirect:/account/view";
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
            accountBroker.removeAdministrator(getUserProfile().getUid(), accountUid, adminUid);
            addMessage(attributes, MessageType.SUCCESS, "account.admin.remove.success", request);
        } catch (AdminRemovalException e) {
            addMessage(attributes, MessageType.ERROR, e.getMessage(), request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "account.admin.remove.access", request);
        }

        return "redirect:/account/view";
    }

    /* quick helper method to do role & permission check */
    private void validateUserIsAdministrator(Account account) {
        User user = userManagementService.load(getUserProfile().getUid());
        if (user.getAccountAdministered() == null || !user.getAccountAdministered().equals(account)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }
    }

}
