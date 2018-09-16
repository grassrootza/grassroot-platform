package za.org.grassroot.webapp.controller.rest.whatsapp;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j @RestController @Grassroot2RestController
@RequestMapping("/v2/api/whatsapp") @Api("/v2/api/whatsapp")
@PreAuthorize("hasRole('ROLE_SYSTEM_CALL')")
public class WhatsAppRelatedController extends BaseController {

    private final JwtService jwtService;
    private CampaignBroker campaignBroker;
    private MessageSource messageSource;

    @Autowired
    public WhatsAppRelatedController(UserManagementService userManagementService, PermissionBroker permissionBroker, JwtService jwtService) {
        super(userManagementService, permissionBroker);
        this.jwtService = jwtService;
    }

    @Autowired
    private void setCampaignBroker(CampaignBroker campaignBroker) {
        this.campaignBroker = campaignBroker;
    }

    @Autowired
    public void setMessageSource(@Qualifier("messageSource") MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @RequestMapping(value = "/user/id", method = RequestMethod.POST)
    public ResponseEntity fetchUserId(String msisdn) {
        User user = userManagementService.loadOrCreate(msisdn);
        return ResponseEntity.ok(user.getUid());
    }

    // for accessing standard user APIs, but is time limited, and does not include system roles
    // so in case overall microservice token is compromised, only some features can be called
    @RequestMapping(value = "/user/token", method = RequestMethod.POST)
    public ResponseEntity fetchRestrictedUserToken(String userId) {
        CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.MSGING_CLIENT);
        tokenRequest.addClaim(JwtService.USER_UID_KEY, userId);
        tokenRequest.addClaim(JwtService.SYSTEM_ROLE_KEY, BaseRoles.ROLE_FULL_USER);
        return ResponseEntity.ok(jwtService.createJwt(tokenRequest));
    }

    @RequestMapping(value = "/phrase/search", method = RequestMethod.POST)
    public ResponseEntity<PhraseSearchResponse> checkIfPhraseTriggersCampaign(@RequestParam String phrase, @RequestParam String userId) {
        Campaign campaign = campaignBroker.findCampaignByJoinWord(phrase, userId, UserInterfaceType.WHATSAPP);
        if (campaign != null) {
            // passing null as channel because reusing USSD, for now
            CampaignMessage message = campaignBroker.getOpeningMessage(campaign.getUid(), null, null, null);
            PhraseSearchResponse response = PhraseSearchResponse.builder()
                    .entityFound(true)
                    .entityType(JpaEntityType.CAMPAIGN)
                    .entityUid(campaign.getUid())
                    .responseMessages(getResponseMessages(message))
                    .responseMenu(getMenuFromMessage(message))
                    .build();
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.ok(PhraseSearchResponse.notFoundResponse());
        }
    }

    @RequestMapping(value = "/entity/respond/{entityType}/{entityUid}", method = RequestMethod.POST)
    public ResponseEntity<EntityResponseToUser> processFurtherResponseToEntity(@PathVariable JpaEntityType entityType,
                                                                               @PathVariable String entityUid,
                                                                               @RequestParam String userId,
                                                                               @RequestBody EntityReplyFromUser userReply) {
        EntityResponseToUser response;
        if (JpaEntityType.CAMPAIGN.equals(entityType)) {
            response = replyToCampaignMessage(userId, entityUid, userReply.getAuxProperties().get("PRIOR"),
                    CampaignActionType.valueOf(userReply.getMenuOptionPayload()), userReply.getUserMessage());
        } else {
            response = EntityResponseToUser.cannotRespond(entityType, entityUid);
        }

        return ResponseEntity.ok(response);
    }

    private EntityResponseToUser replyToCampaignMessage(@RequestParam String userId,
                                                          @RequestParam String campaignUid,
                                                          @RequestParam String priorMessageUid,
                                                          @RequestParam CampaignActionType action,
                                                          @RequestParam String userResponse) {
        switch (action) {
            case JOIN_GROUP:
                campaignBroker.addUserToCampaignMasterGroup(campaignUid, userId, UserInterfaceType.WHATSAPP);
                break;
            case SIGN_PETITION:
                campaignBroker.signPetition(campaignUid, userId, UserInterfaceType.WHATSAPP);
                break;
            case TAG_ME:
                campaignBroker.setUserJoinTopic(campaignUid, userId, userResponse, UserInterfaceType.WHATSAPP);
                break;
            case SHARE_SEND:
                campaignBroker.sendShareMessage(campaignUid, userId, userResponse, null, UserInterfaceType.WHATSAPP);
                break;
            default:
                log.info("No action possible for incoming user action {}, just returning message", action);
                break;
        }

        List<CampaignMessage> nextMsgs = campaignBroker.findCampaignMessage(campaignUid, action, null);
        if (nextMsgs == null || nextMsgs.isEmpty()) {
            nextMsgs = Collections.singletonList(campaignBroker.findCampaignMessage(campaignUid, priorMessageUid, action));
        }

        List<String> messageTexts = nextMsgs.stream().map(CampaignMessage::getMessage).collect(Collectors.toList());
        LinkedHashMap<String, String> actionOptions = new LinkedHashMap<>();
        nextMsgs.stream().filter(CampaignMessage::hasMenuOptions).findFirst().ifPresent(message ->
            message.getNextMessages().forEach((key, value) -> actionOptions.put(key, key))
        );

        return EntityResponseToUser.builder()
                .entityType(JpaEntityType.CAMPAIGN)
                .entityUid(campaignUid)
                .messages(messageTexts)
                .menu(actionOptions)
                .build();
    }

    private List<String> getResponseMessages(CampaignMessage message) {
        List<String> messages = new ArrayList<>();
        messages.add(message.getMessage());
        message.getNextMessages().forEach((msgUid, action) -> messages.add(actionToMessage(action)));
        return messages;
    }

    private LinkedHashMap<String, String> getMenuFromMessage(CampaignMessage message) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        message.getNextMessages().forEach((msgUid, action) -> map.put(action.toString(), actionToMessage(action)));
        return map;
    }

    private String actionToMessage(CampaignActionType action) {
        return messageSource.getMessage("ussd.campaign." + action.toString().toLowerCase(),
                null, action.toString(), Locale.ENGLISH);
    }

}
