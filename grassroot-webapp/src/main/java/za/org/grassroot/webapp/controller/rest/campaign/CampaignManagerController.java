package za.org.grassroot.webapp.controller.rest.campaign;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.campaign.CampaignBroker;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RestController @Grassroot2RestController
@Api("/api/campaign/manage") @Slf4j
@RequestMapping(value = "/api/campaign/manage")
public class CampaignManagerController extends BaseRestController {

    private final CampaignBroker campaignBroker;
    private final UserManagementService userManager;
    private final static String SA_TIME_ZONE = "Africa/Johannesburg";
    private MessageSource messageSource;

    @Autowired
    public CampaignManagerController(JwtService jwtService, CampaignBroker campaignBroker, UserManagementService userManager, @Qualifier("messageSource")
        MessageSource messageSource) {
        super(jwtService, userManager);
        this.campaignBroker = campaignBroker;
        this.userManager = userManager;
        this.messageSource = messageSource;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ApiOperation(value = "List user's campaigns", notes = "Lists the campaigns a user has created")
    public ResponseEntity<List<CampaignViewDTO>> fetchCampaignsManagedByUser(HttpServletRequest request,
                                                                       @RequestParam(required = false) String userUid) {
        return ResponseEntity.ok(CampaignWebUtil.createCampaignViewDtoList(campaignBroker.getCampaignsCreatedByUser(
                        userUid == null ? getUserIdFromRequest(request) : userUid)));
    }

    @RequestMapping(value = "/create" , method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "create campaign", notes = "create a campaign using given values")
    public ResponseEntity<ResponseWrapper> createCampaign(@Valid @RequestBody CreateCampaignRequest createCampaignRequest, BindingResult bindingResult){
        if (bindingResult.hasErrors()) {
            return RestUtil.errorResponseWithData(RestMessage.CAMPAIGN_CREATION_INVALID_INPUT, getFieldValidationErrors(bindingResult.getFieldErrors()));
        }
        if(campaignBroker.getCampaignDetailsByCode(createCampaignRequest.getCode().trim()) != null){
            return RestUtil.errorResponse(RestMessage.CAMPAIGN_WITH_SAME_CODE_EXIST);
        }
        List<String> tagList = null;
        if(createCampaignRequest.getTags() != null && !createCampaignRequest.getTags().isEmpty()){
            tagList = Collections.list(Collections.enumeration(createCampaignRequest.getTags()));
        }
        log.info("finished processing tags, value = ");
        LocalDate firstDate = LocalDate.parse(createCampaignRequest.getStartDate());
        Instant campaignStartDate = firstDate.atStartOfDay(ZoneId.of(SA_TIME_ZONE)).toInstant();

        LocalDate secondDate = LocalDate.parse(createCampaignRequest.getEndDate());
        Instant campaignEndDate = secondDate.atStartOfDay(ZoneId.of(SA_TIME_ZONE)).toInstant();

        Campaign campaign = campaignBroker.createCampaign(createCampaignRequest.getName(), createCampaignRequest.getCode(),
                createCampaignRequest.getDescription(),
                createCampaignRequest.getUserUid(),
                campaignStartDate,
                campaignEndDate,
                tagList,
                createCampaignRequest.getType(), createCampaignRequest.getUrl());
        if(!StringUtils.isEmpty(createCampaignRequest.getGroupUid())){
            campaign = campaignBroker.linkCampaignToMasterGroup(campaign.getCampaignCode(), createCampaignRequest.getGroupUid(), createCampaignRequest.getUserUid());
        }
        else if(!StringUtils.isEmpty(createCampaignRequest.getGroupName())){
            campaign = campaignBroker.createMasterGroupForCampaignAndLinkCampaign(campaign.getCampaignCode(), createCampaignRequest.getGroupName(), createCampaignRequest.getUserUid());
        }
        return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_CREATED,CampaignWebUtil.createCampaignViewDTO(campaign));
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

    @RequestMapping(value ="/add/message", method = RequestMethod.POST,  consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "add message to campaign", notes = "add message to a campaign")
    public ResponseEntity<ResponseWrapper> addCampaignMessage(@Valid @RequestBody CreateCampaignMessageRequest createCampaignMessageRequest,
                                     BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return RestUtil.errorResponseWithData(RestMessage.CAMPAIGN_MESSAGE_CREATION_INVALID_INPUT, getFieldValidationErrors(bindingResult.getFieldErrors()));
        }
        List<String> tagList = null;
        if (createCampaignMessageRequest.getTags() != null && !createCampaignMessageRequest.getTags().isEmpty()) {
            tagList = Collections.list(Collections.enumeration(createCampaignMessageRequest.getTags()));
        }
        User user = userManager.load(createCampaignMessageRequest.getUserUid());
        Campaign campaign = campaignBroker.addCampaignMessage(createCampaignMessageRequest.getCampaignCode(), createCampaignMessageRequest.getMessage(),
                new Locale(createCampaignMessageRequest.getLanguageCode()), createCampaignMessageRequest.getAssignmentType(), createCampaignMessageRequest.getChannelType(), user, tagList);
        if(campaign != null) {
            return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_MESSAGE_ADDED, CampaignWebUtil.createCampaignViewDTO(campaign));
        }
        return RestUtil.messageOkayResponse(RestMessage.CAMPAIGN_NOT_FOUND);
    }


    @RequestMapping(value ="/add/message/action", method = RequestMethod.POST,  consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "add user action on a message", notes = "add user action to a message")
    public ResponseEntity<ResponseWrapper> addActionOnMessage(@Valid @RequestBody CreateCampaignMessageActionRequest createCampaignMessageActionRequest,
                                                              BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return RestUtil.errorResponseWithData(RestMessage.CAMPAIGN_MESSAGE_ACTION_CREATION_INVALID_INPUT, getFieldValidationErrors(bindingResult.getFieldErrors()));
        }
        User user = userManager.load(createCampaignMessageActionRequest.getUserUid());
        Campaign campaign = campaignBroker.addActionToCampaignMessage(createCampaignMessageActionRequest.getCampaignCode(),createCampaignMessageActionRequest.getMessageUid(),createCampaignMessageActionRequest.getAction(),createCampaignMessageActionRequest.getActionMessage().getMessage()
        ,new Locale(createCampaignMessageActionRequest.getActionMessage().getLanguageCode()),createCampaignMessageActionRequest.getActionMessage().getAssignmentType(), createCampaignMessageActionRequest.getActionMessage().getChannelType(),user,createCampaignMessageActionRequest.getActionMessage().getTags());
        if(campaign != null) {
            return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_MESSAGE_ACTION_ADDED, CampaignWebUtil.createCampaignViewDTO(campaign));
        }
        return RestUtil.messageOkayResponse(RestMessage.CAMPAIGN_NOT_FOUND);
    }

    @RequestMapping(value="/view", method = RequestMethod.GET)
    @ApiOperation(value = "view campaign details", notes = "view campaign details")
    public ResponseEntity<ResponseWrapper> viewCampaign(@RequestParam(value = "code",required = false)String code,
                                                        @RequestParam(value = "name",required = false)String name){
        Campaign campaign = null;
        if(StringUtils.isEmpty(code) && StringUtils.isEmpty(name)){
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_INPUT);
        }
        if(!StringUtils.isEmpty(code)){
            campaign =  campaignBroker.getCampaignDetailsByCode(code);
        }
        if(campaign == null && !StringUtils.isEmpty(name)){
            campaign = campaignBroker.getCampaignDetailsByName(name);
        }
        if(campaign != null){
            return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_FOUND,CampaignWebUtil.createCampaignViewDTO(campaign));
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
