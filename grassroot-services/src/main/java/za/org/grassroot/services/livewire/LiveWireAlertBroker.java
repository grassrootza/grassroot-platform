package za.org.grassroot.services.livewire;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.UserInterfaceType;

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

    String create(String userUid, LiveWireAlertType type, String entityUid);

    void updateContactUser(String userUid, String alertUid, String contactUserUid, String contactName);

    void updateDescription(String userUid, String alertUid, String description);

    void setAlertToSend(String userUid, String alertUid, Instant timeToSend);

    void addLocationToAlert(String userUid, String alertUid, GeoLocation location, UserInterfaceType interfaceType);

    void updateSentStatus(String alertUid, boolean sent);

    List<LiveWireAlert> findAlertsPendingSend();

}
