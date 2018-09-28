package za.org.grassroot.webapp.controller.rest.whatsapp;

import com.google.common.base.Enums;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j @RestController @Grassroot2RestController
@RequestMapping("/v2/api/whatsapp") @Api("/v2/api/whatsapp")
@PreAuthorize("hasRole('ROLE_SYSTEM_CALL')")
public class WhatsAppRelatedController extends BaseController {

    private final List<RequestDataType> USER_DATA_REQUESTS_WITH_MSGS = Arrays.asList(
            RequestDataType.USER_NAME, RequestDataType.LOCATION_PROVINCE_OKAY, RequestDataType.LOCATION_GPS_REQUIRED, RequestDataType.NONE);

    private final JwtService jwtService;
    private final AsyncUserLogger userLogger;

    private CampaignBroker campaignBroker;
    private GroupBroker groupBroker;
    private LocationInfoBroker locationInfoBroker;
    private MessageSource messageSource;

    @Autowired
    public WhatsAppRelatedController(UserManagementService userManagementService, PermissionBroker permissionBroker, JwtService jwtService, AsyncUserLogger userLogger) {
        super(userManagementService, permissionBroker);
        this.jwtService = jwtService;
        this.userLogger = userLogger;
    }

    @Autowired
    private void setCampaignBroker(CampaignBroker campaignBroker) {
        this.campaignBroker = campaignBroker;
    }

    @Autowired
    public void setGroupBroker(GroupBroker groupBroker) {
        this.groupBroker = groupBroker;
    }

    @Autowired
    public void setLocationInfoBroker(LocationInfoBroker locationInfoBroker) {
        this.locationInfoBroker = locationInfoBroker;
    }

    @Autowired
    public void setMessageSource(@Qualifier("messageSource") MessageSource messageSource) {
        this.messageSource = messageSource;
    }


    // this will get called _a lot_ during a sesion (each message to and fro), so not yet introducting a record user log in
    @RequestMapping(value = "/user/id", method = RequestMethod.POST)
    public ResponseEntity fetchUserId(String msisdn) {
        User user = userManagementService.loadOrCreate(msisdn);
        userLogger.recordUserSession(user.getUid(), UserInterfaceType.WHATSAPP);
        return ResponseEntity.ok(user.getUid());
    }

