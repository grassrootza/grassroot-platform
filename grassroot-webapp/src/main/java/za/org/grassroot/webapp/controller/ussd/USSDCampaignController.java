package za.org.grassroot.webapp.controller.ussd;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignMessageAction;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.util.CampaignUtil;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDCampaignUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDCampaignController extends USSDController{

    private static final String campaignMenus = "campaign/";
    private static final String campaignUrl = homePath + campaignMenus;
    private final CampaignBroker campaignBroker;
    private final GroupJoinRequestService groupJoinRequestService;
    private final UserManagementService userManagementService;

    @Autowired
    public USSDCampaignController(CampaignBroker campaignBroker,GroupJoinRequestService groupJoinRequestService, UserManagementService userManagementService ){
        this.campaignBroker = campaignBroker;
        this.groupJoinRequestService = groupJoinRequestService;
        this.userManagementService = userManagementService;
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.TAG_ME_URL)
    @ResponseBody
    public Request processTagMeRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam (value = USSDCampaignUtil.CODE_PARAMETER) String campaignCode,
                                      @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode,
                                       @RequestParam (value = USSDCampaignUtil.TAG_PARAMETER)String tag)  throws URISyntaxException{

        User user = userManagementService.loadOrCreateUser(inputNumber);
        Campaign campaign = campaignBroker.getCampaignDetailsByCode(campaignCode);
        groupJoinRequestService.open(user.getUid(), campaign.getMasterGroup().getUid(), "Join Campaign Master Group");
        updateMembership(user,campaign,tag);
        CampaignMessage message =  CampaignUtil.processCampaignMessageByAssignmentVariationAndUserInterfaceTypeAndLocaleAndActionType(campaign,MessageVariationAssignment.CONTROL, UserInterfaceType.USSD, new Locale(languageCode),CampaignActionType.TAG_ME);
        return menuBuilder(buildUSSDMenu(message,languageCode, campaignCode));

    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.JOIN_MASTER_GROUP_URL)
    @ResponseBody
    public Request processJoinMasterGroupRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                                @RequestParam (value = USSDCampaignUtil.CODE_PARAMETER) String campaignCode,
                                                 @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode)  throws URISyntaxException{
        CampaignMessage message = addUserToMasterGroup(campaignCode,inputNumber,languageCode,CampaignActionType.JOIN_MASTER_GROUP);
        return menuBuilder(buildUSSDMenu(message,languageCode, campaignCode));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.SIGN_PETITION_URL)
    @ResponseBody
    public Request processSignPetitionRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                             @RequestParam (value = USSDCampaignUtil.CODE_PARAMETER) String campaignCode,
                                              @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode)  throws URISyntaxException{
        CampaignMessage message = campaignBroker.getCampaignMessageByCampaignCodeAndActionType(campaignCode, MessageVariationAssignment.CONTROL, UserInterfaceType.USSD, CampaignActionType.SIGN_PETITION, inputNumber, new Locale(languageCode));
        //TO do. integrate to petition API
        return menuBuilder(buildUSSDMenu(message,languageCode, campaignCode));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.MORE_INFO_URL)
    @ResponseBody
    public Request processMoreInfoRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                          @RequestParam String campaignCode,
                                          @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode)  throws URISyntaxException{
        CampaignMessage message = campaignBroker.getCampaignMessageByCampaignCodeAndActionType(campaignCode, MessageVariationAssignment.CONTROL, UserInterfaceType.USSD, CampaignActionType.MORE_INFO, inputNumber, new Locale(languageCode));
        return menuBuilder(buildUSSDMenu(message,languageCode, campaignCode));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.EXIT_URL)
    @ResponseBody
    public Request processExitRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam (value = USSDCampaignUtil.CODE_PARAMETER) String campaignCode,
                                     @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode)  throws URISyntaxException{
        CampaignMessage message = campaignBroker.getCampaignMessageByCampaignCodeAndActionType(campaignCode, MessageVariationAssignment.CONTROL, UserInterfaceType.USSD, CampaignActionType.EXIT, inputNumber, new Locale(languageCode));
        return menuBuilder(buildUSSDMenu(message,languageCode, campaignCode));
    }

    private USSDMenu buildUSSDMenu(CampaignMessage campaignMessage,String languageCode, String campaignCode){
        String promptMessage = campaignMessage.getMessage();
        Map<String, String> linksMap = new HashMap<>();
        if(campaignMessage.getCampaignMessageActionSet() != null && !campaignMessage.getCampaignMessageActionSet().isEmpty()){
            for(CampaignMessageAction action : campaignMessage.getCampaignMessageActionSet()){
                String optionKey = USSDCampaignUtil.CAMPAIGN_PREFIX + action.getActionType().name().toLowerCase();
                String option  = getMessage(optionKey,languageCode);
                StringBuilder url = new StringBuilder();
                url.append(USSDCampaignUtil.getCampaignUrls().get(action.getActionType()));
                url.append(USSDCampaignUtil.CODE_PARAMETER);
                url.append(campaignCode);
                url.append(USSDCampaignUtil.LANGUAGE_PARAMETER);
                url.append(languageCode);
                if(campaignMessage.getTags() != null && campaignMessage.getTags().length > 0){
                    url.append(USSDCampaignUtil.TAG_PARAMETER);
                    url.append(campaignMessage.getTags()[0]);//revisit
                }
                linksMap.put(option,url.toString());
            }
        }
        return new USSDMenu(promptMessage,linksMap);
    }

    private CampaignMessage addUserToMasterGroup(String campaignCode, String phoneNumber, String languageCode, CampaignActionType type){
        User user = userManagementService.loadOrCreateUser(phoneNumber);
        Campaign campaign = campaignBroker.getCampaignDetailsByCode(campaignCode);
        groupJoinRequestService.open(user.getUid(), campaign.getMasterGroup().getUid(), "Join Campaign Master Group");
        return  CampaignUtil.processCampaignMessageByAssignmentVariationAndUserInterfaceTypeAndLocaleAndActionType(campaign,MessageVariationAssignment.CONTROL, UserInterfaceType.USSD, new Locale(languageCode),type);
    }

    //check with Luke:
    //Question is should joining of master group require approval?
    //when is Membership updated...what is the flow?
    private void updateMembership(User user, Campaign campaign, String tag){
        if(user.getMemberships() != null && !user.getMemberships().isEmpty()){
            for(Membership membership: user.getMemberships()){
                if(membership.getGroup().getId() == campaign.getMasterGroup().getId()){
                    membership.addTag(tag);
                }
            }
        }else {
            Membership membership = new Membership(campaign.getMasterGroup(), user, new Role(BaseRoles.ROLE_ORDINARY_MEMBER, null), Instant.now());
            membership.addTag(tag);
            user.getMemberships().add(membership);
            userManagementService.createUserProfile(user);
        }
    }
}
