package za.org.grassroot.webapp.controller.rest.android2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.user.UserManagementService;

import java.util.Set;

@RestController
@RequestMapping(value = "/api/mobile/livewire")
public class LiveWireController {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireController.class);

    private final UserManagementService userManagementService;
    private final GroupBroker groupBroker;
    private final EventBroker eventBroker;
    private final StorageBroker storageBroker;
    private final LiveWireAlertBroker liveWireAlertBroker;

    public LiveWireController(UserManagementService userManagementService, GroupBroker groupBroker, EventBroker eventBroker, StorageBroker storageBroker, LiveWireAlertBroker liveWireAlertBroker) {
        this.userManagementService = userManagementService;
        this.groupBroker = groupBroker;
        this.eventBroker = eventBroker;
        this.storageBroker = storageBroker;
        this.liveWireAlertBroker = liveWireAlertBroker;
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public ResponseEntity<String> createLiveWireAlert(@RequestParam String userUid,
                                                      @RequestParam String headline,
                                                      @RequestParam String description,
                                                      @RequestParam LiveWireAlertType type,
                                                      @RequestParam String groupUid,
                                                      @RequestParam String taskUid,
                                                      @RequestParam boolean addLocation,
                                                      @RequestParam Double latitude,
                                                      @RequestParam Double longitude,
                                                      @RequestParam Set<String> mediaFileKeys) {
        LiveWireAlert.Builder builder = LiveWireAlert.newBuilder();
        builder.creatingUser(userManagementService.load(userUid))
            .headline(headline)
            .description(description)
            .type(type);

        if (LiveWireAlertType.INSTANT.equals(type)) {
            builder.group(groupBroker.load(groupUid));
        } else if (LiveWireAlertType.MEETING.equals(type)) {
            builder.meeting(eventBroker.loadMeeting(taskUid));
        }

        if (addLocation) {
            builder.location(new GeoLocation(latitude, longitude), LocationSource.convertFromInterface(UserInterfaceType.ANDROID));
        }

        if (mediaFileKeys != null && !mediaFileKeys.isEmpty()) {
            builder.mediaFiles(storageBroker.retrieveMediaRecordsForFunction(MediaFunction.LIVEWIRE_MEDIA, mediaFileKeys));
        }

        return ResponseEntity.ok(liveWireAlertBroker.create(builder));
    }
}
