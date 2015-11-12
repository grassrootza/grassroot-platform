package za.org.grassroot.services;

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

    @Autowired
    AccountRepository accountRepository;
    
    @Autowired
    PaidGroupRepository paidGroupRepository;

    @Autowired
    GroupManagementService groupManagementService;

    @Override
    public Account createAccount(String accountName) {
        return accountRepository.save(new Account(accountName, true));
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
