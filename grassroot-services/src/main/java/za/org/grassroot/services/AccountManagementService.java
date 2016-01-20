package za.org.grassroot.services;

import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.PaidGroup;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * Created by luke on 2015/11/12.
 */
public interface AccountManagementService {

    /*
    Methods to create institutional accounts, designate administrators and deactivate them
     */

    Account createAccount(String accountName);

    Account createAccount(String accountName, User administrator);

    Account createAccount(String accountName, User administrator, String billingEmail, boolean enabled);

    Account addAdministrator(Account account, User administrator);

    Account removeAdministrator(Account account, User administrator);

    Account setBillingEmail(Account account, String billingEmail);

    /*
    Methods to load, find and save institutional accounts
     */

    Account loadAccount(Long accountId);

    Account findAccountByAdministrator(User administrator);

    List<Account> loadAllAccounts();

    /*
    Methods to designate groups as paid for by accounts (and remove the designation)
     */

    Group addGroupToAccount(Account account, Group group, User addingUser);

    Account findAccountForGroup(Group group);

    Account removeGroupFromAccount(Account account, PaidGroup group, User removingUser);

    /*
    Methods to aggregate information about groups paid for by an account
     */

    List<PaidGroup> getGroupsPaidForByAccount(Account account);

    PaidGroup loadPaidGroupEntity(Long paidGroupId);

    /*
    Methods to handle billing for institutional accounts
     */

}
