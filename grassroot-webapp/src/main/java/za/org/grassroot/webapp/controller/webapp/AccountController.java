package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.integration.payments.PaymentMethod;
import za.org.grassroot.integration.payments.PaymentServiceBroker;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.text.DecimalFormat;
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

    private static long PAYMENT_VERIFICATION_AMT = 1000; // todo : make proportional to account size

    private AccountBroker accountBroker;
    private AccountBillingBroker accountBillingBroker;
    private AccountGroupBroker accountGroupBroker;
    private PaymentServiceBroker paymentServiceBroker;

    @Autowired
    public AccountController(AccountBroker accountBroker, AccountGroupBroker accountGroupBroker,
                             AccountBillingBroker accountBillingBroker, PaymentServiceBroker paymentServiceBroker) {
        this.accountBroker = accountBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.accountBillingBroker = accountBillingBroker;
        this.paymentServiceBroker = paymentServiceBroker;
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping("/index")
    public String paidAccountIndex(Model model, HttpServletRequest request) {
        if (request.isUserInRole("ROLE_SYSTEM_ADMIN")) {
            model.addAttribute("accounts", accountBroker.loadAllAccounts(true));
            return "account/index";
        } else if (request.isUserInRole("ROLE_ACCOUNT_ADMIN")) {
            User user = userManagementService.load(getUserProfile().getUid());
            Account account = user.getAccountAdministered();
            return viewPaidAccount(model, account.getUid());
        } else {
            throw new AccessDeniedException("Error! Only system admin or account admin can access this page");
        }
    }

    // todo : refine / fix UI on add group search (e.g., subsequent page if not found via autocomplete)
    // todo : take out select box on account type (since using extra page for task)
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/view", method = RequestMethod.GET)
    public String viewPaidAccount(Model model, @RequestParam String accountUid) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);

        List<PaidGroup> currentlyPaidGroups = account.getPaidGroups().stream()
                .filter(PaidGroup::isActive)
                .collect(Collectors.toList());

        model.addAttribute("account", account);
        model.addAttribute("paidGroups", currentlyPaidGroups);
        model.addAttribute("billingRecords", accountBillingBroker.fetchBillingRecords(accountUid,
                new Sort(Sort.Direction.DESC, "statementDateTime")));
        model.addAttribute("canAddAllGroups", currentlyPaidGroups.isEmpty()
                && accountGroupBroker.canAddMultipleGroupsToOwnAccount(getUserProfile().getUid()));
        model.addAttribute("administrators", account.getAdministrators());

        return "account/view";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/type", method = RequestMethod.GET)
    public String changeAccountTypeOptions(Model model, @RequestParam(required = false) String accountUid) {
        Account account = !StringUtils.isEmpty(accountUid) ? accountBroker.loadAccount(accountUid)
                : accountBroker.loadUsersAccount(getUserProfile().getUid());
        validateUserIsAdministrator(account);

        model.addAttribute("account", account);

        return "account/type";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/type/change", method = RequestMethod.GET)
    public String changeAccountTypeDo(@RequestParam String accountUid, @RequestParam AccountType newType,
                                      RedirectAttributes attributes, HttpServletRequest request) {
        // todo : check for need to remove groups (use an exception)
        // todo : check for switch during account billing (don't need to bill now, just do @ next cycle)
        accountBroker.changeAccountType(getUserProfile().getUid(), accountUid, newType);
        addMessage(attributes, MessageType.SUCCESS, "account.changetype.success", request);
        attributes.addAttribute("accountUid", accountUid);
        return "redirect:/account/view";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/add", method = RequestMethod.POST)
    public String addGroupAsPaidFor(@RequestParam String accountUid, @RequestParam String groupUid,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        accountGroupBroker.addGroupToAccount(accountUid, groupUid, getUserProfile().getUid());
        attributes.addAttribute("accountUid", accountUid);
        addMessage(attributes, MessageType.SUCCESS, "account.addgroup.success", request);
        return "redirect:/account/view";
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/group/add/all", method = RequestMethod.GET)
    public String addAllGroupsToAccount(RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountBroker.loadUsersAccount(getUserProfile().getUid());
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
            accountGroupBroker.removeGroupFromAccount(accountUid, paidGroupUid, getUserProfile().getUid());
            addMessage(model, MessageType.INFO, "account.remgroup.success", request);
        } else {
            addMessage(model, MessageType.ERROR, "account.remgroup.failed", request);
        }
        return viewPaidAccount(model, accountUid);
    }

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/statement/view", method = RequestMethod.GET)
    public String viewAccountBillingStatement(Model model, @RequestParam String accountUid, @RequestParam String recordUid) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        model.addAttribute("account", account);
        model.addAttribute("record", accountBillingBroker.fetchBillingRecord(recordUid));
        return "/account/view_statement";
    }

    // todo : as with groups, have an intermediate step if autocomplete fails
    // todo : switch account/admin to many-to-many
    // todo : usual exception handling etc
    // todo : add 'remove method'
    // todo : check why user autocomplete is not restricting to user's graph
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/admin/add", method = RequestMethod.GET)
    public String addAccountAdmin(@RequestParam String accountUid, @RequestParam String newAdminMsisdn,
                                  RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountBroker.loadAccount(accountUid);
        validateUserIsAdministrator(account);
        User newAdmin = userManagementService.findByInputNumber(newAdminMsisdn);
        accountBroker.addAdministrator(getUserProfile().getUid(), accountUid, newAdmin.getUid());
        addMessage(attributes, MessageType.SUCCESS, "account.admin.added", request);
        attributes.addAttribute("accountUid", accountUid);
        return "redirect:/account/view";
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/payment/change", method = RequestMethod.GET)
    public String changePaymentMethod(Model model, @RequestParam(required = false) String accountUid) {
        Account account = StringUtils.isEmpty(accountUid) ? accountBroker.loadUsersAccount(getUserProfile().getUid()) :
                accountBroker.loadAccount(accountUid);
        model.addAttribute("account", account);
        model.addAttribute("newAccount", false);
        model.addAttribute("billingAmount", "R" + (new DecimalFormat("#.##").format(PAYMENT_VERIFICATION_AMT / 100)));
        model.addAttribute("method", PaymentMethod.makeEmpty());
        return "/account/payment";
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/payment/change", method = RequestMethod.POST)
    public String changePaymentDo(RedirectAttributes attributes, @RequestParam String accountUid,
                                  @ModelAttribute("method") PaymentMethod paymentMethod, HttpServletRequest request) {
        AccountBillingRecord record = accountBillingBroker.generatePaymentChangeBill(accountUid, PAYMENT_VERIFICATION_AMT);
        boolean paymentSucceded = paymentServiceBroker.linkPaymentMethodToAccount(paymentMethod, accountUid, record, true);
        attributes.addAttribute("accountUid", accountUid);
        addMessage(attributes, paymentSucceded ? MessageType.SUCCESS : MessageType.ERROR,
                paymentSucceded ? "account.payment.changed" : "account.payment.failed", request);
        return paymentSucceded ? "redirect:/account/view" : "redirect:change";
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

}
