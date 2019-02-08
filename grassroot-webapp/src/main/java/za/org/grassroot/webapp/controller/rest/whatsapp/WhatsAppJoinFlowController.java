package za.org.grassroot.webapp.controller.rest.whatsapp;

import com.google.common.base.Enums;
import io.swagger.annotations.Api;
import liquibase.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.StandardRole;
import za.org.grassroot.core.domain.UidIdentifiable;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j @RestController @Grassroot2RestController
@RequestMapping("/v2/api/whatsapp") @Api("/v2/api/whatsapp")
@PreAuthorize("hasRole('ROLE_SYSTEM_CALL')")
public class WhatsAppJoinFlowController extends BaseController {

    private final List<RequestDataType> USER_DATA_REQUESTS_WITH_MSGS = Arrays.asList(
            RequestDataType.USER_NAME, RequestDataType.LOCATION_PROVINCE_OKAY, RequestDataType.LOCATION_GPS_REQUIRED);

    private final List<CampaignActionType> NO_RESPONSE_CAMPAIGN_ACTIONS = Arrays.asList(
            CampaignActionType.SHARE_SEND, CampaignActionType.RECORD_MEDIA);

    private final JwtService jwtService;
    private final AsyncUserLogger userLogger;

    private CampaignBroker campaignBroker;
    private GroupBroker groupBroker;
    private GroupQueryBroker groupQueryBroker;
    private LocationInfoBroker locationInfoBroker;
    private MessageSource messageSource;

    @Autowired
    public WhatsAppJoinFlowController(UserManagementService userManagementService, PermissionBroker permissionBroker, JwtService jwtService, AsyncUserLogger userLogger) {
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
    public void setGroupQueryBroker(GroupQueryBroker groupQueryBroker) {
        this.groupQueryBroker = groupQueryBroker;
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
        log.debug("South African number? : {}", PhoneNumberUtil.isPhoneNumberSouthAfrican(msisdn));
        User user = userManagementService.loadOrCreateUser("+" + msisdn, UserInterfaceType.WHATSAPP);
        userLogger.recordUserSession(user.getUid(), UserInterfaceType.WHATSAPP);
        return ResponseEntity.ok(user.getUid());
    }

    // for accessing standard user APIs, but is time limited, and does not include system roles
    // so in case overall microservice token is compromised, only some features can be called
    @RequestMapping(value = "/user/token", method = RequestMethod.POST)
    public ResponseEntity fetchRestrictedUserToken(@RequestParam String userId) {
        User user = userManagementService.load(userId);
        if (user == null)
            throw new AccessDeniedException("Only existing / created users can have a token");

        CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.MSGING_CLIENT);
        tokenRequest.addClaim(JwtService.USER_UID_KEY, userId);
        tokenRequest.addClaim(JwtService.SYSTEM_ROLE_KEY, StandardRole.ROLE_FULL_USER.name());
        userLogger.logUserLogin(userId, UserInterfaceType.WHATSAPP); // keep an eye on how often this gets called (may become redundant)
        return ResponseEntity.ok(jwtService.createJwt(tokenRequest));
    }

