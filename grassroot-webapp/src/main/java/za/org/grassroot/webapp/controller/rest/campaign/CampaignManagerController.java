package za.org.grassroot.webapp.controller.rest.campaign;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.CampaignMessageDTO;
import za.org.grassroot.services.exception.CampaignCodeTakenException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.exception.RestValidationMessage;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.CampaignViewDTO;
import za.org.grassroot.webapp.model.rest.wrappers.CreateCampaignMessageActionRequest;
import za.org.grassroot.webapp.model.rest.wrappers.CreateCampaignMessageRequest;
import za.org.grassroot.webapp.model.rest.wrappers.CreateCampaignRequest;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.Instant;
import java.util.*;

@RestController @Grassroot2RestController
@Api("/api/campaign/manage") @Slf4j
@RequestMapping(value = "/api/campaign/manage")
public class CampaignManagerController extends BaseRestController {

    private final CampaignBroker campaignBroker;
    private final UserManagementService userManager;
    private final GroupBroker groupBroker;
    private MessageSource messageSource;

    @Autowired
    public CampaignManagerController(JwtService jwtService, CampaignBroker campaignBroker, UserManagementService userManager, GroupBroker groupBroker, @Qualifier("messageSource")
            MessageSource messageSource) {
        super(jwtService, userManager);
        this.campaignBroker = campaignBroker;
        this.userManager = userManager;
        this.groupBroker = groupBroker;
        this.messageSource = messageSource;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ApiOperation(value = "List user's campaigns", notes = "Lists the campaigns a user has created")
    public ResponseEntity<List<CampaignViewDTO>> fetchCampaignsManagedByUser(HttpServletRequest request,
                                                                       @RequestParam(required = false) String userUid) {
        return ResponseEntity.ok(CampaignWebUtil.createCampaignViewDtoList(campaignBroker.getCampaignsCreatedByUser(
                        userUid == null ? getUserIdFromRequest(request) : userUid)));
    }

    @RequestMapping(value = "/list/group", method = RequestMethod.GET)
    @ApiOperation(value = "List  campaigns linked to group", notes = "Lists the campaigns linked to specific group")
    public ResponseEntity<List<CampaignViewDTO>> fetchCampaignsForGroup(String groupUid) {

        return ResponseEntity.ok(CampaignWebUtil.createCampaignViewDtoList(
                campaignBroker.getCampaignsCreatedLinkedToGroup(groupUid)
        ));
    }

    @RequestMapping(value = "/fetch/{campaignUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch a specific campaign", notes = "Fetches a campaign with basic info")
    public ResponseEntity fetchCampaign(HttpServletRequest request, @PathVariable String campaignUid) {
        try {
            Campaign campaign = campaignBroker.load(getUserIdFromRequest(request), campaignUid);
            return ResponseEntity.ok(CampaignWebUtil.createCampaignViewDTO(campaign));
        } catch (AccessDeniedException e) {
            return RestUtil.errorResponse(RestMessage.USER_NOT_CAMPAIGN_MANAGER);
        }
    }

    @RequestMapping(value = "/create" , method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "create campaign", notes = "create a campaign using given values")
    public ResponseEntity createCampaign(@Valid @RequestBody CreateCampaignRequest createCampaignRequest,
                                         BindingResult bindingResult, HttpServletRequest request){
        String userUid = getUserIdFromRequest(request);

        if (bindingResult.hasErrors()) {
            return RestUtil.errorResponseWithData(RestMessage.CAMPAIGN_CREATION_INVALID_INPUT, getFieldValidationErrors(bindingResult.getFieldErrors()));
        }
        if (createCampaignRequest.hasNoGroup()) {
            return RestUtil.errorResponse(RestMessage.CAMPAIGN_MISSING_MASTER_GROUP);
        }

        List<String> tagList = null;
        if(createCampaignRequest.getTags() != null && !createCampaignRequest.getTags().isEmpty()){
            tagList = Collections.list(Collections.enumeration(createCampaignRequest.getTags()));
        }
        log.info("finished processing tags, value = {}", tagList);

        Instant campaignStartDate = Instant.ofEpochMilli(createCampaignRequest.getStartDateEpochMillis());
        Instant campaignEndDate = Instant.ofEpochMilli(createCampaignRequest.getEndDateEpochMillis());

        String masterGroupUid = createCampaignRequest.getGroupUid();
        if (StringUtils.isEmpty(masterGroupUid)) {
            User groupCreator = userManager.load(userUid);
            MembershipInfo creator = new MembershipInfo(groupCreator, groupCreator.getName(), BaseRoles.ROLE_GROUP_ORGANIZER, null);
            Group createdGroup = groupBroker.create(userUid, createCampaignRequest.getGroupName(), null, Collections.singleton(creator),
                    GroupPermissionTemplate.CLOSED_GROUP, null, null, false, false);
            masterGroupUid = createdGroup.getUid();
        }

        Campaign campaign = campaignBroker.create(createCampaignRequest.getName(),
                createCampaignRequest.getCode(),
                createCampaignRequest.getDescription(),
                userUid,
                masterGroupUid,
                campaignStartDate,
                campaignEndDate,
                tagList,
                createCampaignRequest.getType(),
                createCampaignRequest.getUrl());

        return ResponseEntity.ok(CampaignWebUtil.createCampaignViewDTO(campaign));
    }

    @ExceptionHandler(CampaignCodeTakenException.class)
    public ResponseEntity codeTaken() {
        return RestUtil.errorResponse(RestMessage.CAMPAIGN_WITH_SAME_CODE_EXIST);
    }

    @RequestMapping(value = "/codes/list/active", method = RequestMethod.GET)
    @ApiOperation(value = "List all the active campaign codes (to prevent duplicates")
    public ResponseEntity<Set<String>> listJoinCodes() {
        return ResponseEntity.ok(campaignBroker.getActiveCampaignCodes());
    }

    @RequestMapping(value ="/add/tag", method = RequestMethod.GET)
    @ApiOperation(value = "add tag to campaign", notes = "add tag to a campaign")
    public ResponseEntity<ResponseWrapper> addCampaignTag(@RequestParam(value="campaignCode", required = true) String campaignCode,
                                 @RequestParam(value="tag", required = true) String tag) {
        List<String> tagList = new ArrayList<>();
        tagList.add(tag);
        Campaign campaign = campaignBroker.addCampaignTags(campaignCode, tagList);
        if(campaign != null) {
            return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_TAG_ADDED, CampaignWebUtil.createCampaignViewDTO(campaign));
        }
        return RestUtil.messageOkayResponse(RestMessage.CAMPAIGN_NOT_FOUND);
    }

