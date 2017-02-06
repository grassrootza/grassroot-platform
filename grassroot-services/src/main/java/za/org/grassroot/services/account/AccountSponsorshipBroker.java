package za.org.grassroot.services.account;

import org.springframework.data.domain.Sort;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.core.enums.AssocRequestStatus;

import java.util.List;

/**
 * Created by luke on 2017/02/06.
 */
public interface AccountSponsorshipBroker {

    void openSponsorshipRequest(String openingUserUid, String accountUid, String requestedUserUid, String messageToUser);

    AccountSponsorshipRequest fetchSponsorshipRequest(String requestUid);

    void denySponsorshipRequest(String requestUid);

    void approveSponsorshipRequest(String requestUid);

    /*
    In case user approves, but then payment fails / they back out
     */
    void abortSponsorshipRequest(String requestUid);

    List<AccountSponsorshipRequest> requestsForUser(String userUid, AssocRequestStatus status, Sort sort);

    List<AccountSponsorshipRequest> requestsForAccount(String accountUid, AssocRequestStatus status, Sort sort);

}
