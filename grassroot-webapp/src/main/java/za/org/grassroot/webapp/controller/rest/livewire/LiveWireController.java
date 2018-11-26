package za.org.grassroot.webapp.controller.rest.livewire;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.home.PublicLiveWireDTO;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;

@RestController @Grassroot2RestController @Slf4j
@RequestMapping(value = "/v2/api/livewire") @Api("/v2/api/livewire")
public class LiveWireController extends BaseRestController{

    private final UserManagementService userManagementService;
    private final GroupBroker groupBroker;
    private final EventBroker eventBroker;
    private final StorageBroker storageBroker;

    private final LiveWireAlertBroker liveWireAlertBroker;
    private final DataSubscriberBroker subscriberBroker;


    @Autowired
    public LiveWireController(UserManagementService userManagementService,
                              GroupBroker groupBroker,
                              EventBroker eventBroker,
                              StorageBroker storageBroker,
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
    }

    @PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
    @RequestMapping(value = "/list/subscriber", method = RequestMethod.GET)
    public Page<PublicLiveWireDTO> fetchLiveWireAlertsForSubscribers(Pageable pageable) {
        return liveWireAlertBroker.fetchReleasedAlerts(pageable).map(alert -> new PublicLiveWireDTO(alert, true, true));
    }

    @PreAuthorize("hasRole('ROLE_FULL_USER')")
    @RequestMapping(value = "/create/{userUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createLiveWireAlert(@RequestParam String headline,
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
                                                               @RequestParam(required = false) String contactNumber,
                                                               @PathVariable String userUid, HttpServletRequest request) {
        User creatingUser = userManagementService.load(getUserIdFromRequest(request));
        LiveWireAlert.Builder builder = LiveWireAlert.newBuilder();

        builder.creatingUser(creatingUser)
                .contactUser(creatingUser)
                .headline(headline)
                .description(description)
                .contactName(contactName)
                .contactNumber(contactNumber)
                .type(type);

        log.info("Dest type....###########{}",destType);

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
}