    // for accessing standard user APIs, but is time limited, and does not include system roles
    // so in case overall microservice token is compromised, only some features can be called
    @RequestMapping(value = "/user/token", method = RequestMethod.POST)
    public ResponseEntity fetchRestrictedUserToken(String userId) {
        CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.MSGING_CLIENT);
        tokenRequest.addClaim(JwtService.USER_UID_KEY, userId);
        tokenRequest.addClaim(JwtService.SYSTEM_ROLE_KEY, BaseRoles.ROLE_FULL_USER);
        userLogger.logUserLogin(userId, UserInterfaceType.WHATSAPP); // keep an eye on how often this gets called (may become redundant)
        return ResponseEntity.ok(jwtService.createJwt(tokenRequest));
    }


    @RequestMapping(value = "/phrase/search", method = RequestMethod.POST)
    public ResponseEntity<PhraseSearchResponse> checkIfPhraseTriggersCampaign(@RequestParam String phrase, @RequestParam String userId) {
        User user = userManagementService.load(userId);
        Campaign campaign = campaignBroker.findCampaignByJoinWord(phrase, userId, UserInterfaceType.WHATSAPP);
        Group group = campaign != null ? null : groupBroker.searchForGroupByWord(userId, phrase);
        log.info("Incoming phrase check, found ? : campaign: {}, group: {}", campaign != null, group != null);
        PhraseSearchResponse response;
        if (campaign != null) {
            response = campaignResponse(user, campaign);
        } else if (group != null) {
            response = groupResponse(user, group);
        } else {
            response = PhraseSearchResponse.notFoundResponse();
        }
        return ResponseEntity.ok(response);
    }

    private PhraseSearchResponse campaignResponse(User user, Campaign campaign) {
        // because campaign admins may mess this up and want some robustness, at little cost
        CampaignMessage whatsAppMsg = campaignBroker.getOpeningMessage(campaign.getUid(), null, UserInterfaceType.WHATSAPP, null);
        CampaignMessage defaultMsg = campaignBroker.getOpeningMessage(campaign.getUid(), null, null, null);

        LinkedHashMap<String, String> menu = getMenuFromMessage(whatsAppMsg != null ? whatsAppMsg : defaultMsg);
        LinkedHashMap<String, String> fallbackMenu = getMenuFromMessage(defaultMsg);

        List<String> responseMsgs = getResponseMessages(whatsAppMsg != null ? whatsAppMsg : defaultMsg,
                !menu.isEmpty() ? menu.values() : fallbackMenu.values());

        campaignBroker.recordEngagement(campaign.getUid(), user.getUid(), UserInterfaceType.WHATSAPP, null);

        return PhraseSearchResponse.builder()
                .entityFound(true)
                .entityType(JpaEntityType.CAMPAIGN)
                .entityUid(campaign.getUid())
                .responseMessages(responseMsgs)
                .responseMenu(!menu.isEmpty() ? menu : fallbackMenu)
                .build();
    }

    private PhraseSearchResponse groupResponse(User user, Group group) {
        RequestDataType outstandingUserInfo = checkForNextUserInfo(user.getUid());
        List<String> messages = new ArrayList<>();
        Locale locale = user.getLocale() == null ? Locale.ENGLISH : user.getLocale();
        messages.add(messageSource.getMessage("whatsapp.group.joined", new String[] { group.getName() }, locale));
        messages.addAll(dataRequestMessages(outstandingUserInfo, JpaEntityType.GROUP));
        log.info("Adding user to group {}, outstanding data request: {}", group.getName(), outstandingUserInfo);
        return PhraseSearchResponse.builder()
                .entityFound(true)
                .entityType(JpaEntityType.GROUP)
                .entityUid(group.getUid())
                .responseMessages(messages)
                .requestDataType(outstandingUserInfo)
                .build();
    }

    @RequestMapping(value = "/entity/respond/{entityType}/{entityUid}", method = RequestMethod.POST)
    public ResponseEntity<EntityResponseToUser> processFurtherResponseToEntity(@PathVariable JpaEntityType entityType,
                                                                               @PathVariable String entityUid,
                                                                               @RequestParam String userId,
                                                                               @RequestBody EntityReplyFromUser userReply) {
        EntityResponseToUser response;
        log.info("Received user response: {}", userReply);
        if (userReply.getAuxProperties() != null && userReply.getAuxProperties().containsKey("requestDataType")) {
            response = replyToDataRequest(userId, userReply, entityType, entityUid);
        } else if (JpaEntityType.CAMPAIGN.equals(entityType)) {
            CampaignActionType actionType = userReply.getMenuOptionPayload() == null ? CampaignActionType.EXIT_POSITIVE
                    : Enums.getIfPresent(CampaignActionType.class, userReply.getMenuOptionPayload()).or(CampaignActionType.EXIT_POSITIVE);
            response = replyToCampaignMessage(userId, entityUid, userReply.getAuxProperties().get("PRIOR"), actionType, userReply.getUserMessage());
        } else {
            response = EntityResponseToUser.cannotRespond(entityType, entityUid);
        }
        log.info("Sending back to user: {}", response);
        return ResponseEntity.ok(response);
    }

    private EntityResponseToUser replyToDataRequest(String userId, EntityReplyFromUser userReply, JpaEntityType entityType, String entityId) {
        RequestDataType requestType = RequestDataType.valueOf(userReply.getAuxProperties().get("requestDataType"));
        if ("<<SKIP>>".equals(userReply.getMenuOptionPayload())) {
            return requestSkipped(userId, requestType, entityType, entityId);
        }

        switch (requestType) {
            case USER_NAME:                 userManagementService.updateDisplayName(userId, userId, userReply.getUserMessage());    break;
            case LOCATION_GPS_REQUIRED:     handleUserLocationResponse(userId, userReply);                                          break;
            case LOCATION_PROVINCE_OKAY:    handleUserLocationResponse(userId, userReply);                                          break;
            default: log.info("Got a user response we can't do anything with. Request type: {}, user response: {}", requestType, userReply);    break;
        }

        RequestDataType nextRequestType = checkForNextUserInfo(userId);

        return EntityResponseToUser.builder()
                .entityType(entityType)
                .entityUid(entityId)
                .messages(dataRequestMessages(nextRequestType, entityType))
                .requestDataType(nextRequestType)
                .build();
    }

    private EntityResponseToUser requestSkipped(String userId, RequestDataType skippedType, JpaEntityType entityType, String entityId) {
        boolean skippingName = RequestDataType.USER_NAME.equals(skippedType);
        UserLogType logType = skippingName ? UserLogType.USER_SKIPPED_NAME : UserLogType.USER_SKIPPED_PROVINCE;
        userLogger.recordUserLog(userId, logType, "Skipped setting detail on WhatsApp", UserInterfaceType.WHATSAPP);

        final String responseMsgKey = skippingName ? "whatsapp.user.skipped.name" : "whatsapp.user.skipped.province";
        final RequestDataType nextRequest = skippingName ? RequestDataType.LOCATION_PROVINCE_OKAY : RequestDataType.NONE;

        return EntityResponseToUser.builder()
                .entityType(entityType)
                .entityUid(entityId)
                .messages(Collections.singletonList(messageSource.getMessage(responseMsgKey, null, Locale.ENGLISH)))
                .requestDataType(nextRequest)
                .build();
    }

    private void handleUserLocationResponse(String userId, EntityReplyFromUser userReply) {
        log.info("Handling user's response to request for province: {}", userReply);
        if (userReply.getLocation() != null) {
            log.info("User sent us a PIN, so handling it");
            Province province = locationInfoBroker.getProvinceFromGeoLocation(userReply.getLocation());
            userManagementService.updateUserProvince(userId, province);
            userLogger.recordUserLocation(userId, userReply.getLocation(), LocationSource.LOGGED_PRECISE, UserInterfaceType.WHATSAPP);
        } else {
            Province province = Enums.getIfPresent(Province.class, userReply.getMenuOptionPayload()).orNull();
            log.info("For user reply {}, extracted {}", userReply.getMenuOptionPayload(), province);
            userManagementService.updateUserProvince(userId, province);
        }
    }

    private EntityResponseToUser replyToCampaignMessage(String userId,
                                                        String campaignUid,
                                                        String priorMessageUid,
                                                        CampaignActionType action,
                                                        String userResponse) {
        log.info("Getting campaign message for action type {}, user response {}, campaign ID: {}", action, userResponse, campaignUid);

        switch (action) {
            case JOIN_GROUP:        campaignBroker.addUserToCampaignMasterGroup(campaignUid, userId, UserInterfaceType.WHATSAPP);   break;
            case SIGN_PETITION:     campaignBroker.signPetition(campaignUid, userId, UserInterfaceType.WHATSAPP);                   break;
            case TAG_ME:            campaignBroker.setUserJoinTopic(campaignUid, userId, userResponse, UserInterfaceType.WHATSAPP); break;
            case SHARE_SEND:        campaignBroker.sendShareMessage(campaignUid, userId, userResponse, null, UserInterfaceType.WHATSAPP);   break;
            default:                log.info("No action possible for incoming user action {}, just returning message", action); break;
        }

        List<CampaignMessage> nextMsgs = campaignBroker.findCampaignMessage(campaignUid, action, null, UserInterfaceType.WHATSAPP);
        if (nextMsgs == null || nextMsgs.isEmpty()) {
            log.info("Could not find message from action, tracing from prior, prior uid: {}", priorMessageUid);
            nextMsgs = Collections.singletonList(campaignBroker.findCampaignMessage(campaignUid, priorMessageUid, action));
        }
        log.info("Next campaign messages found: {}", nextMsgs);

        List<String> messageTexts = nextMsgs.stream().map(CampaignMessage::getMessage).collect(Collectors.toList());
        LinkedHashMap<String, String> actionOptions = nextMsgs.stream().filter(CampaignMessage::hasMenuOptions).findFirst()
                .map(this::getMenuFromMessage).orElse(new LinkedHashMap<>());
        messageTexts.addAll(actionOptions.values());

        RequestDataType requestDataType = actionOptions.isEmpty() ? checkForNextUserInfo(userId) : RequestDataType.MENU_SELECTION;
        log.info("Found request data type for user: {}", requestDataType);
        if (USER_DATA_REQUESTS_WITH_MSGS.contains(requestDataType)) {
            messageTexts.addAll(dataRequestMessages(requestDataType, JpaEntityType.CAMPAIGN));
        }

        return EntityResponseToUser.builder()
                .entityType(JpaEntityType.CAMPAIGN)
                .entityUid(campaignUid)
                .requestDataType(requestDataType)
                .messages(messageTexts)
                .menu(actionOptions)
                .build();
    }

    private List<String> getResponseMessages(CampaignMessage message, Collection<String> menuOptionTexts) {
        List<String> messages = new ArrayList<>();
        if (message != null) {
            messages.add(message.getMessage());
            messages.addAll(menuOptionTexts);
        }
        return messages;
    }

    private LinkedHashMap<String, String> getMenuFromMessage(CampaignMessage message) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if (message != null) {
            List<CampaignActionType> options = new ArrayList<>(message.getNextMessages().values());
            log.info("Next menu options, should be in order: {}", options);
            IntStream.range(0, options.size()).forEach(i ->
                    map.put(options.get(i).toString(), (i + 1) + ". " + actionToMessage(options.get(i)))
            );
        }
        return map;
    }

    private String actionToMessage(CampaignActionType action) {
        return messageSource.getMessage("ussd.campaign." + action.toString().toLowerCase(),
                null, action.toString(), Locale.ENGLISH);
    }

    private RequestDataType checkForNextUserInfo(String userId) {
        User user = userManagementService.load(userId);
        log.info("Does user need to set name? : {}", userManagementService.needsToSetName(user, true));
        if (userManagementService.needsToSetName(user, true))
            return RequestDataType.USER_NAME;
        if (userManagementService.needsToSetProvince(user, true))
            return RequestDataType.LOCATION_PROVINCE_OKAY;
        else
            return RequestDataType.NONE;
    }

    private List<String> dataRequestMessages(RequestDataType dataType, JpaEntityType entityType) {
        List<String> messages = new ArrayList<>();
        final String baseKey = "whatsapp.user." + entityType.toString().toLowerCase() + ".prompt.";
        switch (dataType) {
            case USER_NAME:
                messages.add(messageSource.getMessage(baseKey + "name", null, Locale.ENGLISH));
                break;
            case LOCATION_PROVINCE_OKAY:
                messages.add(messageSource.getMessage(baseKey + "province", null, Locale.ENGLISH));
                break;
            case NONE:
                messages.add(messageSource.getMessage("ussd.campaign.exit_positive.generic", null, Locale.ENGLISH));
            default:
                log.info("Trying to extract messages for impossible data type request: {}", dataType);
                break;
        }
        log.info("Returning messages {} for data type {}", messages, dataType);
        return messages;
    }

}
