package za.org.grassroot.webapp.controller.rest.campaign;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.CampaignLogsDataCollection;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.CampaignMessageDTO;
import za.org.grassroot.services.campaign.CampaignStatsBroker;
import za.org.grassroot.services.campaign.CampaignTextBroker;
import za.org.grassroot.services.exception.CampaignCodeTakenException;
import za.org.grassroot.services.exception.NoPaidAccountException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.CampaignViewDTO;
import za.org.grassroot.webapp.model.rest.wrappers.CreateCampaignRequest;
import za.org.grassroot.webapp.util.RestUtil;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController
@Api("/v2/api/campaign/manage") @Slf4j
@RequestMapping(value = "/v2/api/campaign/manage")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class CampaignManagerController extends BaseRestController {

    private final CampaignBroker campaignBroker;
    private final CampaignTextBroker campaignTextBroker;
    private final CampaignStatsBroker campaignStatsBroker;

    private final UserManagementService userManager;
    private final GroupBroker groupBroker;
    private final CacheManager cacheManager;

    private Cache campaignsCache;

    @Autowired
    public CampaignManagerController(JwtService jwtService, CampaignBroker campaignBroker, CampaignTextBroker campaignTextBroker, CampaignStatsBroker campaignStatsBroker, UserManagementService userManager, GroupBroker groupBroker, CacheManager cacheManager) {
        super(jwtService, userManager);
        this.campaignBroker = campaignBroker;
        this.campaignTextBroker = campaignTextBroker;
        this.campaignStatsBroker = campaignStatsBroker;
        this.userManager = userManager;
        this.groupBroker = groupBroker;
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    public void init() {
        log.info("Setting up campaign caches");
        campaignsCache = cacheManager.getCache("campaign_view_dtos");
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ApiOperation(value = "List user's campaigns", notes = "Lists the campaigns a user has created")
    public ResponseEntity<List<CampaignViewDTO>> fetchCampaignsManagedByUser(HttpServletRequest request) {
        List<CampaignViewDTO> dbCampaigns = campaignBroker
                .getCampaignsManagedByUser(getUserIdFromRequest(request))
                .stream().map(campaign -> getCampaign(campaign.getUid(), false))
                .sorted(Comparator.comparing(CampaignViewDTO::getLastActivityEpochMilli, Comparator.reverseOrder()))
                .collect(Collectors.toList());
        cacheCampaigns(dbCampaigns);
        return ResponseEntity.ok(dbCampaigns);
    }

    // we only use this when loading page, so send whole back
    @RequestMapping(value = "/fetch/{campaignUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch a specific campaign", notes = "Fetches a campaign with basic info")
    public ResponseEntity fetchCampaign(HttpServletRequest request, @PathVariable String campaignUid) {
        try {
            campaignBroker.validateUserCanViewFull(getUserIdFromRequest(request), campaignUid);
            CampaignViewDTO viewDto = getCampaign(campaignUid, true);
            log.info("sending back DTO with messages: {}", viewDto.getCampaignMessages());
            return ResponseEntity.ok(viewDto);
        } catch (AccessDeniedException e) {
            return RestUtil.errorResponse(RestMessage.USER_NOT_CAMPAIGN_MANAGER);
        }
    }

    @RequestMapping(value = "/fetch/media/{campaignUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the records for inbound media (which users have submitted, via WhatsApp")
    public ResponseEntity fetchCampaignMediaList(HttpServletRequest request, @PathVariable String campaignUid) {
        return ResponseEntity.ok(campaignBroker.fetchInboundCampaignMediaDetails(getUserIdFromRequest(request), campaignUid));
    }

    @RequestMapping(value = "/create" , method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "create campaign", notes = "create a campaign using given values")
    public ResponseEntity createCampaign(@Valid @RequestBody CreateCampaignRequest createCampaignRequest,
                                         BindingResult bindingResult, HttpServletRequest request){
        String userUid = getUserIdFromRequest(request);

        log.info("creating a campaign with request: {}", createCampaignRequest);

        if (bindingResult.hasErrors()) {
            log.info("error! binding result has errors: {}", bindingResult);
            return RestUtil.errorResponseWithData(RestMessage.CAMPAIGN_CREATION_INVALID_INPUT, bindingResult.getFieldErrors());
        }

        if (createCampaignRequest.hasNoGroup()) {
            log.info("error! campaign has no master group set");
            return RestUtil.errorResponse(RestMessage.CAMPAIGN_MISSING_MASTER_GROUP);
        }

        Instant campaignStartDate = Instant.ofEpochMilli(createCampaignRequest.getStartDateEpochMillis());
        log.info("about to convert campaign end ate, from millis: ", createCampaignRequest.getEndDateEpochMillis());
        Instant campaignEndDate = Instant.ofEpochMilli(createCampaignRequest.getEndDateEpochMillis());

        String masterGroupUid = createCampaignRequest.getGroupUid();
        if (StringUtils.isEmpty(masterGroupUid)) {
            User groupCreator = userManager.load(userUid);
            MembershipInfo creator = new MembershipInfo(groupCreator, groupCreator.getName(), BaseRoles.ROLE_GROUP_ORGANIZER, null);
            Group createdGroup = groupBroker.create(userUid, createCampaignRequest.getGroupName(), null, Collections.singleton(creator),
                    GroupPermissionTemplate.CLOSED_GROUP, null, null, false, false, true);

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
                createCampaignRequest.getUrl(),
                createCampaignRequest.isSmsShare(),
                createCampaignRequest.getSmsLimit() == null ? 0 : createCampaignRequest.getSmsLimit(),
                createCampaignRequest.getImageKey());

        return ResponseEntity.ok(recacheCampaignAndReturnDTO(campaign, true));
    }

    @ExceptionHandler(CampaignCodeTakenException.class)
    public ResponseEntity codeTaken(CampaignCodeTakenException e) {
        log.error("Campaign code taken! Should not happen ... error: ", e);
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

    @RequestMapping(value = "/words/check", method = RequestMethod.GET)
    @ApiOperation(value = "Check if a campaign join word is available")
    public Boolean isJoinWordAvailable(@RequestParam String word, @RequestParam(required = false) String currentCampaignUid) {
        log.info("is this join word available: {}, for campaign uid: {}", word, currentCampaignUid);
        return !campaignBroker.isTextJoinWordTaken(word.trim(), currentCampaignUid);
    }

    @RequestMapping(value = "/messages/set/{campaignUid}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "add a set of messages to a campaign")
    public ResponseEntity addCampaignMessages(HttpServletRequest request, @PathVariable String campaignUid,
                                              @Valid @RequestBody Set<CampaignMessageDTO> campaignMessages,
                                              BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return RestUtil.errorResponseWithData(RestMessage.CAMPAIGN_MESSAGE_CREATION_INVALID_INPUT, bindingResult.getFieldErrors());
        }

        log.info("campaign messages received: {}", campaignMessages);
        Campaign updatedCampaign = campaignBroker.setCampaignMessages(getUserIdFromRequest(request), campaignUid, campaignMessages);
        return ResponseEntity.ok(recacheCampaignAndReturnDTO(updatedCampaign, true));
    }

    @RequestMapping(value = "/update/image/{campaignUid}", method = RequestMethod.POST)
    @ApiOperation(value = "update the image for the campaign")
    public ResponseEntity updateCampaignImage(HttpServletRequest request, @PathVariable String campaignUid,
                                              @RequestParam String mediaFileUid) {
        campaignBroker.setCampaignImage(getUserIdFromRequest(request), campaignUid, mediaFileUid);
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
                                                      @RequestParam(required = false) String newJoinWord,
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
                endDate, newCode, newJoinWord, landingUrl, petitionApi, joinTopics);

        // doing this separately as is more important than other changes but not quite important enough for own method
        if (!StringUtils.isEmpty(newMasterGroupUid)) {
            campaignBroker.updateMasterGroup(campaignUid, newMasterGroupUid, userUid);
        }
        // and this one separately as may split out, depending on user feedback
        if (campaignType != null) {
            campaignBroker.updateCampaignType(userUid, campaignUid, campaignType, null);
        }
        Campaign updatedCampaign = campaignBroker.load(campaignUid);
        log.info("updated campaign group name: {}", updatedCampaign.getMasterGroup().getName());
        return ResponseEntity.ok(recacheCampaignAndReturnDTO(updatedCampaign, true));
    }

    @RequestMapping(value = "/end/{campaignUid}", method = RequestMethod.GET)
    public ResponseEntity endCampaign(@PathVariable String campaignUid, HttpServletRequest request) {
        campaignBroker.endCampaign(getUserIdFromRequest(request), campaignUid);
        return ResponseEntity.ok(fetchAndCacheUpdatedCampaign(campaignUid));
    }

    @RequestMapping(value = "/update/sharing/{campaignUid}", method = RequestMethod.POST)
    public ResponseEntity alterSmsSharingSettings(HttpServletRequest request, @PathVariable String campaignUid,
                                                  @RequestParam boolean sharingEnabled, @RequestParam long smsLimit,
                                                  @RequestBody(required = false) Set<CampaignMessageDTO> sharingMessages) {
        String userUid = getUserIdFromRequest(request);
        log.info("changing SMS settings: enabled = {}, budget = {}, messages = {}", sharingEnabled, smsLimit, sharingMessages);
        campaignBroker.alterSmsSharingSettings(userUid, campaignUid, sharingEnabled, smsLimit, sharingMessages);
        return ResponseEntity.ok(fetchAndCacheUpdatedCampaign(campaignUid));
    }

    @RequestMapping(value = "/update/type/{campaignUid}", method = RequestMethod.POST)
    public ResponseEntity updateCampaignType(HttpServletRequest request, @PathVariable String campaignUid,
                                             @RequestParam CampaignType campaignType,
                                             @RequestBody(required = false) Set<CampaignMessageDTO> revisedMessages) {
        String userUid = getUserIdFromRequest(request);
        log.info("changing campaign to type: {}, with messages: {}", campaignType, revisedMessages);
        campaignBroker.updateCampaignType(userUid, campaignUid, campaignType, revisedMessages);
        return ResponseEntity.ok(fetchAndCacheUpdatedCampaign(campaignUid));
    }

    @RequestMapping(value = "/update/welcome/set/{campaignUid}", method = RequestMethod.POST)
    public ResponseEntity updateCampaignWelcomeMessage(HttpServletRequest request, @PathVariable String campaignUid,
                                                       @RequestParam String message) {
        campaignTextBroker.setCampaignMessageText(getUserIdFromRequest(request), campaignUid, message);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/update/welcome/clear/{campaignUid}", method = RequestMethod.POST)
    public ResponseEntity clearCampaignWelcomeMsg(HttpServletRequest request, @PathVariable String campaignUid) {
        campaignTextBroker.clearCampaignMessageText(getUserIdFromRequest(request), campaignUid);
        return ResponseEntity.ok().build();
    }

    // campaign fetch is already heavy enough and this is a very specific use case, so separating it
    // not the world' best REST path, but not really worth the candle to rewire a bunch above
    @RequestMapping(value = "/update/welcome/current/{campaignUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch campaign welcome message, if one exists")
    public ResponseEntity checkForExistingCampaignWelcome(HttpServletRequest request, @PathVariable String campaignUid) {
        final String message = campaignTextBroker.getCampaignMessageText(getUserIdFromRequest(request), campaignUid);
        return StringUtils.isEmpty(message) ? ResponseEntity.ok().build() : ResponseEntity.ok(message);
    }

    // and this is for setting a default language
    @RequestMapping(value = "/update/language/{campaignUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Change the default language on the campaign")
    public ResponseEntity updateCampaignDefaultLanguage(HttpServletRequest request, @PathVariable String campaignUid,
                                                        @RequestParam Locale defaultLanguage) {
        log.info("Updating campaign to language: {}", defaultLanguage);
        campaignBroker.updateCampaignDefaultLanguage(getUserIdFromRequest(request), campaignUid, defaultLanguage);
        return ResponseEntity.ok(fetchAndCacheUpdatedCampaign(campaignUid));
    }

    private CampaignViewDTO fetchAndCacheUpdatedCampaign(String campaignUid) {
        Campaign updatedCampaign = campaignBroker.load(campaignUid);
        return recacheCampaignAndReturnDTO(updatedCampaign, true);
    }

    private void cacheCampaigns(List<CampaignViewDTO> campaigns) {
        campaignsCache.putAll(campaigns.stream().map(dto -> new Element(dto.getCampaignUid(), dto)).collect(Collectors.toSet()));
    }

    private CampaignViewDTO recacheCampaignAndReturnDTO(Campaign campaign, boolean fullInfo) {
        CampaignLogsDataCollection countCollection = campaignStatsBroker.getCampaignLogData(campaign.getUid());
        CampaignViewDTO dto = new CampaignViewDTO(campaign, countCollection, fullInfo);
        campaignsCache.put(new Element(campaign.getUid(), dto));
        return dto;
    }

    private CampaignViewDTO getCampaign(String campaignUid, boolean fullInfo) {
        return getCampaignIfInCache(campaignUid, fullInfo).orElseGet(() -> {
            CampaignLogsDataCollection countCollection = campaignStatsBroker.getCampaignLogData(campaignUid);
            Campaign campaign = campaignBroker.load(campaignUid);
            CampaignViewDTO dto = new CampaignViewDTO(campaign, countCollection, fullInfo);
            cacheCampaigns(Collections.singletonList(dto));
            return dto;
        });
    }

    private Optional<CampaignViewDTO> getCampaignIfInCache(String campaignUid, boolean fullInfo) {
        if (!campaignsCache.isKeyInCache(campaignUid))
            return Optional.empty();
        Element cacheElement = campaignsCache.get(campaignUid);
        if (cacheElement == null || cacheElement.getObjectValue() == null)
            return Optional.empty();
        CampaignViewDTO dto = (CampaignViewDTO) cacheElement.getObjectValue();
        return !fullInfo || dto.isFullInfo() ? Optional.of(dto) : Optional.empty();
    }
}
