package za.org.grassroot.webapp.controller.rest.campaign;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
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
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.CampaignMessageDTO;
import za.org.grassroot.services.exception.CampaignCodeTakenException;
import za.org.grassroot.services.exception.NoPaidAccountException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.exception.RestValidationMessage;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.CampaignViewDTO;
import za.org.grassroot.webapp.model.rest.wrappers.CreateCampaignRequest;
import za.org.grassroot.webapp.util.RestUtil;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController
@Api("/api/campaign/manage") @Slf4j
@RequestMapping(value = "/api/campaign/manage")
public class CampaignManagerController extends BaseRestController {

    private final CampaignBroker campaignBroker;
    private final UserManagementService userManager;
    private final GroupBroker groupBroker;
    private final CacheManager cacheManager;

    // we use three of these because we have access to different keys, and transforming one to another would require
    // DB hits that would significantly decrease benefit of cache (esp when campaign entities get big on large campaigns)
    // hence a little complexity in cache set up in exchange for simplicity later
    private Cache userCampaignsCache;
    private Cache groupCampaignsCache;
    private Cache fullCampaignsCache;

    private MessageSource messageSource;

    private static final CacheConfiguration CAMPAIGN_CACHE_CONFIG = new CacheConfiguration()
            .eternal(false).timeToLiveSeconds(300).maxEntriesLocalHeap(20);

    @Autowired
    public CampaignManagerController(JwtService jwtService, CampaignBroker campaignBroker, UserManagementService userManager, GroupBroker groupBroker, CacheManager cacheManager, @Qualifier("messageSource")
            MessageSource messageSource) {
        super(jwtService, userManager);
        this.campaignBroker = campaignBroker;
        this.userManager = userManager;
        this.groupBroker = groupBroker;
        this.cacheManager = cacheManager;
        this.messageSource = messageSource;
    }

