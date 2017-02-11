package za.org.grassroot.services.account;

import org.springframework.data.domain.Sort;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;

import java.util.List;

/**
 * Created by luke on 2017/02/06.
 */
public interface AccountSponsorshipBroker {

    AccountSponsorshipRequest load(String requestUid);

    void openSponsorshipRequest(String openingUserUid, String accountUid, String destinationUserUid, String messageToUser);

    void markRequestAsResponded(String requestUid);

    void denySponsorshipRequest(String requestUid);

    boolean hasUserBeenAskedToSponsor(String userUid, String accountUid);

    // if user is the same as the destination of a request, the request will be marked approved
    void closeRequestsAndMarkApproved(String userUid, String accountUid);

    /*
    In case user approves, but then payment fails / they back out
     */
    void abortAndCleanSponsorshipRequests();

    boolean accountHasOpenRequests(String accountUid);

    List<AccountSponsorshipRequest> openRequestsForAccount(String accountUid, Sort sort);

}
