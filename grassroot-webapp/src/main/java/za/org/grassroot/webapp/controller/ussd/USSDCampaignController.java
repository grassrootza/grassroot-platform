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
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDCampaignConstants;

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

    @RequestMapping(value = campaignUrl + USSDCampaignConstants.SET_LANGUAGE_URL)
    @ResponseBody
    public Request userSetLanguageForCampaign(@RequestParam(value= phoneNumber) String inputNumber,
                                              @RequestParam String campaignUid,
                                              @RequestParam(value = USSDCampaignConstants.LANGUAGE_PARAMETER) String languageCode) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        if(StringUtils.isEmpty(user.getLanguageCode())) {
            userManager.updateUserLanguage(user.getUid(), new Locale(languageCode));
        }
        CampaignMessage campaignMessage = campaignBroker.getOpeningMessage(campaignUid, new Locale(languageCode), UserInterfaceType.USSD, null);
        return  menuBuilder(buildCampaignUSSDMenu(campaignMessage));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignConstants.MORE_INFO_URL)
    @ResponseBody
    public Request processMoreInfoRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                          @RequestParam String messageUid)  throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
        return menuBuilder(buildCampaignUSSDMenu(message));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignConstants.JOIN_MASTER_GROUP_URL)
    @ResponseBody
    public Request processJoinMasterGroupRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                                 @RequestParam(required = false) String messageUid,
                                                 @RequestParam(required = false) String campaignUid)  throws URISyntaxException{
        User user = userManager.findByInputNumber(inputNumber);
        Locale locale;
        String promptStart;
        if (campaignUid == null) {
            CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
            campaignUid = message.getCampaign().getUid();
            locale = message.getLocale();
            promptStart = message.getMessage() + (StringUtils.isEmpty(message.getMessage()) ? "" : ". ");
            log.info("prompt start: {}, message : {}", promptStart, message.getMessage());
        } else {
            promptStart = getMessage("campaign.joined.generic", user);
            locale = user.getLocale();
        }
        Campaign campaign = campaignBroker.addUserToCampaignMasterGroup(campaignUid, user.getUid(), UserInterfaceType.USSD);
        return menuBuilder(topicsOrFinalOptionsMenu(campaign, user, promptStart, locale));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignConstants.SIGN_PETITION_URL)
    @ResponseBody
    public Request processSignPetitionRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                              @RequestParam String messageUid) throws URISyntaxException{
        User user = userManager.findByInputNumber(inputNumber);
        CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
        campaignBroker.signPetition(message.getUid(), user.getUid(), UserInterfaceType.USSD);
        final String promptStart = message.getMessage() + (StringUtils.isEmpty(message.getMessage()) ? "" : ". ");
        return menuBuilder(joinGroupOrFinalOptionsMenu(message.getCampaign(), user, promptStart, message.getLocale()));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignConstants.TAG_ME_URL)
    @ResponseBody
    public Request processTagMeRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam String messageUid,
                                       @RequestParam String parentMsgUid)  throws URISyntaxException{
        User user = userManager.findByInputNumber(inputNumber);
        CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
        updateMembership(user, message, campaignBroker.loadCampaignMessage(parentMsgUid, user.getUid()));
        return menuBuilder(buildCampaignUSSDMenu(message));
    }

    @RequestMapping(value = campaignUrl + "province")
    public Request processProvinceRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                          @RequestParam String campaignUid,
                                          @RequestParam Province province) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        userManager.updateUserProvince(user.getUid(), province);
        Campaign campaign = campaignBroker.load(campaignUid);
        return menuBuilder(processFinalOptionsMenu(campaign, user, "", user.getLocale()));
    }

    private USSDMenu joinGroupOrFinalOptionsMenu(Campaign campaign, User user, String promptStart, Locale locale) {
        USSDMenu menu;
        if (!campaignBroker.isUserInCampaignMasterGroup(campaign.getUid(), user.getUid())) {
            final String prompt = promptStart + getMessage("campaign.join.generic", locale.getLanguage());
            menu = new USSDMenu(prompt, optionsYesNo(user,
                    campaignMenus + USSDCampaignConstants.JOIN_MASTER_GROUP_URL + "?campaignUid=" + campaign.getUid(),
                    campaignMenus + USSDCampaignConstants.EXIT_URL + "?campaignUid=" + campaign.getUid()));
        } else {
            menu = processFinalOptionsMenu(campaign, user, promptStart, locale);
        }
        return menu;
    }

    private USSDMenu topicsOrFinalOptionsMenu(Campaign campaign, User user, String promptStart, Locale locale) {
        USSDMenu menu;
        if (!campaign.getJoinTopics().isEmpty()) {
            final String prompt = promptStart + getMessage("campaign.choose.topic", locale.getLanguage());
            final String urlPrefix = campaignMenus + USSDCampaignConstants.TAG_ME_URL + "?campaignUid=" + campaign.getUid();
            menu = new USSDMenu(prompt);
            campaign.getJoinTopics().forEach(topic -> menu.addMenuOption(urlPrefix + topic, topic));
        } else {
            menu = processFinalOptionsMenu(campaign, user, promptStart, locale);
        }
        return menu;
    }

    private USSDMenu processFinalOptionsMenu(Campaign campaign, User user, String promptStart, Locale locale) {
        USSDMenu menu;
        if (user.getProvince() == null) {
            final String prompt = promptStart + getMessage("campaign.joined.province", user);
            menu = new USSDMenu(prompt, provinceOptions(user, campaignMenus + "province?campaignUid="
                    + campaign.getUid() + "&province="));
        } else if (!user.hasName()) {
            final String prompt = promptStart + getMessage("campaign.joined.name", user);
            menu = new USSDMenu(prompt, campaignMenus + "/user/name?campaignUid=" + campaign.getUid());
        } else if (campaign.isSharingEnabled()) {
            menu = buildSharingMenu(campaign.getUid(), locale);
        } else {
            menu = genericPositiveExit(campaign.getUid(), locale);
        }
        return menu;
    }

    @RequestMapping(value = campaignUrl + USSDCampaignConstants.EXIT_URL)
    @ResponseBody
    public Request processExitRequest(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(required = false) String messageUid,
                                      @RequestParam(required = false) String campaignUid)  throws URISyntaxException{
        User user = userManager.findByInputNumber(inputNumber);
        if (campaignUid == null) {
            CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
            return menuBuilder(buildCampaignUSSDMenu(message));
        } else {
            return menuBuilder(genericPositiveExit(campaignUid, user.getLocale()));
        }
    }

    @RequestMapping(value = campaignUrl + USSDCampaignConstants.SHARE_URL)
    @ResponseBody
    public Request sharePrompt(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam String messageUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
        return menuBuilder(buildCampaignUSSDMenu(message));
    }

    @RequestMapping(value = campaignUrl + USSDCampaignConstants.SHARE_URL + "/do")
    @ResponseBody
    public Request shareDo(@RequestParam(value = phoneNumber) String inputNumber,
                           @RequestParam(value = userInputParam) String userInput,
                           @RequestParam String campaignUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        // todo: check number validity
        campaignBroker.sendShareMessage(campaignUid, user.getUid(), userInput, "Hello please sign up", UserInterfaceType.USSD);
        return menuBuilder(genericPositiveExit(campaignUid, user.getLocale()));
    }

    private USSDMenu buildCampaignUSSDMenu(CampaignMessage campaignMessage){
        String promptMessage = campaignMessage.getMessage();
        Map<String, String> linksMap = new LinkedHashMap<>();
        campaignMessage.getNextMessages().forEach((msgUid, actionType) -> {
            String optionKey = USSDCampaignConstants.CAMPAIGN_PREFIX + actionType.name().toLowerCase();
            String option = getMessage(optionKey, campaignMessage.getLocale().getLanguage());
            String embeddedUrl = campaignMenus +
                    USSDCampaignConstants.getCampaignUrlPrefixs().get(actionType) + "?" +
                    USSDCampaignConstants.MESSAGE_UID_PARAMETER + msgUid;
            linksMap.put(embeddedUrl, option);
        });
        return new USSDMenu(promptMessage,linksMap);
    }

    private USSDMenu buildSharingMenu(String campaignUid, Locale locale) {
        List<CampaignMessage> campaignMessage = campaignBroker.findCampaignMessage(campaignUid,
                CampaignActionType.SHARE_PROMPT, locale);
        final String prompt = !campaignMessage.isEmpty() ? campaignMessage.get(0).getMessage() :
                getMessage("campaign.share.generic", locale.getLanguage());
        return new USSDMenu(prompt, campaignMenus + "share/do?campaignUid=" + campaignUid);
    }

    private USSDMenu genericPositiveExit(String campaignUid, Locale locale) {
        List<CampaignMessage> campaignMessage = campaignBroker.findCampaignMessage(campaignUid,
                CampaignActionType.EXIT_POSITIVE, locale);
        return !campaignMessage.isEmpty() ? buildCampaignUSSDMenu(campaignMessage.get(0)) :
                new USSDMenu(getMessage("campaign.exit_positive.generic", locale.getLanguage()));
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
