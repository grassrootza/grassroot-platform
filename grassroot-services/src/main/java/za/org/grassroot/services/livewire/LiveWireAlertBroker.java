package za.org.grassroot.services.livewire;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.LiveWireAlertDestType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.geo.GeographicSearchType;

import java.time.Instant;
import java.util.List;

/**
 * Created by luke on 2017/05/06.
 */
public interface LiveWireAlertBroker {

    LiveWireAlert load(String alertUid);

    boolean canUserCreateAlert(String userUid);

    long countGroupsForInstantAlert(String userUid);

    List<Group> groupsForInstantAlert(String userUid, Integer pageNumber, Integer pageSize);

    List<Meeting> meetingsForAlert(String userUid);

    /*
    Methods to create an alert via web app or controller
     */
    String createAsComplete(String userUid, String headline, String description,
                            LiveWireAlertType type, String entityUid,
                            String contactUserUid, String contactName, String contactNumber,
                            LiveWireAlertDestType destType, DataSubscriber destSubscriber, List<MediaFileRecord> mediaFiles);

    /*
    Methods to create an alert or register as a contact person, via USSD
     */

    String create(String userUid, LiveWireAlertType type, String entityUid);

    String createAsComplete(String userUid, LiveWireAlert.Builder builder);

    void updateContactUser(String userUid, String alertUid, String contactUserUid, String contactName);

    void updateHeadline(String userUid, String alertUid, String headline);

    void updateDescription(String userUid, String alertUid, String description);

    void addMediaFile(String userUid, String alertUid, MediaFileRecord mediaFileRecord);

    // pass null to have it be the public account
    void updateAlertDestination(String userUid, String alertUid, String subscriberUid, LiveWireAlertDestType destType);

    void setAlertComplete(String userUid, String alertUid, Instant soonestTimeToSend);

    void addLocationToAlert(String userUid, String alertUid, GeoLocation location, UserInterfaceType interfaceType);

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

    List<LiveWireAlert> fetchAlertsNearUser(String userUid, GeoLocation location, int radius, GeographicSearchType searchType);

}