    @RequestMapping(value = "/messages/set/{campaignUid}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "add a set of messages to a campaign")
    public ResponseEntity addCampaignMessages(HttpServletRequest request, @PathVariable String campaignUid,
                                              @Valid @RequestBody Set<CampaignMessageDTO> campaignMessages, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return RestUtil.errorResponseWithData(RestMessage.CAMPAIGN_MESSAGE_CREATION_INVALID_INPUT, getFieldValidationErrors(bindingResult.getFieldErrors()));
        }

        log.info("campaign messages received: {}", campaignMessages);
        Campaign updatedCampaign = campaignBroker.setCampaignMessages(getUserIdFromRequest(request), campaignUid, campaignMessages);
        return ResponseEntity.ok(CampaignWebUtil.createCampaignViewDTO(updatedCampaign));
    }

    @RequestMapping(value ="/messages/add/{campaignUid}", method = RequestMethod.POST,  consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "add message to campaign", notes = "add message to a campaign")
    public ResponseEntity<ResponseWrapper> addCampaignMessage(HttpServletRequest request,
                                                              @PathVariable String campaignUid,
                                                              @Valid @RequestBody CreateCampaignMessageRequest createRequest,
                                                              BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return RestUtil.errorResponseWithData(RestMessage.CAMPAIGN_MESSAGE_CREATION_INVALID_INPUT, getFieldValidationErrors(bindingResult.getFieldErrors()));
        }
        List<String> tagList = null;
        if (createRequest.getTags() != null && !createRequest.getTags().isEmpty()) {
            tagList = Collections.list(Collections.enumeration(createRequest.getTags()));
        }
        User user = getUserFromRequest(request);
        Campaign campaign = campaignBroker.addCampaignMessage(campaignUid, createRequest.getMessage(),
                createRequest.getLanguage(), createRequest.getAssignmentType(), createRequest.getChannelType(), user, tagList);

        if(campaign != null) {
            return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_MESSAGE_ADDED, CampaignWebUtil.createCampaignViewDTO(campaign));
        }
        return RestUtil.messageOkayResponse(RestMessage.CAMPAIGN_NOT_FOUND);
    }

    @RequestMapping(value ="/messages/action/add/{campaignUid}", method = RequestMethod.POST,  consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "add user action on a message", notes = "add user action to a message")
    public ResponseEntity<ResponseWrapper> addActionOnMessage(@PathVariable String campaignUid,
                                                              @Valid @RequestBody CreateCampaignMessageActionRequest createCampaignMessageActionRequest,
                                                              BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return RestUtil.errorResponseWithData(RestMessage.CAMPAIGN_MESSAGE_ACTION_CREATION_INVALID_INPUT, getFieldValidationErrors(bindingResult.getFieldErrors()));
        }
        User user = userManager.load(createCampaignMessageActionRequest.getUserUid());
        Campaign campaign = campaignBroker.addActionToCampaignMessage(createCampaignMessageActionRequest.getCampaignCode(),createCampaignMessageActionRequest.getMessageUid(),createCampaignMessageActionRequest.getAction(),createCampaignMessageActionRequest.getActionMessage().getMessage()
        ,createCampaignMessageActionRequest.getActionMessage().getLanguage(),createCampaignMessageActionRequest.getActionMessage().getAssignmentType(), createCampaignMessageActionRequest.getActionMessage().getChannelType(),user,createCampaignMessageActionRequest.getActionMessage().getTags());
        if(campaign != null) {
            return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_MESSAGE_ACTION_ADDED, CampaignWebUtil.createCampaignViewDTO(campaign));
        }
        return RestUtil.messageOkayResponse(RestMessage.CAMPAIGN_NOT_FOUND);
    }

    private List<RestValidationMessage> getFieldValidationErrors(List<FieldError> errors) {
        List<RestValidationMessage> fieldValidationErrorList = new ArrayList<>();
        for (FieldError field : errors) {
            fieldValidationErrorList
                    .add(new RestValidationMessage(field.getField(),getMessage(field.getDefaultMessage())));
        }
        return fieldValidationErrorList;
    }

    private String getMessage(String msgKey) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage("web." + msgKey, null, locale);
    }
}
