package za.org.grassroot.webapp.controller.rest.livewire;

import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.enums.LiveWireAlertDestType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.util.Set;

@RestController
@Api("/api/livewire")
@RequestMapping(value = "/api/livewire")
public class LiveWireController {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireController.class);

    private final UserManagementService userManagementService;
    private final GroupBroker groupBroker;
    private final EventBroker eventBroker;
    private final StorageBroker storageBroker;

    private final LiveWireAlertBroker liveWireAlertBroker;
    private final DataSubscriberBroker subscriberBroker;

    @Autowired
    public LiveWireController(UserManagementService userManagementService, GroupBroker groupBroker, EventBroker eventBroker, StorageBroker storageBroker, LiveWireAlertBroker liveWireAlertBroker, DataSubscriberBroker subscriberBroker) {
        this.userManagementService = userManagementService;
        this.groupBroker = groupBroker;
        this.eventBroker = eventBroker;
        this.storageBroker = storageBroker;
        this.liveWireAlertBroker = liveWireAlertBroker;
        this.subscriberBroker = subscriberBroker;
    }

    @RequestMapping(value = "/create/{userUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createLiveWireAlert(@PathVariable String userUid,
                                                               @RequestParam String headline,
                                                               @RequestParam(required = false) String description,
                                                               @RequestParam LiveWireAlertType type,
                                                               @RequestParam(required = false) String groupUid,
                                                               @RequestParam(required = false) String taskUid,
                                                               @RequestParam boolean addLocation,
                                                               @RequestParam(required = false) Double latitude,
                                                               @RequestParam(required = false) Double longitude,
                                                               @RequestParam(required = false) LiveWireAlertDestType destType,
                                                               @RequestParam(required = false) String destUid,
                                                               @RequestParam(required = false) Set<String> mediaFileKeys,
                                                               @RequestParam(required = false) String contactName,
                                                               @RequestParam(required = false) String contactNumber) {
        User creatingUser = userManagementService.load(userUid);
        LiveWireAlert.Builder builder = LiveWireAlert.newBuilder();

        builder.creatingUser(creatingUser)
                .contactUser(creatingUser)
                .headline(headline)
                .description(description)
                .contactName(contactName)
                .contactNumber(contactNumber)
                .type(type);

        logger.info("do we have mediaFiles? {}", mediaFileKeys);

        if (LiveWireAlertType.INSTANT.equals(type)) {
            builder.group(groupBroker.load(groupUid));
        } else if (LiveWireAlertType.MEETING.equals(type)) {
            builder.meeting(eventBroker.loadMeeting(taskUid));
        }

        if (addLocation) {
            builder.location(new GeoLocation(latitude, longitude), LocationSource.convertFromInterface(UserInterfaceType.ANDROID));
        }

        if (destType == null) {
            builder.destType(LiveWireAlertDestType.PUBLIC_LIST);
        } else {
            builder.destType(destType);
            if (!StringUtils.isEmpty(destUid)) {
                builder.destSubscriber(subscriberBroker.load(destUid));
            }
        }

        if (mediaFileKeys != null && !mediaFileKeys.isEmpty()) {
            Set<MediaFileRecord> records = storageBroker.retrieveMediaRecordsForFunction(MediaFunction.LIVEWIRE_MEDIA, mediaFileKeys);
            builder.mediaFiles(records);
        }

        return RestUtil.okayResponseWithData(RestMessage.LIVEWIRE_ALERT_CREATED,
                liveWireAlertBroker.createAsComplete(userUid, builder));
    }
}
