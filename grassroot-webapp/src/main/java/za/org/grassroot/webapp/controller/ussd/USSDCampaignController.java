package za.org.grassroot.webapp.controller.ussd;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.GroupJoinMethod;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignMessageAction;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.util.CampaignUtil;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDCampaignUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDCampaignController extends USSDBaseController {

    private static final String campaignMenus = "campaign/";
    private static final String campaignUrl = homePath + campaignMenus;
    private final CampaignBroker campaignBroker;
    private final UserManagementService userManagementService;

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.TAG_ME_URL)
    @ResponseBody
    public Request processTagMeRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam (value = USSDCampaignUtil.CODE_PARAMETER) String campaignCode,
                                      @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode,
                                      @RequestParam (value = USSDCampaignUtil.MESSAGE_UID)String parentMessageUid
                                      )  throws URISyntaxException{

        User user = userManagementService.loadOrCreateUser(inputNumber);
        Campaign campaign = campaignBroker.getCampaignDetailsByCode(campaignCode);
        updateMembership(user,campaign,parentMessageUid);
        CampaignMessage message =  CampaignUtil.getNextCampaignMessageByActionTypeAndLocale(campaign,CampaignActionType.TAG_ME,parentMessageUid, new Locale(languageCode));
        return menuBuilder(buildCampaignUSSDMenu(message,languageCode, campaignCode));

    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.JOIN_MASTER_GROUP_URL)
    @ResponseBody
    public Request processJoinMasterGroupRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                                @RequestParam (value = USSDCampaignUtil.CODE_PARAMETER) String campaignCode,
                                                @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode,
                                                @RequestParam (value = USSDCampaignUtil.MESSAGE_UID)String parentMessageUid)  throws URISyntaxException{
        CampaignMessage message = addUserToMasterGroup(campaignCode,inputNumber,CampaignActionType.JOIN_MASTER_GROUP, parentMessageUid, new Locale(languageCode));
        return menuBuilder(buildCampaignUSSDMenu(message,languageCode, campaignCode));
    }

    @Autowired
    public USSDCampaignController(CampaignBroker campaignBroker, UserManagementService userManagementService ){
        this.campaignBroker = campaignBroker;
        this.userManagementService = userManagementService;
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.SET_LANGUAGE_URL)
    @ResponseBody
    public Request userSetLanguageForCampaign(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                      @RequestParam (value = USSDCampaignUtil.CODE_PARAMETER) String campaignCode,
                                      @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        if(StringUtils.isEmpty(user.getLanguageCode())) {
            userManager.updateUserLanguage(user.getUid(), new Locale(languageCode));
        }
        Campaign campaign =  campaignBroker.getCampaignDetailsByCode(campaignCode);
        CampaignMessage campaignMessage = CampaignUtil.getFirstCampaignMessageByLocale(campaign,languageCode);
        return  menuBuilder(buildCampaignUSSDMenu(campaignMessage, languageCode,campaignCode));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.SIGN_PETITION_URL)
    @ResponseBody
    public Request processSignPetitionRequest(@RequestParam (value = USSDCampaignUtil.CODE_PARAMETER) String campaignCode,
                                             @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode,
                                             @RequestParam (value = USSDCampaignUtil.MESSAGE_UID)String parentMessageUid)  throws URISyntaxException{
        Campaign campaign = campaignBroker.getCampaignDetailsByCode(campaignCode);
        CampaignMessage message = CampaignUtil.getNextCampaignMessageByActionTypeAndLocale(campaign,CampaignActionType.SIGN_PETITION,parentMessageUid, new Locale(languageCode));
        //TO do. integrate to petition API
        return menuBuilder(buildCampaignUSSDMenu(message,languageCode, campaignCode));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.MORE_INFO_URL)
    @ResponseBody
    public Request processMoreInfoRequest(@RequestParam(value = USSDCampaignUtil.CODE_PARAMETER) String campaignCode,
                                          @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode,
                                          @RequestParam (value = USSDCampaignUtil.MESSAGE_UID)String parentMessageUid)  throws URISyntaxException{
        Campaign campaign = campaignBroker.getCampaignDetailsByCode(campaignCode);
        CampaignMessage message = CampaignUtil.getNextCampaignMessageByActionTypeAndLocale(campaign,CampaignActionType.MORE_INFO,parentMessageUid, new Locale(languageCode));
        return menuBuilder(buildCampaignUSSDMenu(message,languageCode, campaignCode));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.EXIT_URL)
    @ResponseBody
    public Request processExitRequest(@RequestParam (value = USSDCampaignUtil.CODE_PARAMETER) String campaignCode,
                                     @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode,
                                     @RequestParam (value = USSDCampaignUtil.MESSAGE_UID)String parentMessageUid )  throws URISyntaxException{
        Campaign campaign = campaignBroker.getCampaignDetailsByCode(campaignCode);
        CampaignMessage message = CampaignUtil.getNextCampaignMessageByActionTypeAndLocale(campaign,CampaignActionType.EXIT,parentMessageUid, new Locale(languageCode));
        return menuBuilder(buildCampaignUSSDMenu(message,languageCode, campaignCode));
    }

    private USSDMenu buildCampaignUSSDMenu(CampaignMessage campaignMessage, String languageCode, String campaignCode){
        String promptMessage = campaignMessage.getMessage();
        Map<String, String> linksMap = new HashMap<>();
        if(campaignMessage.getCampaignMessageActionSet() != null && !campaignMessage.getCampaignMessageActionSet().isEmpty()){
            for(CampaignMessageAction action : campaignMessage.getCampaignMessageActionSet()){
                String optionKey = USSDCampaignUtil.CAMPAIGN_PREFIX + action.getActionType().name().toLowerCase();
                String option  = getMessage(optionKey,languageCode);
                StringBuilder embeddedUrl = new StringBuilder();
                embeddedUrl.append(campaignMenus);
                embeddedUrl.append(USSDCampaignUtil.getCampaignUrlPrefixs().get(action.getActionType()));
                embeddedUrl.append(USSDCampaignUtil.CODE_PARAMETER);
                embeddedUrl.append(campaignCode);
                embeddedUrl.append(USSDCampaignUtil.LANGUAGE_PARAMETER);
                embeddedUrl.append(languageCode);
                embeddedUrl.append(USSDCampaignUtil.MESSAGE_UID_PARAMETER);
                embeddedUrl.append(campaignMessage.getUid());
                linksMap.put(embeddedUrl.toString(),option);
            }
        }
        return new USSDMenu(promptMessage,linksMap);
    }

    private CampaignMessage addUserToMasterGroup(String campaignCode, String phoneNumber, CampaignActionType type, String parentMessageUid, Locale locale) {
        Campaign campaign = campaignBroker.addUserToCampaignMasterGroup(campaignCode,phoneNumber);
        return CampaignUtil.getNextCampaignMessageByActionTypeAndLocale(campaign, type, parentMessageUid, locale);
    }

    private void updateMembership(User user, Campaign campaign, String messageUid){
        CampaignMessage message = CampaignUtil.findCampaignMessageFromCampaignByMessageUid(campaign,messageUid);
        if(message != null && message.getTagList() != null && message.getTagList().isEmpty()){
            if(user.getMemberships() != null && !user.getMemberships().isEmpty()){
                for(Membership membership: user.getMemberships()){
                    if(membership.getGroup().getUid().equalsIgnoreCase(campaign.getMasterGroup().getUid())){
                        addTagsOnMembership(membership, message.getTagList());
                    }
                }
            }else {
                Membership membership = new Membership(campaign.getMasterGroup(), user, new Role(BaseRoles.ROLE_ORDINARY_MEMBER, null),
                        Instant.now(), GroupJoinMethod.SELF_JOINED, null);
                addTagsOnMembership(membership, message.getTagList());
                user.getMemberships().add(membership);
                userManagementService.createUserProfile(user);
            }
        }
    }

    private void addTagsOnMembership(Membership membership, List<String> tags){
        for(String tag :tags) {
            membership.addTag(tag);
        }
    }
}
