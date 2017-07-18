package za.org.grassroot.services.livewire;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.LiveWireAlertDestType;

import java.time.Instant;
import java.util.List;

/**
 * Created by luke on 2017/05/06.
 */
public interface LiveWireAlertBroker {

    LiveWireAlert load(String alertUid);

    long countGroupsForInstantAlert(String userUid);

    List<Group> groupsForInstantAlert(String userUid, Integer pageNumber, Integer pageSize);

    List<Meeting> meetingsForAlert(String userUid);

    List<User> fetchLiveWireContactsNearby(String queryingUserUid, GeoLocation location, Integer radius);

    /*
    Methods to create an alert or register as a contact person
     */

    String create(String userUid, LiveWireAlertType type, String entityUid);

    void updateContactUser(String userUid, String alertUid, String contactUserUid, String contactName);

    void updateDescription(String userUid, String alertUid, String description);

    // pass null to have it be the public account
    void updateAlertDestination(String userUid, String alertUid, String subscriberUid, LiveWireAlertDestType destType);

    void setAlertComplete(String userUid, String alertUid, Instant soonestTimeToSend);

    void addLocationToAlert(String userUid, String alertUid, GeoLocation location, UserInterfaceType interfaceType);

    // set boolean to false to revoke
    void updateUserLiveWireContactStatus(String userUid, boolean addingPermission, UserInterfaceType interfaceType);

    void trackLocationForLiveWireContact(String userUid, UserInterfaceType type);

    /*
    Methods for loading, tagging, and releasing alerts
     */

    @PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
    Page<LiveWireAlert> loadAlerts(String userUid, boolean unreviewedOnly, Pageable pageable);

    boolean canUserTag(String userUid);

    boolean canUserRelease(String userUid);

    @PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
    void setTagsForAlert(String userUid, String alertUid, List<String> tags);

    @PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
    void reviewAlert(String userUid, String alertUid, List<String> tags, boolean send, List<String> publicListUids);

}
