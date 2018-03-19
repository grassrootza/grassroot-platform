package za.org.grassroot.webapp.controller.rest.livewire;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.LiveWireAlertDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;

@RestController @Slf4j
@Api("/api/livewire")
@RequestMapping(value = "/api/livewire")
public class LiveWireController extends BaseRestController{

    private final UserManagementService userManagementService;
    private final GroupBroker groupBroker;
    private final EventBroker eventBroker;
    private final StorageBroker storageBroker;

    private final LiveWireAlertBroker liveWireAlertBroker;
    private final DataSubscriberBroker subscriberBroker;
    private final MediaFileBroker mediaFileBroker;

    @Autowired
    public LiveWireController(UserManagementService userManagementService,
                              GroupBroker groupBroker,
                              EventBroker eventBroker,
                              StorageBroker storageBroker,
                              MediaFileBroker mediaFileBroker,
                              LiveWireAlertBroker liveWireAlertBroker,
                              DataSubscriberBroker subscriberBroker,
                              JwtService jwtService) {
        super(jwtService,userManagementService);
        this.userManagementService = userManagementService;
        this.groupBroker = groupBroker;
        this.eventBroker = eventBroker;
        this.storageBroker = storageBroker;
        this.liveWireAlertBroker = liveWireAlertBroker;
        this.subscriberBroker = subscriberBroker;
        this.mediaFileBroker = mediaFileBroker;
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

        log.info("do we have mediaFiles? {}, task uid {}", mediaFileKeys,taskUid);

        if (LiveWireAlertType.INSTANT.equals(type)) {
            builder.group(groupBroker.load(groupUid));
        } else if (LiveWireAlertType.MEETING.equals(type)) {
            log.info("meeting entity: {}", eventBroker.loadMeeting(taskUid));
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

    @RequestMapping(value = "/list",method = RequestMethod.GET)
    public Page<LiveWireAlertDTO> getLiveWireAlerts(HttpServletRequest request,
                                                    Pageable pageable){
        return liveWireAlertBroker.loadAlerts(getUserIdFromRequest(request),false,pageable).map(LiveWireAlertDTO::new);
    }

    @RequestMapping(value = "/view",method = RequestMethod.GET)
    public ResponseEntity<LiveWireAlertDTO> loadAlert(@RequestParam String serverUid){
        return ResponseEntity.ok(new LiveWireAlertDTO(liveWireAlertBroker.load(serverUid)));
    }

    @RequestMapping(value = "/modify/headline",method = RequestMethod.POST)
    public ResponseEntity<LiveWireAlertDTO> updateHeadline(@RequestParam String alertUid,
                                                           @RequestParam String headline,
                                                           HttpServletRequest request){
        liveWireAlertBroker.updateHeadline(getUserIdFromRequest(request),alertUid,headline);
        return ResponseEntity.ok(new LiveWireAlertDTO(liveWireAlertBroker.load(alertUid)));
    }

    @RequestMapping(value = "/modify/description",method = RequestMethod.POST)
    public ResponseEntity<LiveWireAlertDTO> updateDescription(@RequestParam String alertUid,
                                                              @RequestParam String description,
                                                              HttpServletRequest request){
        liveWireAlertBroker.updateDescription(getUserIdFromRequest(request),alertUid,description);
        return ResponseEntity.ok(new LiveWireAlertDTO(liveWireAlertBroker.load(alertUid)));
    }

    @PostMapping(value = "modify/images/add")
    public ResponseEntity<LiveWireAlertDTO> addImages(@RequestParam String alertUid,
                                                      @RequestParam Set<String> mediaFileKeys){
        LiveWireAlert liveWireAlert = liveWireAlertBroker.load(alertUid);
        Set<MediaFileRecord> records = storageBroker.retrieveMediaRecordsForFunction(MediaFunction.LIVEWIRE_MEDIA, mediaFileKeys);
        liveWireAlert.getMediaFiles().addAll(records);
        return ResponseEntity.ok(new LiveWireAlertDTO(liveWireAlertBroker.load(alertUid)));
    }

    @PostMapping(value = "modify/images/delete")
    public ResponseEntity<LiveWireAlertDTO> deleteImages(@RequestParam String imageUid,
                                                         @RequestParam String alertUid){
        mediaFileBroker.deleteFile(liveWireAlertBroker.load(alertUid),imageUid,MediaFunction.LIVEWIRE_MEDIA);
        return ResponseEntity.ok(new LiveWireAlertDTO(liveWireAlertBroker.load(alertUid)));
    }
}
