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
    GroupManagementService groupManagementService;

    @Override
    public Account createAccount(String accountName) {
        log.info("Okay, creating a bare bones account ... with name: " + accountName);
        Account newAccount = new Account(accountName, true);
        return accountRepository.save(newAccount);
    }

    @Override
    public Account createAccount(String accountName, User administrator) {
        return accountRepository.save(new Account(accountName, administrator));
    }

    @Override
    public Account createAccount(String accountName, User administrator, String billingEmail, boolean enabled) {
        return accountRepository.save(new Account(accountName, administrator, billingEmail, enabled));
    }

    @Override
    public Account addAdministrator(Account account, User administrator) {
        account.addAdministrator(administrator);
        return accountRepository.save(account);
    }

    @Override
    public Account removeAdministrator(Account account, User administrator) {
        account.removeAdministrator(administrator);
        return accountRepository.save(account);
    }

    @Override
    public Account setBillingEmail(Account account, String billingEmail) {
        account.setPrimaryEmail(billingEmail);
        return accountRepository.save(account);
    }

    @Override
    public Account loadAccount(Long accountId) {
        return accountRepository.findOne(accountId);
    }

    @Override
    public List<Account> findAccountsByAdministrator(User administrator) {
        return null;
    }

    @Override
    public List<Account> loadAllAccounts() {
        return accountRepository.findAll();
    }

    @Override
    public Group addGroupToAccount(Account account, Group group, User addingUser) {
        // todo: check it isn't already added, didn't exist before, etc
        PaidGroup paidGroup = paidGroupRepository.save(new PaidGroup(group, account, addingUser));
        account.addPaidGroup(paidGroup);
        account = accountRepository.save(account);
        group.setPaidFor(true);
        return groupManagementService.saveGroup(group);
    }

    @Override
    public Group removeGroupFromAccount(Account account, Group group, User removingUser) {
        // todo: figure out best way to find paidGroup when given group so can remove from account
        group.setPaidFor(false);
        return groupManagementService.saveGroup(group);
    }
}