    @PostConstruct
    public void init() {
        userCampaignsCache = new Cache(CAMPAIGN_CACHE_CONFIG.clone().name("user_campaigns"));
        cacheManager.addCache(userCampaignsCache);
        groupCampaignsCache = new Cache(CAMPAIGN_CACHE_CONFIG.clone().name("group_campaigns"));
        cacheManager.addCache(groupCampaignsCache);
        fullCampaignsCache = new Cache(CAMPAIGN_CACHE_CONFIG.clone().name("full_campaigns"));
        cacheManager.addCache(fullCampaignsCache);
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ApiOperation(value = "List user's campaigns", notes = "Lists the campaigns a user has created")
    public ResponseEntity<List<CampaignViewDTO>> fetchCampaignsManagedByUser(HttpServletRequest request) {
        final String userUid = getUserIdFromRequest(request);
        List<CampaignViewDTO> cachedCampaigns = checkForCampaignsInCache(userUid);
        if (cachedCampaigns != null) {
            return ResponseEntity.ok(cachedCampaigns);
        } else {
            List<CampaignViewDTO> dbCampaigns = campaignBroker.getCampaignsManagedByUser(getUserIdFromRequest(request))
                    .stream().map(CampaignViewDTO::new).collect(Collectors.toList());
            cacheUserCampaigns(userUid, dbCampaigns);
            return ResponseEntity.ok(dbCampaigns);
        }
    }

    @RequestMapping(value = "/list/group", method = RequestMethod.GET)
    @ApiOperation(value = "List  campaigns linked to group", notes = "Lists the campaigns linked to specific group")
    public ResponseEntity<List<CampaignViewDTO>> fetchCampaignsForGroup(String groupUid) {
        List<CampaignViewDTO> campaigns = checkForGroupCampaignsCache(groupUid);
        if (campaigns != null) {
            return ResponseEntity.ok(campaigns);
        }
        campaigns = campaignBroker.getCampaignsCreatedLinkedToGroup(groupUid).stream()
                .map(CampaignViewDTO::new).collect(Collectors.toList());
        cacheGroupCampaigns(groupUid, campaigns);
        return ResponseEntity.ok(campaigns);
    }

    @RequestMapping(value = "/fetch/{campaignUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch a specific campaign", notes = "Fetches a campaign with basic info")
    public ResponseEntity fetchCampaign(HttpServletRequest request, @PathVariable String campaignUid) {
        try {
            CampaignViewDTO viewDto = checkForCampaignInCache(campaignUid, getUserIdFromRequest(request));
            if (viewDto != null) {
                return ResponseEntity.ok(viewDto);
            }
            Campaign campaign = campaignBroker.load(campaignUid);
            viewDto = new CampaignViewDTO(campaign);
            cacheCampaignFull(viewDto, getUserIdFromRequest(request));
            return ResponseEntity.ok(viewDto);
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
                createCampaignRequest.getJoinTopics(),
                createCampaignRequest.getType(),
                createCampaignRequest.getUrl());

        clearCaches(campaign.getUid(), userUid, campaign.getMasterGroup().getUid());
        return ResponseEntity.ok(new CampaignViewDTO(campaign));
    }

    @ExceptionHandler(CampaignCodeTakenException.class)
    public ResponseEntity codeTaken() {
        return RestUtil.errorResponse(RestMessage.CAMPAIGN_WITH_SAME_CODE_EXIST);
    }

    @ExceptionHandler(NoPaidAccountException.class)
    public ResponseEntity noAccount() { return RestUtil.errorResponse(RestMessage.ACCOUNT_REQUIRED); }

    @RequestMapping(value = "/codes/list/active", method = RequestMethod.GET)
    @ApiOperation(value = "List all the active campaign codes (to prevent duplicates")
    public ResponseEntity<Set<String>> listJoinCodes() {
        return ResponseEntity.ok(campaignBroker.getActiveCampaignCodes());
    }

    @RequestMapping(value = "/codes/check", method = RequestMethod.GET)
    @ApiOperation(value = "Check if a campaign code is available")
    public Boolean isCodeAvailable(@RequestParam String code,
                                   @RequestParam(required = false) String currentCampaignUid) {
        log.info("is this code available: {}, for campaign uid: {}", code, currentCampaignUid);
        return !campaignBroker.isCodeTaken(code, currentCampaignUid);
    }

    @RequestMapping(value = "/messages/set/{campaignUid}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "add a set of messages to a campaign")
    public ResponseEntity addCampaignMessages(HttpServletRequest request, @PathVariable String campaignUid,
                                              @Valid @RequestBody Set<CampaignMessageDTO> campaignMessages,
                                              BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return RestUtil.errorResponseWithData(RestMessage.CAMPAIGN_MESSAGE_CREATION_INVALID_INPUT, getFieldValidationErrors(bindingResult.getFieldErrors()));
        }

        log.info("campaign messages received: {}", campaignMessages);
        Campaign updatedCampaign = campaignBroker.setCampaignMessages(getUserIdFromRequest(request), campaignUid, campaignMessages);
        return ResponseEntity.ok(new CampaignViewDTO(updatedCampaign));
    }

    @RequestMapping(value = "/update/image/{campaignUid}", method = RequestMethod.POST)
    @ApiOperation(value = "update the image for the campaign")
    public ResponseEntity updateCampaignImage(HttpServletRequest request, @PathVariable String campaignUid,
                                              @RequestParam String mediaFileUid) {
        campaignBroker.setCampaignImage(getUserIdFromRequest(request), campaignUid, mediaFileUid);
        clearCaches(campaignUid, getUserIdFromRequest(request), null);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/update/settings/{campaignUid}", method = RequestMethod.POST)
    @ApiOperation(value = "update some basic settings on campaign")
    public ResponseEntity updateCampaignBasicSettings(HttpServletRequest request, @PathVariable String campaignUid,
                                                      @RequestParam(required = false) String name,
                                                      @RequestParam(required = false) String description,
                                                      @RequestParam(required = false) Boolean removeImage,
                                                      @RequestParam(required = false) String mediaFileUid,
                                                      @RequestParam(required = false) Long endDateMillis,
                                                      @RequestParam(required = false) String newCode,
                                                      @RequestParam(required = false) CampaignType campaignType,
                                                      @RequestParam(required = false) String landingUrl,
                                                      @RequestParam(required = false) String petitionApi,
                                                      @RequestParam(required = false) List<String> joinTopics,
                                                      @RequestParam(required = false) String newMasterGroupUid) {
        String userUid = getUserIdFromRequest(request);
        log.info("altering campaign, setting fields: name = {}, desc = {}, image = {}, endDate = {}, landing = {}, petition = {}",
                name, description, mediaFileUid, endDateMillis, landingUrl, petitionApi);
        Instant endDate = endDateMillis != null ? Instant.ofEpochMilli(endDateMillis) : null;
        log.info("and end date instant = {}", endDate);
        campaignBroker.updateCampaignDetails(userUid, campaignUid, name, description, mediaFileUid, removeImage != null && removeImage,
                endDate, newCode, landingUrl, petitionApi, joinTopics);
        // doing this separately as is more important than other changes but not quite important enough for own method
        if (!StringUtils.isEmpty(newMasterGroupUid)) {
            Campaign priorUpdate = campaignBroker.load(campaignUid);
            clearCaches(null, null, priorUpdate.getMasterGroup().getUid());
            campaignBroker.updateMasterGroup(campaignUid, newMasterGroupUid, userUid);
        }
        // and this one separately as may split out, depending on user feedback
        if (campaignType != null) {
            campaignBroker.updateCampaignType(userUid, campaignUid, campaignType, null);
        }
        Campaign updatedCampaign = campaignBroker.load(campaignUid);
        log.info("updated campaign group name: {}", updatedCampaign.getMasterGroup().getName());
        clearCaches(campaignUid, userUid, updatedCampaign.getMasterGroup().getUid());
        CampaignViewDTO revisedDTO = new CampaignViewDTO(updatedCampaign);
        log.info("revised campaign: {}", updatedCampaign);
        log.info("and revised DTO: {}", revisedDTO);
        return ResponseEntity.ok(revisedDTO);
    }

    @RequestMapping(value = "/update/sharing/{campaignUid}", method = RequestMethod.POST)
    public ResponseEntity alterSmsSharingSettings(HttpServletRequest request, @PathVariable String campaignUid,
                                                  @RequestParam boolean sharingEnabled, @RequestParam long smsLimit,
                                                  @RequestBody(required = false) Set<CampaignMessageDTO> sharingMessages) {
        String userUid = getUserIdFromRequest(request);
        log.info("changing SMS settings: enabled = {}, budget = {}, messages = {}", sharingEnabled, smsLimit, sharingMessages);
        campaignBroker.alterSmsSharingSettings(userUid, campaignUid, sharingEnabled, smsLimit, sharingMessages);
        return ResponseEntity.ok(fetchAndCacheUpdatedCampaign(campaignUid, userUid));
    }

    @RequestMapping(value = "/update/type/{campaignUid}", method = RequestMethod.POST)
    public ResponseEntity updateCampaignType(HttpServletRequest request, @PathVariable String campaignUid,
                                             @RequestParam CampaignType campaignType,
                                             @RequestBody(required = false) Set<CampaignMessageDTO> revisedMessages) {
        String userUid = getUserIdFromRequest(request);
        log.info("changing campaign to type: {}, with messages: {}", campaignType, revisedMessages);
        campaignBroker.updateCampaignType(userUid, campaignUid, campaignType, revisedMessages);
        return ResponseEntity.ok(fetchAndCacheUpdatedCampaign(campaignUid, userUid));
    }

    private CampaignViewDTO fetchAndCacheUpdatedCampaign(String campaignUid, String userUid) {
        Campaign updatedCampaign = campaignBroker.load(campaignUid);
        clearCaches(campaignUid, userUid, updatedCampaign.getMasterGroup().getUid());
        CampaignViewDTO viewDTO = new CampaignViewDTO(updatedCampaign);
        cacheCampaignFull(viewDTO, userUid);
        return viewDTO;
    }

    private void clearCaches(String campaignUid, String userUid, String groupUid) {
        if (campaignUid != null)
            fullCampaignsCache.remove(campaignUid);
        if (userUid != null)
            userCampaignsCache.remove(userUid);
        if (groupUid != null)
            groupCampaignsCache.remove(groupUid);
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

    private List<CampaignViewDTO> checkForCampaignsInCache(String userUid) {
        try {
            return userCampaignsCache.isKeyInCache(userUid) ? (List<CampaignViewDTO>) userCampaignsCache.get(userUid).getObjectValue() : null;
        } catch (NullPointerException e) {
            log.info("null pointer strangeness in campaigns cache again");
            return null;
        }
    }

    private void cacheUserCampaigns(String userUid, List<CampaignViewDTO> campaigns) {
        userCampaignsCache.put(new Element(userUid, campaigns));
    }

    private List<CampaignViewDTO> checkForGroupCampaignsCache(String groupUid) {
        return !groupCampaignsCache.isKeyInCache(groupUid) ? null :
                (List<CampaignViewDTO>) groupCampaignsCache.get(groupUid).getObjectValue();
    }

    private void cacheGroupCampaigns(String groupUid, List<CampaignViewDTO> campaigns) {
        groupCampaignsCache.put(new Element(groupUid, campaigns));
    }

    private CampaignViewDTO checkForCampaignInCache(String campaignUid, String userUid) {
        try {
            return !fullCampaignsCache.isKeyInCache(campaignUid + userUid) ? null :
                    (CampaignViewDTO) fullCampaignsCache.get(campaignUid + userUid).getObjectValue();
        } catch (NullPointerException e) {
            log.info("got null pointer getting campaign from cache");
            return null;
        }
    }

    private void cacheCampaignFull(CampaignViewDTO campaign, String userUid) {
        fullCampaignsCache.put(new Element(campaign.getCampaignUid() + userUid, campaign));
    }
}
