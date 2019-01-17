package za.org.grassroot.webapp.controller.rest.livewire;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.dto.DataSubscriberAdminDTO;
import za.org.grassroot.core.dto.DataSubscriberDTO;
import za.org.grassroot.core.dto.UserAdminDTO;
import za.org.grassroot.core.enums.DataSubscriberType;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;
import za.org.grassroot.integration.socialmedia.FBPostBuilder;
import za.org.grassroot.integration.socialmedia.GenericPostResponse;
import za.org.grassroot.integration.socialmedia.SocialMediaBroker;
import za.org.grassroot.integration.socialmedia.TwitterPostBuilder;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.livewire.LiveWireSendingBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.LiveWireAlertDTO;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
    private final UserManagementService userManagementService;
    private final PasswordTokenService passwordTokenService;

    private JwtService jwtService;

    private static final Pattern emailSplitPattern = Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b",
            Pattern.CASE_INSENSITIVE);

    public LiveWireAdminRestController(UserManagementService userManagementService,
                                       StorageBroker storageBroker,
                                       MediaFileBroker mediaFileBroker,
                                       SocialMediaBroker socialMediaBroker,
                                       LiveWireSendingBroker liveWireSendingBroker,
                                       PasswordTokenService passwordTokenService,
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
        this.userManagementService = userManagementService;
        this.passwordTokenService = passwordTokenService;
    }

    @Autowired
    public void setJwtService(JwtService jwtService) {
        this.jwtService = jwtService;
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
        log.info("Modifying LiveWire alert description ...");
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
        List<DataSubscriberDTO> dataSubscriberDTOS = dataSubscriberBroker.listPublicSubscribers().stream()
                .map(DataSubscriberDTO::new).collect(Collectors.toList());
        return ResponseEntity.ok(dataSubscriberDTOS);
    }

    @PostMapping(value = "/release")
    public ResponseEntity<LiveWireAlertDTO> releaseAlert(@RequestParam String alertUid,
                                                         @RequestParam List<String> publicLists,
                                                         HttpServletRequest request){
        liveWireAlertBroker.reviewAlert(getUserIdFromRequest(request), alertUid, null, true, publicLists);
//        LiveWireAlert alert = liveWireAlertBroker.load(alertUid);
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

        List<DataSubscriberAdminDTO> dataSubscriberDTOS = dataSubscriberBroker.listSubscribers(false,new Sort(Sort.Direction.ASC, "displayName"))
                .stream().map(DataSubscriberAdminDTO::new).collect(Collectors.toList());

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

    @RequestMapping(value = "/subscriber/access/users",method = RequestMethod.GET)
    public ResponseEntity<List<UserAdminDTO>> getUsersWithAccess(@RequestParam String subscriberUid,
                                                                 HttpServletRequest request){
        DataSubscriber dataSubscriber = dataSubscriberBroker.validateSubscriberAdmin(getUserIdFromRequest(request),subscriberUid);
        List<UserAdminDTO> userAdminDTOS = new ArrayList<>();
        if(dataSubscriber != null){
            List<User> users = dataSubscriber.getUsersWithAccess().stream()
                    .map(uid -> userManagementService.load(uid)).collect(Collectors.toList());
            users.forEach(user -> userAdminDTOS.add(new UserAdminDTO(user)));
        }
        return ResponseEntity.ok(userAdminDTOS);
    }

    @RequestMapping(value = "/subscriber/emails/add", method = RequestMethod.POST)
    public ResponseEntity addPushEmailsToSubscriber(@RequestParam String subscriberUid,
                                                    @RequestParam String emailsToAdd,
                                                    HttpServletRequest request){
        RestMessage restMessage = null;
        try {
            DataSubscriber dataSubscriber = dataSubscriberBroker.validateSubscriberAdmin(getUserIdFromRequest(request),subscriberUid);
            List<String> emails = new ArrayList<>();

            if (emailsToAdd != null) {
                emails.addAll(splitEmailInput(emailsToAdd));
                dataSubscriberBroker.addPushEmails(getUserIdFromRequest(request), dataSubscriber.getUid(), emails);
                restMessage = RestMessage.UPDATED;
            }

        }catch (AccessDeniedException e){
            restMessage = RestMessage.ERROR;
        }
        return ResponseEntity.ok(restMessage.name());
    }

    @RequestMapping(value = "/subscriber/emails/remove", method = RequestMethod.POST)
    public ResponseEntity removePushEmailFromSubscriber(@RequestParam String subscriberUid,
                                                        @RequestParam String emailsToRemove,
                                                        HttpServletRequest request){
        DataSubscriber subscriber = dataSubscriberBroker.validateSubscriberAdmin(getUserIdFromRequest(request), subscriberUid);
        List<String> emails = splitEmailInput(emailsToRemove);
        RestMessage restMessage;
        try {
            dataSubscriberBroker.removePushEmails(getUserIdFromRequest(request), subscriber.getUid(), emails);
            restMessage = RestMessage.UPDATED;
        }catch (AccessDeniedException e){
            restMessage = RestMessage.ERROR;
        }
        return ResponseEntity.ok(restMessage.name());
    }

    @RequestMapping(value = "/subscriber/user/add", method = RequestMethod.POST)
    public ResponseEntity addUserUidToSubscriber(@RequestParam String subscriberUid,
                                                 @RequestParam String addUserPhone,
                                                 HttpServletRequest request){
        RestMessage restMessage;
        try{
            String msisdn = PhoneNumberUtil.convertPhoneNumber(addUserPhone);
            User user = userManagementService.findByInputNumber(msisdn);
            dataSubscriberBroker.addUsersWithViewAccess(getUserIdFromRequest(request), subscriberUid,
                    Collections.singleton(user.getUid()));
            restMessage = RestMessage.UPDATED;
        }catch (AccessDeniedException | InvalidPhoneNumberException e){
            restMessage = RestMessage.ERROR;
        }
        return ResponseEntity.ok(restMessage.name());
    }

    @RequestMapping(value = "/subscriber/user/remove", method = RequestMethod.POST)
    public ResponseEntity removeUserUidFromSubscriber(@RequestParam String subscriberUid,
                                                      @RequestParam String userToRemoveUid,
                                                      HttpServletRequest request){
        RestMessage restMessage;
        try {
            dataSubscriberBroker.removeUsersWithViewAccess(getUserIdFromRequest(request), subscriberUid,
                    Collections.singleton(userToRemoveUid));
            restMessage = RestMessage.UPDATED;
        }catch (AccessDeniedException e){
            restMessage = RestMessage.ERROR;
        }
        return ResponseEntity.ok(restMessage.name());
    }

    @RequestMapping(value = "/subscriber/permissions/change", method = RequestMethod.POST)
    public ResponseEntity updateDataSubscriberPermissions(@RequestParam String subscriberUid,
                                                 @RequestParam Boolean canTag,
                                                 @RequestParam Boolean canRelease,
                                                 HttpServletRequest request){
        RestMessage restMessage;
        try {
            dataSubscriberBroker.updateSubscriberPermissions(getUserIdFromRequest(request), subscriberUid,
                    canTag != null ? canTag : false, canRelease != null ? canRelease : false);
            restMessage = RestMessage.UPDATED;
        }catch (AccessDeniedException e){
            restMessage = RestMessage.ERROR;
        }
        return ResponseEntity.ok(restMessage.name());
    }

    @RequestMapping(value = "/subscriber/type/change", method = RequestMethod.POST)
    public ResponseEntity updateDataSubscriberType(@RequestParam String subscriberUid,
                                                   @RequestParam String subscriberType,
                                                   HttpServletRequest request){
        RestMessage restMessage;
        try{
            dataSubscriberBroker.updateSubscriberType(getUserIdFromRequest(request), subscriberUid, DataSubscriberType.valueOf(subscriberType));
            restMessage = RestMessage.UPDATED;
        }catch (AccessDeniedException e){
            restMessage = RestMessage.ERROR;
        }
        return ResponseEntity.ok(restMessage.name());
    }

    @RequestMapping(value = "/subscriber/active/otp", method = RequestMethod.GET)
    public ResponseEntity sendAdminOtp(HttpServletRequest request){
        RestMessage restMessage;
        try {
            passwordTokenService.triggerOtp(getUserFromRequest(request));
            restMessage = RestMessage.VERIFICATION_TOKEN_SENT;
        }catch (Exception e){
            restMessage = RestMessage.ERROR;
        }
        return ResponseEntity.ok(restMessage.name());
    }

    @RequestMapping(value = "/subscriber/active/status", method = RequestMethod.POST)
    public ResponseEntity activateSubscriber(@RequestParam String subscriberUid,
                                     @RequestParam String otpEntered,
                                     HttpServletRequest request){
        RestMessage restMessage;
        if (!passwordTokenService.isShortLivedOtpValid(getUserFromRequest(request).getPhoneNumber(), otpEntered)) {
            restMessage = RestMessage.INVALID_OTP;
        }else{
            DataSubscriber subscriber = dataSubscriberBroker.validateSubscriberAdmin(getUserIdFromRequest(request), subscriberUid);
            dataSubscriberBroker.updateActiveStatus(getUserIdFromRequest(request), subscriberUid, !subscriber.isActive());
            restMessage = RestMessage.UPDATED;
        }
        return ResponseEntity.ok(restMessage.name());
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/subscriber/token", method = RequestMethod.GET)
    public ResponseEntity getSubscriberToken(@RequestParam String subscriberUid) {
        DataSubscriber subscriberAdmin= dataSubscriberBroker.load(subscriberUid);
        CreateJwtTokenRequest jwtTokenRequest = new CreateJwtTokenRequest(JwtType.API_CLIENT);
        jwtTokenRequest.addClaim(JwtService.USER_UID_KEY, subscriberAdmin.getUid());
        jwtTokenRequest.addClaim(JwtService.SYSTEM_ROLE_KEY, BaseRoles.ROLE_LIVEWIRE_USER);
        return ResponseEntity.ok(jwtService.createJwt(jwtTokenRequest));
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
