package za.org.grassroot.webapp.controller.rest.campaign;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.web.CampaignMessageWrapper;
import za.org.grassroot.webapp.model.web.CampaignWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RestController @Grassroot2RestController
@Api("/api/group/modify")
@RequestMapping(value = "/api/campaign/manage")
public class CampaignManagerController {

    private static final Logger logger = LoggerFactory.getLogger(CampaignManagerController.class);

    private final CampaignBroker campaignBroker;
    private final UserManagementService userManager;
    private final static String SA_TIME_ZONE = "Africa/Johannesburg";

    @Autowired
    public CampaignManagerController(CampaignBroker campaignBroker, UserManagementService userManager) {
        this.campaignBroker = campaignBroker;
        this.userManager = userManager;
    }

    @RequestMapping(value = "/list/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "List user's campaigns", notes = "Lists the campaigns a user has created")
    public ResponseEntity<ResponseWrapper> fetchCampaignsManagedByUser(@PathVariable String userUid) {
        return RestUtil.okayResponseWithData(RestMessage.USER_ACTIVITIES,
                CampaignWebUtil.createCampaignWrapperList(campaignBroker.getCampaignsCreatedByUser(userUid)));
    }

    @RequestMapping(value = "/create" , method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "create campaign", notes = "create a campaign using given values")
    public ResponseEntity<ResponseWrapper> createCampaign(@RequestBody CampaignWrapper campaignWrapper){
        if(campaignBroker.getCampaignDetailsByCode(campaignWrapper.getCode().trim()) != null){
            return RestUtil.errorResponse(RestMessage.CAMPAIGN_WITH_SAME_CODE_EXIST);
        }
        List<String> tagList = null;
        if(campaignWrapper.getTags() != null && !campaignWrapper.getTags().isEmpty()){
            tagList = Collections.list(Collections.enumeration(campaignWrapper.getTags()));
        }
        LocalDate firstDate = LocalDate.parse(campaignWrapper.getStartDate());
        Instant campaignStartDate = firstDate.atStartOfDay(ZoneId.of(SA_TIME_ZONE)).toInstant();

        LocalDate secondDate = LocalDate.parse(campaignWrapper.getEndDate());
        Instant campaignEndDate = secondDate.atStartOfDay(ZoneId.of(SA_TIME_ZONE)).toInstant();

        Campaign campaign = campaignBroker.createCampaign(campaignWrapper.getName(),campaignWrapper.getCode(),
                campaignWrapper.getDescription(),campaignWrapper.getUserUid(),campaignStartDate, campaignEndDate, tagList,
                CampaignType.valueOf(campaignWrapper.getType()),campaignWrapper.getUrl());
        if(!StringUtils.isEmpty(campaignWrapper.getGroupUid())){
            campaign = campaignBroker.linkCampaignToMasterGroup(campaign.getCampaignCode(),campaignWrapper.getGroupUid(),campaignWrapper.getUserUid());
        }
        else if(!StringUtils.isEmpty(campaignWrapper.getGroupName())){
            campaign = campaignBroker.createMasterGroupForCampaignAndLinkCampaign(campaign.getCampaignCode(),campaignWrapper.getGroupName(),campaignWrapper.getUserUid());
        }
        return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_CREATED,CampaignWebUtil.createCampaignWrapper(campaign));
    }

    @RequestMapping(value ="/add/tag", method = RequestMethod.GET)
    @ApiOperation(value = "add tag to campaign", notes = "add tag to a campaign")
    public ResponseEntity<ResponseWrapper> addCampaignTag(@RequestParam(value="campaignCode", required = true) String campaignCode,
                                 @RequestParam(value="tag", required = true) String tag) {
        List<String> tagList = new ArrayList<>();
        tagList.add(tag);
        Campaign campaign = campaignBroker.addCampaignTags(campaignCode, tagList);
        if(campaign != null) {
            return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_TAG_ADDED, CampaignWebUtil.createCampaignWrapper(campaign));
        }
        return RestUtil.messageOkayResponse(RestMessage.CAMPAIGN_NOT_FOUND);
    }

    @RequestMapping(value ="/add/message", method = RequestMethod.POST,  consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "add message to campaign", notes = "add message to a campaign")
    public ResponseEntity<ResponseWrapper> addCampaignMessage(@RequestBody CampaignMessageWrapper messageWrapper,
                                     BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            //handle errors
        }
        List<String> tagList = null;
        if (messageWrapper.getTags() != null && !messageWrapper.getTags().isEmpty()) {
            tagList = Collections.list(Collections.enumeration(messageWrapper.getTags()));
        }
        User user = userManager.load(messageWrapper.getUserUid());
        Campaign campaign = campaignBroker.addCampaignMessage(messageWrapper.getCampaignCode(), messageWrapper.getMessage(),
                new Locale(messageWrapper.getLanguageCode()), MessageVariationAssignment.valueOf(messageWrapper.getAssignmentType()), messageWrapper.getChannelType(), user, tagList);
        if(campaign != null) {
            return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_MESSAGE_ADDED, CampaignWebUtil.createCampaignWrapper(campaign));
        }
        return RestUtil.messageOkayResponse(RestMessage.CAMPAIGN_NOT_FOUND);
    }

    @RequestMapping(value="/view", method = RequestMethod.GET)
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
            return RestUtil.okayResponseWithData(RestMessage.CAMPAIGN_FOUND,CampaignWebUtil.createCampaignWrapper(campaign));
        }
        return RestUtil.messageOkayResponse(RestMessage.CAMPAIGN_NOT_FOUND);

    }
}
