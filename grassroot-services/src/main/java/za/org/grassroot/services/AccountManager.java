package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.PaidGroup;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.PaidGroupRepository;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by luke on 2015/11/12.
 * todo: decide where to implement permission checking for all of this (front end / services, where in services)
 * todo: equals logic in account to prevent duplication
 */
@Service
@Transactional
public class AccountManager implements AccountManagementService {

    private static final Logger log = LoggerFactory.getLogger(AccountManager.class);

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    PaidGroupRepository paidGroupRepository;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    RoleManagementService roleManagementService;

    private String accountAdminRole = "ROLE_ACCOUNT_ADMIN";


    @Override
    public Account createAccount(String accountName) {
        log.info("Okay, creating a bare bones account ... with name: " + accountName);
        Account newAccount = new Account(accountName, true);
        return accountRepository.save(newAccount);
    }

    @Override
    public Account createAccount(String accountName, User administrator) {
        Account account = accountRepository.save(new Account(accountName, administrator));
        log.info("Created the account ... " + account.toString());
        addAdminToUser(administrator, account);
        return accountRepository.save(account);
    }

    @Override
    public Account createAccount(String accountName, User administrator, String billingEmail, boolean enabled) {
        Account account = accountRepository.save(new Account(accountName, administrator, billingEmail, enabled));
        addAdminToUser(administrator, account);
        return account;
    }

    @Override
    public Account addAdministrator(Account account, User administrator) {
        account.addAdministrator(administrator);
        addAdminToUser(administrator, account);
        return accountRepository.save(account);
    }

    @Override
    public Account removeAdministrator(Account account, User administrator) {
        /* account.removeAdministrator(administrator);
        administrator.setAccountAdministered(null);
        userManagementService.save(administrator); */
        roleManagementService.removeStandardRoleFromUser(accountAdminRole, administrator);
        return accountRepository.save(account);
    }

    @Override
    public Account setBillingEmail(Account account, String billingEmail) {
        account.setPrimaryEmail(billingEmail);
        return accountRepository.save(account);
    }

    @Override
    public Account adjustSettings(Account changedAccount) {
        Account savedAccount = accountRepository.findOne(changedAccount.getId());
        savedAccount.setFreeFormMessages(changedAccount.isFreeFormMessages());
        savedAccount.setRelayableMessages(changedAccount.isRelayableMessages());
        savedAccount.setLogbookExtraMessages(changedAccount.isLogbookExtraMessages());
        return accountRepository.save(savedAccount);
    }

    /*
    Helper function to make sure second side of relationship & admin role added to user
     */
    private User addAdminToUser(User user, Account account) {
        // todo: check if there are redundant calls here (though this won't be used often)
        log.info("Wiring up user to account admin ... ");
        user.setAccountAdministered(account);
        user = userManagementService.save(user);
        log.info("User account admin set ... User: " + user.toString());
        return roleManagementService.addStandardRoleToUser(accountAdminRole, user);
//        return user;
    }

    @Override
    public Account loadAccount(Long accountId) {
        return accountRepository.findOne(accountId);
    }

    @Override
    public Account findAccountByAdministrator(User administrator) {
        return accountRepository.findByAdministrators(administrator);
    }

    @Override
    public List<Account> loadAllAccounts() {
        return accountRepository.findAll();
    }

    @Override
    public Group addGroupToAccount(Account account, Group group, User addingUser) throws GroupAlreadyPaidForException {
        // todo: check it isn't already added, didn't exist before, etc

        // do this a bit more elegantly
        if (group.isPaidFor())
            throw new GroupAlreadyPaidForException();
        PaidGroup paidGroup = paidGroupRepository.save(new PaidGroup(group, account, addingUser));
        account.addPaidGroup(paidGroup);
        account = accountRepository.save(account);
        group.setPaidFor(true);
        return groupManagementService.saveGroup(group, true, "Set paid for", addingUser.getId());
    }

    @Override
    public Account findAccountForGroup(Group group) {
        if (!group.isPaidFor())
            return null;
        else
            return paidGroupRepository.findByGroupOrderByExpireDateTimeDesc(group).get(0).getAccount();
    }

    @Override
    public Account removeGroupFromAccount(Account account, PaidGroup paidGroupRecord, User removingUser) {
        Group group = paidGroupRecord.getGroup();

        paidGroupRecord.setExpireDateTime(Timestamp.valueOf(LocalDateTime.now()));
        paidGroupRecord.setRemovedByUser(removingUser);
        paidGroupRecord.setArchivedAccountId(account.getId());
        paidGroupRecord.setAccount(getNullAccount());

        account.removePaidGroup(paidGroupRecord);
        group.setPaidFor(false);

        paidGroupRepository.save(paidGroupRecord);
        log.info("PaidGroup entity now ... " + paidGroupRecord);
        groupManagementService.saveGroup(group, true,"Remove paid for",removingUser.getId());
        return accountRepository.save(account);
    }

    @Override
    public List<PaidGroup> getGroupsPaidForByAccount(Account account) {
        return paidGroupRepository.findByAccount(account);
    }

    @Override
    public PaidGroup loadPaidGroupEntity(Long paidGroupId) {
        return paidGroupRepository.findOne(paidGroupId);
    }

    private Account getNullAccount() {
        return accountRepository.findOne(0L);
    }
}
