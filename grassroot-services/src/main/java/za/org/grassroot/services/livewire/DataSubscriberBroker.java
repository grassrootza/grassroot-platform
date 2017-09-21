package za.org.grassroot.services.livewire;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.DataSubscriberType;

import java.util.List;
import java.util.Set;

/**
 * Created by luke on 2017/05/05.
 */
public interface DataSubscriberBroker {

    DataSubscriber load(String dataSubcriberUid);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<DataSubscriber> listSubscribers(final boolean activeOnly, final Sort sort);

    List<DataSubscriber> listPublicSubscribers();

    DataSubscriber validateSubscriberAdmin(final String viewingUserUid, final String subscriberUid);

    boolean doesUserHaveCustomLiveWireList(final String userUid);

    DataSubscriber fetchLiveWireListForSubscriber(final String userUid);

    void create(final String sysAdminUid, final String displayName, final String primaryEmail,
                boolean addPrimaryEmailToPush, List<String> additionalPushEmails, boolean active);

    void updateActiveStatus(final String sysAdminUid, final String subscriberUid, final boolean active);

    void addPushEmails(final String userUid, final String subscriberUid, final List<String> pushEmails);

    // userUid can be null if owner of push email is not a user
    // subscriberUid can be null if need to remove from any/all subscribers
    void removePushEmails(final String userUid, final String subscriberUid, final List<String> pushEmails);

    void removeEmailFromAllSubscribers(final String pushEmail);

    void addUsersWithViewAccess(final String adminUid, final String subscriberUid, final Set<String> userUids);

    void removeUsersWithViewAccess(final String adminUid, final String subscriberUid, final Set<String> userUids);

    void updateSubscriberPermissions(final String adminUid, final String subscriberUid,
                                     final boolean canTag, final boolean canRelease);

    void updateSubscriberType(String userUid, String subscriberUid, DataSubscriberType type);

    int countPushEmails(LiveWireAlert alert);

}
