package za.org.grassroot.webapp.controller.rest.livewire;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.dto.DataSubscriberAdminDTO;
import za.org.grassroot.core.dto.DataSubscriberDTO;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.socialmedia.FBPostBuilder;
import za.org.grassroot.integration.socialmedia.GenericPostResponse;
import za.org.grassroot.integration.socialmedia.SocialMediaBroker;
import za.org.grassroot.integration.socialmedia.TwitterPostBuilder;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.livewire.LiveWireSendingBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.LiveWireAlertDTO;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController @Slf4j
@Api("/api/livewire/admin")
@RequestMapping(value = "/v2/api/livewire/admin")
@PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
public class LiveWireAdminRestController extends BaseRestController {

    private final StorageBroker storageBroker;

    private final LiveWireAlertBroker liveWireAlertBroker;
    private final MediaFileBroker mediaFileBroker;
    private final DataSubscriberBroker dataSubscriberBroker;
    private final LiveWireSendingBroker liveWireSendingBroker;
    private final SocialMediaBroker socialMediaBroker;

    private static final Pattern emailSplitPattern = Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b",
            Pattern.CASE_INSENSITIVE);

    public LiveWireAdminRestController(UserManagementService userManagementService,
                                       StorageBroker storageBroker,
                                       MediaFileBroker mediaFileBroker,
                                       SocialMediaBroker socialMediaBroker,
                                       LiveWireSendingBroker liveWireSendingBroker,
                                       DataSubscriberBroker dataSubscriberBroker,
                                       LiveWireAlertBroker liveWireAlertBroker,
                                       JwtService jwtService){
        super(jwtService,userManagementService);
        this.storageBroker = storageBroker;
        this.liveWireAlertBroker = liveWireAlertBroker;
        this.mediaFileBroker = mediaFileBroker;
        this.dataSubscriberBroker = dataSubscriberBroker;
        this.liveWireSendingBroker = liveWireSendingBroker;
        this.socialMediaBroker = socialMediaBroker;
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
                                                      @RequestParam Set<String> mediaFileKeys,
                                                      HttpServletRequest request){
        LiveWireAlert liveWireAlert = liveWireAlertBroker.load(alertUid);
        Set<MediaFileRecord> records = storageBroker.retrieveMediaRecordsForFunction(MediaFunction.LIVEWIRE_MEDIA, mediaFileKeys);
        log.info("Records to add....{}",records);
        for (MediaFileRecord record : records) {
            liveWireAlertBroker.addMediaFile(getUserIdFromRequest(request), liveWireAlert.getUid(), record);
        }

        log.info("Media files....{}",liveWireAlert.getMediaFiles());
        return ResponseEntity.ok(new LiveWireAlertDTO(liveWireAlertBroker.load(liveWireAlert.getUid())));
    }

    @PostMapping(value = "modify/images/delete")
    public ResponseEntity<LiveWireAlertDTO> deleteImages(@RequestParam String imageUid,
                                                         @RequestParam String alertUid){
        mediaFileBroker.deleteFile(liveWireAlertBroker.load(alertUid),imageUid,MediaFunction.LIVEWIRE_MEDIA);
        return ResponseEntity.ok(new LiveWireAlertDTO(liveWireAlertBroker.load(alertUid)));
    }

    @PostMapping(value = "/tag")
    public ResponseEntity<LiveWireAlertDTO> tagAlert(@RequestParam String alertUid,
                                                     @RequestParam String tags,
                                                     HttpServletRequest request){
        List<String> tagList = Arrays.asList(tags.split("\\s*,\\s*"));
        liveWireAlertBroker.setTagsForAlert(getUserIdFromRequest(request), alertUid, tagList);
        return ResponseEntity.ok(new LiveWireAlertDTO(liveWireAlertBroker.load(alertUid)));
    }

    @PostMapping(value = "/block")
    public ResponseEntity<LiveWireAlertDTO> blockAlert(@RequestParam String alertUid,
                                                       HttpServletRequest request){
        liveWireAlertBroker.reviewAlert(getUserIdFromRequest(request), alertUid, null, false, null);
        return ResponseEntity.ok(new LiveWireAlertDTO(liveWireAlertBroker.load(alertUid)));
    }

    @GetMapping(value = "/subscribers")
    public ResponseEntity<List<DataSubscriberDTO>> getSubscribers(HttpServletRequest request){
        if(liveWireAlertBroker.canUserRelease(getUserIdFromRequest(request))){
            List<DataSubscriberDTO> dataSubscriberDTOS = dataSubscriberBroker.listPublicSubscribers().stream()
                    .map(DataSubscriberDTO::new).collect(Collectors.toList());
            return ResponseEntity.ok(dataSubscriberDTOS);
        } else {
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @PostMapping(value = "/release")
    public ResponseEntity<LiveWireAlertDTO> releaseAlert(@RequestParam String alertUid,
                                                         @RequestParam List<String> publicLists,
                                                         HttpServletRequest request){
        log.info("Data subscriber list.....{}",publicLists);
        liveWireAlertBroker.reviewAlert(getUserIdFromRequest(request), alertUid, null, true, publicLists);
        LiveWireAlert alert = liveWireAlertBroker.load(alertUid);
        log.info("alert lists? : {}", alert.getPublicListsUids());
        liveWireSendingBroker.sendLiveWireAlerts(Collections.singleton(alertUid));
        return ResponseEntity.ok(new LiveWireAlertDTO(liveWireAlertBroker.load(alertUid)));
    }

    @PostMapping(value = "/post/facebook")
    public ResponseEntity<List<GenericPostResponse>> postOnFB(@RequestParam String facebookPageId,
                                                              @RequestParam String message,
                                                              @RequestParam String linkUrl,
                                                              @RequestParam String linkName,
                                                              @RequestParam String imageKey,
                                                              @RequestParam MediaFunction imageMediaType,
                                                              @RequestParam String imageCaption,
                                                              HttpServletRequest request){
        FBPostBuilder fbPostBuilder = FBPostBuilder.builder()
                .postingUserUid(getUserIdFromRequest(request))
                .linkName(linkName)
                .linkUrl(linkUrl)
                .facebookPageId(facebookPageId)
                .imageCaption(imageCaption)
                .imageMediaType(imageMediaType)
                .imageKey(imageKey)
                .message(message)
                .build();

        List<FBPostBuilder> posts = new ArrayList<>();
        posts.add(fbPostBuilder);
        log.info("Post to share on FB.....{}",posts);

        return ResponseEntity.ok(socialMediaBroker.postToFacebook(posts));
    }

    @PostMapping(value = "/post/twitter")
    public ResponseEntity<GenericPostResponse> tweet(@RequestParam String message,
                                                     @RequestParam MediaFunction imageMediaFunction,
                                                     @RequestParam String imageKey,
                                                     HttpServletRequest request){
        TwitterPostBuilder tweet = TwitterPostBuilder.builder()
                .postingUserUid(getUserIdFromRequest(request))
                .message(message)
                .imageKey(imageKey)
                .imageMediaFunction(imageMediaFunction).build();
        return ResponseEntity.ok(socialMediaBroker.postToTwitter(tweet));
    }

    @RequestMapping(value = "/list/subscribers",method = RequestMethod.GET)
    public ResponseEntity<List<DataSubscriberAdminDTO>> listDataSubscribers(){
        List<DataSubscriber> dataSubscribers = dataSubscriberBroker.listSubscribers(false,new Sort(Sort.Direction.ASC, "displayName"));
        List<DataSubscriberAdminDTO> dataSubscriberDTOS = new ArrayList<>();

        dataSubscribers.forEach(dataSubscriber -> dataSubscriberDTOS.add(new DataSubscriberAdminDTO(dataSubscriber)));

       /* dataSubscriberDTOS = dataSubscriberBroker.listSubscribers(false,new Sort(Sort.Direction.ASC, "displayName"))
                .stream().map(DataSubscriberAdminDTO::new).collect(Collectors.toList());*/
        return ResponseEntity.ok(dataSubscriberDTOS);
    }

    @RequestMapping(value = "/create/subscriber",method = RequestMethod.POST)
    public ResponseEntity createSubscriber(@RequestParam String displayName,
                                           @RequestParam String primaryEmail,
                                           @RequestParam Boolean addToPushEmails,
                                           @RequestParam String emailsForPush,
                                           @RequestParam Boolean active,
                                           HttpServletRequest request){
        log.info("name={},primary email={},add primary email={},emais for push={},active={}",displayName,primaryEmail,addToPushEmails,emailsForPush,active);
        List<String> emails = emailsForPush == null ? null : splitEmailInput(emailsForPush);
        RestMessage restMessage;
        try{
            dataSubscriberBroker.create(getUserIdFromRequest(request), displayName, primaryEmail,
                    addToPushEmails != null && addToPushEmails, emails, active != null && active);
            restMessage = RestMessage.ACCOUNT_CREATED;
        }catch (Exception e){
            restMessage = RestMessage.ERROR;
        }
        return ResponseEntity.ok(restMessage.name());
    }

    @RequestMapping(value = "/subscriber/load",method = RequestMethod.GET)
    public ResponseEntity<DataSubscriberAdminDTO> loadDataSubscriber(@RequestParam String subscriberUid){
        return ResponseEntity.ok(new DataSubscriberAdminDTO(dataSubscriberBroker.load(subscriberUid)));
    }

    private List<String> splitEmailInput(String emailsInSingleString) {
        Matcher emailMatcher = emailSplitPattern.matcher(emailsInSingleString);
        List<String> emails = new ArrayList<>();
        while (emailMatcher.find()) {
            emails.add(emailMatcher.group());
        }
        return emails;
    }
}
