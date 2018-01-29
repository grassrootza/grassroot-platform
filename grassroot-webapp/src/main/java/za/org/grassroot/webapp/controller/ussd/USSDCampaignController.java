package za.org.grassroot.webapp.controller.ussd;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDCampaignUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController @Slf4j
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDCampaignController extends USSDBaseController {

    private static final String campaignMenus = "campaign/";
    private static final String campaignUrl = homePath + campaignMenus;

    private final CampaignBroker campaignBroker;

    @Autowired
    public USSDCampaignController(CampaignBroker campaignBroker) {
        this.campaignBroker = campaignBroker;
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.TAG_ME_URL)
    @ResponseBody
    public Request processTagMeRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam String messageUid,
                                      @RequestParam String parentMsgUid)  throws URISyntaxException{
        User user = userManager.findByInputNumber(inputNumber);
        CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
        updateMembership(user, message, campaignBroker.loadCampaignMessage(parentMsgUid, user.getUid()));
        return menuBuilder(buildCampaignUSSDMenu(message));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.JOIN_MASTER_GROUP_URL)
    @ResponseBody
    public Request processJoinMasterGroupRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                                 @RequestParam String messageUid)  throws URISyntaxException{
        User user = userManager.findByInputNumber(inputNumber);
        CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
        campaignBroker.addUserToCampaignMasterGroup(message.getCampaign().getUid(), user.getUid());
        return menuBuilder(buildCampaignUSSDMenu(message));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.SET_LANGUAGE_URL)
    @ResponseBody
    public Request userSetLanguageForCampaign(@RequestParam(value= phoneNumber) String inputNumber,
                                      @RequestParam String campaignUid,
                                      @RequestParam (value = USSDCampaignUtil.LANGUAGE_PARAMETER) String languageCode) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        if(StringUtils.isEmpty(user.getLanguageCode())) {
            userManager.updateUserLanguage(user.getUid(), new Locale(languageCode));
        }
        CampaignMessage campaignMessage = campaignBroker.getOpeningMessage(campaignUid, new Locale(languageCode), UserInterfaceType.USSD, null);
        return  menuBuilder(buildCampaignUSSDMenu(campaignMessage));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.SIGN_PETITION_URL)
    @ResponseBody
    public Request processSignPetitionRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                              @RequestParam String messageUid) throws URISyntaxException{
        User user = userManager.findByInputNumber(inputNumber);
        CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
        // todo : actually sign the petition

        return menuBuilder(buildCampaignUSSDMenu(message));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.MORE_INFO_URL)
    @ResponseBody
    public Request processMoreInfoRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                          @RequestParam String messageUid)  throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
        return menuBuilder(buildCampaignUSSDMenu(message));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.EXIT_URL)
    @ResponseBody
    public Request processExitRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam String messageUid)  throws URISyntaxException{
        User user = userManager.findByInputNumber(inputNumber);
        CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
        return menuBuilder(buildCampaignUSSDMenu(message));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignUtil.SHARE_URL)
    @ResponseBody
    public Request sharePrompt(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam String messageUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
        return menuBuilder(buildCampaignUSSDMenu(message));
    }

    // todo : then, the actual share screen

    private USSDMenu buildCampaignUSSDMenu(CampaignMessage campaignMessage){
        String promptMessage = campaignMessage.getMessage();
        Map<String, String> linksMap = new LinkedHashMap<>();
        campaignMessage.getNextMessages().forEach((msgUid, actionType) -> {
            String optionKey = USSDCampaignUtil.CAMPAIGN_PREFIX + actionType.name().toLowerCase();
            String option = getMessage(optionKey, campaignMessage.getLocale().getLanguage());
            String embeddedUrl = campaignMenus +
                    USSDCampaignUtil.getCampaignUrlPrefixs().get(actionType) + "?" +
                    USSDCampaignUtil.MESSAGE_UID_PARAMETER + msgUid;
            linksMap.put(embeddedUrl, option);
        });
        return new USSDMenu(promptMessage,linksMap);
    }

    private void updateMembership(User user, CampaignMessage thisMessage, CampaignMessage parentMessage) {
        Campaign campaign = thisMessage.getCampaign();
        Group masterGroup = campaign.getMasterGroup();
        if (parentMessage != null && parentMessage.getTagList() != null && parentMessage.getTagList().isEmpty()){
            Membership membership = user.getGroupMembership(masterGroup.getUid());
            if(membership != null) {
                addTagsOnMembership(membership, parentMessage.getTagList());
            } else {
                membership = new Membership(campaign.getMasterGroup(), user, new Role(BaseRoles.ROLE_ORDINARY_MEMBER, null),
                        Instant.now(), GroupJoinMethod.SELF_JOINED, null);
                addTagsOnMembership(membership, parentMessage.getTagList());
                user.getMemberships().add(membership);
                userManager.createUserProfile(user);
            }
        }
    }

    private void addTagsOnMembership(Membership membership, List<String> tags){
        for(String tag :tags) {
            membership.addTag(tag);
        }
    }
}
