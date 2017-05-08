package za.org.grassroot.services.livewire;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.livewire.DataSubscriber;

import java.util.List;

/**
 * Created by luke on 2017/05/05.
 */
public interface DataSubscriberBroker {

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<DataSubscriber> listSubscribers(final boolean activeOnly, final Sort sort);

    DataSubscriber viewSubscriber(final String viewingUserUid, final String subscriberUid);

    void create(final String sysAdminUid, final String displayName, final String primaryEmail,
                boolean addPrimaryEmailToPush, List<String> additionalPushEmails, boolean active);

    void updateActiveStatus(final String sysAdminUid, final String subscriberUid, final boolean active);

    void addPushEmails(final String userUid, final String subscriberUid, final List<String> pushEmails);

    // userUid can be null if owner of push email is not a user
    void removePushEmails(final String userUid, final String subscriberUid, final List<String> pushEmails);

    void addUsersWithViewAccess(final String adminUid, final String subscriberUid, final List<String> userUids);

    void removeUsersWithViewAccess(final String adminUid, final String subscriberUid, final List<String> userUids);

    List<String> fetchAllPushEmails();

}