    @RequestMapping(value = "/phrase/search", method = RequestMethod.POST)
    public ResponseEntity<PhraseSearchResponse> checkCampaignGroupPhrase(@RequestParam String phrase,
                                                                         @RequestParam String userId,
                                                                         @RequestParam boolean broadSearch) {
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
            response = broadSearch ? broadPhraseSearch(user, phrase) : PhraseSearchResponse.notFoundResponse();
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

    private PhraseSearchResponse broadPhraseSearch(User user, String phrase) {
        List<Group> candidateGroups = groupQueryBroker.findPublicGroups(user.getUid(), phrase, null, true);
        List<Campaign> campaignList = campaignBroker.broadSearchForCampaign(user.getUid(), phrase);
        log.info("found {} possible groups and {} possible campaigns for phrase '{}'", candidateGroups.size(), campaignList.size(), phrase);

        if (candidateGroups.isEmpty() && campaignList.isEmpty())
            return PhraseSearchResponse.notFoundResponse();

        List<UidIdentifiable> candidateEntities = Stream.concat(campaignList.stream().map(campaign -> (UidIdentifiable) campaign),
                candidateGroups.stream().map(group -> (UidIdentifiable) group)).collect(Collectors.toList());

        if (candidateEntities.isEmpty())
            return PhraseSearchResponse.notFoundResponse();

        final boolean singleResult = candidateEntities.size() == 1;
        List<String> messages = new ArrayList<>();

        Locale locale = user.getLocale() == null ? Locale.ENGLISH : user.getLocale();
        final String responseKey = "whatsapp.phrase.results." + (singleResult ? "single" : "multiple");
        final String[] fields = singleResult
                ? new String[] { candidateEntities.get(0).getJpaEntityType().toString().toLowerCase(), candidateEntities.get(0).getName() }
                : new String[] {};
        messages.add(messageSource.getMessage(responseKey, fields, locale));

        LinkedHashMap<JpaEntityType, String> possibleEntities = new LinkedHashMap<>();
        LinkedHashMap<String, String> responseMenu = new LinkedHashMap<>();

        // only need menu if multiple entities to select, else will just process yes / no
        if (!singleResult) {
            IntStream.range(0, candidateEntities.size()).forEach(i -> {
                UidIdentifiable entity = candidateEntities.get(i);
                messages.add((i + 1) + ". " + entity.getName());
                possibleEntities.put(entity.getJpaEntityType(), entity.getUid());
                responseMenu.put(entity.getJpaEntityType() + "::" + entity.getUid(), entity.getName());
            });
        } else {
            // so controller can send back on confirmation
            UidIdentifiable likelyEntity = candidateEntities.get(0);
            possibleEntities.put(likelyEntity.getJpaEntityType(), likelyEntity.getUid());
        }

        return PhraseSearchResponse.builder()
                .entityFound(false)
                .responseMessages(messages)
                .responseMenu(responseMenu)
                .possibleEntities(possibleEntities)
                .build();
    }

    @RequestMapping(value = "/entity/select/{entityType}/{entityUid}", method = RequestMethod.POST)
    public ResponseEntity<PhraseSearchResponse> selectEntityToJoin(@PathVariable JpaEntityType entityType,
                                                                   @PathVariable String entityUid,
                                                                   @RequestParam String userId) {
        User user = userManagementService.load(userId);
        if (JpaEntityType.CAMPAIGN.equals(entityType)) {
            campaignBroker.recordEngagement(entityUid, userId, UserInterfaceType.WHATSAPP, "From search");
            return ResponseEntity.ok(campaignResponse(user, campaignBroker.load(entityUid)));
        } else {
            return ResponseEntity.ok(groupResponse(user, groupBroker.load(entityUid)));
        }
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
            CampaignActionType actionUserWishesToTake = StringUtils.isEmpty(userReply.getMenuOptionPayload()) ? null
                    : Enums.getIfPresent(CampaignActionType.class, userReply.getMenuOptionPayload()).orNull();
            response = replyToCampaignMessage(userId, entityUid, userReply.getAuxProperties().get("PRIOR"), actionUserWishesToTake, userReply.getUserMessage());
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

        RequestDataType nextRequestType;
        List<String> nextMessages = new ArrayList<>();
        LinkedHashMap<String, String> nextOptions = new LinkedHashMap<>();

        if (JpaEntityType.CAMPAIGN.equals(entityType)) {
            nextRequestType = handleEndOfFlowStdRequests(userId, entityId, null, nextMessages, nextOptions);
        } else {
            nextRequestType = checkForNextUserInfo(userId);
            nextMessages.addAll(dataRequestMessages(nextRequestType, entityType));
        }

        return EntityResponseToUser.builder()
                .entityType(entityType)
                .entityUid(entityId)
                .messages(nextMessages)
                .menu(nextOptions.isEmpty() ? null : nextOptions)
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

    // NB: 'action' here is what the user just did
    private EntityResponseToUser replyToCampaignMessage(String userId,
                                                        String campaignUid,
                                                        String priorMessageUid,
                                                        CampaignActionType action,
                                                        String userResponse) {
        log.info("### Initiating campaign reply sequence message for action type {}, user response {}, campaign ID: {}", action, userResponse, campaignUid);
        if (action == null) {
            log.error("Null action type received, curious, return empty response");
            return EntityResponseToUser.cannotRespond(JpaEntityType.CAMPAIGN, campaignUid);
        }

        // note: this action is what the user selected based on prior menu / prompt, i.e., JOIN_GROUP does not mean ask them if they want to join,
        // but means they have chosen to join, and sequence is roughly as it is usually present to user
        switch (action) {
            case SIGN_PETITION:     campaignBroker.signPetition(campaignUid, userId, UserInterfaceType.WHATSAPP);                   break;
            case JOIN_GROUP:        campaignBroker.addUserToCampaignMasterGroup(campaignUid, userId, UserInterfaceType.WHATSAPP);   break;
            case TAG_ME:            campaignBroker.setUserJoinTopic(campaignUid, userId, userResponse, UserInterfaceType.WHATSAPP); break;
            case SHARE_SEND:        campaignBroker.sendShareMessage(campaignUid, userId, userResponse, null, UserInterfaceType.WHATSAPP);   break;
            case RECORD_MEDIA:      campaignBroker.recordUserSentMedia(campaignUid, userId, UserInterfaceType.WHATSAPP); break;
            default:                log.info("No action possible for incoming user action {}, just returning message", action); break;
        }

        // so this message must be set as the one for _after_ the user has decided to take the action (NB).
        List<CampaignMessage> nextMsgs = new ArrayList<>();
        // todo : move lower logic into else branch
        if (!NO_RESPONSE_CAMPAIGN_ACTIONS.contains(action)) {
            nextMsgs = campaignBroker.findCampaignMessage(campaignUid, action, null, UserInterfaceType.WHATSAPP);
            if (nextMsgs == null || nextMsgs.isEmpty()) {
                log.info("Could not find message from action, tracing from prior, prior uid: {}", priorMessageUid);
                nextMsgs = Collections.singletonList(campaignBroker.findCampaignMessage(campaignUid, priorMessageUid, action));
            }
            log.info("Next campaign messages found: {}", nextMsgs);
        }

        List<String> messageTexts = nextMsgs.stream().map(CampaignMessage::getMessage).collect(Collectors.toList());
        LinkedHashMap<String, String> actionOptions = nextMsgs.stream().filter(CampaignMessage::hasMenuOptions).findFirst()
                .map(this::getMenuFromMessage).orElse(new LinkedHashMap<>());
        messageTexts.addAll(actionOptions.values());

        RequestDataType requestDataType = RequestDataType.NONE;

        // if we have no menu, then cycle through last questions : note, really need to make this cleaner, and also
        // think of how to make it configurable (probably via next msg logic, but with a default skipping if user already set)
        log.info("No menu with options left, so see what can come next");
        if (actionOptions.isEmpty()) {
            requestDataType = handleEndOfFlowStdRequests(userId, campaignUid, action, messageTexts, actionOptions);
        }

        return EntityResponseToUser.builder()
                .entityType(JpaEntityType.CAMPAIGN)
                .entityUid(campaignUid)
                .requestDataType(requestDataType)
                .messages(messageTexts)
                .menu(actionOptions)
                .build();
    }

    private RequestDataType handleEndOfFlowStdRequests(String userId, String campaignUid, CampaignActionType priorAction,
                                                       List<String> messageTexts, LinkedHashMap<String, String> actionOptions) {
        RequestDataType requestDataType = checkForNextUserInfo(userId);
        log.info("Request data type for user: {}", requestDataType);
        if (USER_DATA_REQUESTS_WITH_MSGS.contains(requestDataType)) {
            messageTexts.addAll(dataRequestMessages(requestDataType, JpaEntityType.CAMPAIGN));
        } else {
            log.info("End of campaign flow, nothing to ask user, check for a share or media prompty message");
            CampaignMessage finalFlowMessage = showUserCloseOffPrompt(campaignUid, userId, priorAction);
            log.info("Returned end-of-flow message: {}", finalFlowMessage);
            if (finalFlowMessage != null) {
                messageTexts.add(finalFlowMessage.getMessage());
                CampaignActionType nextAction = getNextActionForPrompt(finalFlowMessage);
                actionOptions.put(nextAction.toString(), ""); // since we need to know the action
                requestDataType = RequestDataType.FREE_FORM_OR_MEDIA;
            } else {
                log.info("No data type to request, no final message, so close off with campaign defined positive exit or the generic");
                List<CampaignMessage> positiveExitIfExists = campaignBroker.findCampaignMessage(campaignUid, CampaignActionType.EXIT_POSITIVE, null, UserInterfaceType.WHATSAPP);
                List<String> responseMessages = positiveExitIfExists != null && !positiveExitIfExists.isEmpty() ?
                        Collections.singletonList(positiveExitIfExists.get(0).getMessage()) : dataRequestMessages(RequestDataType.NONE, JpaEntityType.CAMPAIGN);
                messageTexts.addAll(responseMessages);
            }
        }
        return requestDataType;
    }

    private CampaignMessage showUserCloseOffPrompt(String campaignUid, String userUid, CampaignActionType priorAction) {
        final boolean wasLastActionShare = CampaignActionType.SHARE_SEND.equals(priorAction); // since the send is async, and don't want to block to wait
        final boolean wasLastActionMedia = CampaignActionType.RECORD_MEDIA.equals(priorAction);

        log.info("Looking for close off prompt, was last action a share? {}, or media? {}", wasLastActionShare, wasLastActionMedia);

        CampaignMessage msg = wasLastActionShare ? null : showUserSharePrompt(campaignUid, userUid);
        log.info("Is there a share prompt message ? {}", msg);

        return msg != null ? msg : wasLastActionMedia ? null : showUserMediaRecordingPrompt(campaignUid, userUid);
    }

    private CampaignMessage showUserSharePrompt(String campaignUid, String userUid) {
        Campaign campaign = campaignBroker.load(campaignUid);

        if (!campaign.isOutboundTextEnabled() || campaign.outboundBudgetLeft() == 0)
            return null;

        log.info("Checking for sharing prompt or message, has user shared? : {}", campaignBroker.hasUserShared(campaignUid, userUid));
        if (campaignBroker.hasUserShared(campaignUid, userUid))
            return null;

        List<CampaignMessage> nextMsgs = campaignBroker.findCampaignMessage(campaignUid, CampaignActionType.SHARE_PROMPT, null, UserInterfaceType.WHATSAPP);
        return (nextMsgs == null || nextMsgs.isEmpty()) ? null : nextMsgs.get(0);
    }

    private CampaignMessage showUserMediaRecordingPrompt(String campaignUid, String userUid) {
        log.info("Going to ask user for media, have they sent prior? : {}", campaignBroker.hasUserSentMedia(campaignUid, userUid));
//        if (campaignBroker.hasUserSentMedia(campaignUid, userUid))
//            return null;

        List<CampaignMessage> recordingPrompt = campaignBroker.findCampaignMessage(campaignUid, CampaignActionType.MEDIA_PROMPT, null, UserInterfaceType.WHATSAPP);
        log.info("Found a prompt for media or recording? : {}", recordingPrompt);
        return (recordingPrompt == null || recordingPrompt.isEmpty()) ? null : recordingPrompt.get(0);
    }

    private CampaignActionType getNextActionForPrompt(CampaignMessage finalFlowPrompt) {
        return CampaignActionType.MEDIA_PROMPT.equals(finalFlowPrompt.getActionType()) ? CampaignActionType.RECORD_MEDIA
                : CampaignActionType.SHARE_PROMPT.equals(finalFlowPrompt.getActionType()) ? CampaignActionType.SHARE_SEND
                    : CampaignActionType.EXIT_POSITIVE;
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
